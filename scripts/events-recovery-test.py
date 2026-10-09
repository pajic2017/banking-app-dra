#!/usr/bin/env python3
"""Development-only recovery checks; deliberately stops this project's Kafka, consumer and Mongo.
Preserves volumes and restores stopped services even when a check fails.
"""
import json
import subprocess
import time
import uuid
import urllib.error
import urllib.request
from pathlib import Path

root = Path(__file__).resolve().parents[1]
base = 'http://localhost:8082'

def compose(*args, stdin=None, check=True):
    result = subprocess.run(['docker', 'compose', *args], cwd=root, input=stdin, text=True, capture_output=True)
    if check and result.returncode:
        raise AssertionError(f'Compose operation failed: {args[:3]}: {result.stderr[-1200:]}')
    return result

def request(method, url, body=None, correlation=None):
    headers = {'Content-Type': 'application/json'}
    if correlation:
        headers['X-Correlation-ID'] = correlation
    with urllib.request.urlopen(urllib.request.Request(url, data=json.dumps(body).encode() if body is not None else None,
                                                     headers=headers, method=method), timeout=10) as response:
        raw = response.read()
        return json.loads(raw) if raw else None

def customer():
    correlation = str(uuid.uuid4())
    row = request('POST', base + '/customers', {'firstName': 'EventRecovery', 'lastName': 'Test',
        'email': f'event-recovery-{uuid.uuid4().hex}@example.com', 'phone': '+38761123456'}, correlation)
    return row['id'], correlation

def outbox(cid):
    assert isinstance(cid, int) and cid > 0
    sql = f"SELECT payload,status,attempts FROM event_outbox WHERE event_type='CUSTOMER_CREATED' AND aggregate_id='{cid}';"
    result = compose('exec', '-T', 'customer-db', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" -D "$MYSQL_DATABASE" -N -e "$1"', 'event-check', sql)
    payload, status, attempts = result.stdout.strip().split('\t')
    return json.loads(payload), status, int(attempts)

def exists(event_id):
    try:
        return request('GET', 'http://localhost:8085/notifications/' + str(uuid.UUID(event_id)))
    except urllib.error.HTTPError as error:
        if error.code in (404, 503):
            return None
        raise

def wait(condition, timeout=150):
    deadline = time.monotonic() + timeout
    while True:
        result = condition()
        if result:
            return result
        if time.monotonic() >= deadline:
            raise AssertionError('Recovery condition did not complete')
        time.sleep(.5)

def send(event, raw=None):
    payload = raw if raw is not None else json.dumps(event, separators=(',', ':'))
    compose('exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-producer.sh', '--bootstrap-server', 'localhost:9092',
        '--topic', 'bank.customer.created', '--property', 'parse.key=true', '--property', 'key.separator=|',
        '--producer-property', 'acks=all', stdin=event['aggregateId'] + '|' + payload + '\n')

def quarantine(event_id=None, raw=None):
    try:
        page = request('GET', 'http://localhost:8085/notifications/dead-letters?size=100')
    except urllib.error.HTTPError as error:
        if error.code == 503:
            return None
        raise
    for row in page['items']:
        if raw is not None and row['payload'] == raw:
            return row
        if event_id is not None:
            try:
                if json.loads(row['payload'])['eventId'] == event_id:
                    return row
            except (ValueError, KeyError, TypeError):
                pass
    return None

customers = []
try:
    compose('stop', 'kafka')
    started = time.monotonic()
    cid, correlation = customer(); customers.append(cid)
    assert time.monotonic() - started < 5, 'Business creation waited for Kafka'
    event, status, _ = outbox(cid)
    assert status == 'PENDING'
    wait(lambda: outbox(cid)[2] > 0, 30)
    assert outbox(cid)[0]['eventId'] == event['eventId']
    compose('up', '-d', '--wait', '--wait-timeout', '180', 'kafka')
    wait(lambda: exists(event['eventId']))
    wait(lambda: outbox(cid)[1] == 'PUBLISHED')
    print('PASS: broker outage did not block business commit; retained outbox event recovered with its original ID')

    send(event); send(event)
    time.sleep(2)
    page = request('GET', f'http://localhost:8085/notifications?correlationId={correlation}&size=100')
    assert page['totalElements'] == 1
    print('PASS: duplicate real Kafka deliveries retained one Mongo notification')

    compose('stop', 'notification-service')
    queued, _ = customer(); customers.append(queued)
    queued_event = wait(lambda: outbox(queued)[0] if outbox(queued)[1] == 'PUBLISHED' else None)
    compose('up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', 'notification-service')
    wait(lambda: exists(queued_event['eventId']))
    assert exists(event['eventId'])
    print('PASS: consumer process restart resumed its stable group and consumed records published while it was stopped')

    malformed = 'malformed-event-' + uuid.uuid4().hex
    send(event, malformed)
    dead = wait(lambda: quarantine(raw=malformed))
    try:
        request('POST', f'http://localhost:8085/notifications/dead-letters/{dead["id"]}/replay')
        raise AssertionError('Malformed replay must require correction')
    except urllib.error.HTTPError as error:
        assert error.code == 422
    request('POST', f'http://localhost:8085/notifications/dead-letters/{dead["id"]}/replay', {'correctedPayload': json.dumps(event)})
    assert exists(event['eventId'])
    print('PASS: poison record reached durable quarantine; validated corrected replay preserved the original notification')

    compose('stop', 'notification-db')
    failed, _ = customer(); customers.append(failed)
    failed_event = outbox(failed)[0]
    # Wait for a real DLT record while Mongo is unavailable; do not infer recovery from a timer.
    def dlt_contains():
        # Assign partitions directly: a short-lived random group can time out before rebalance.
        for partition in range(3):
            result = compose('exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-consumer.sh', '--bootstrap-server', 'localhost:9092',
                '--topic', 'bank.customer.created.DLT', '--partition', str(partition), '--offset', 'earliest',
                '--timeout-ms', '2000', '--max-messages', '100', check=False)
            if failed_event['eventId'] in result.stdout:
                return True
        return False
    wait(dlt_contains, 60)
    compose('up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', 'notification-db')
    dead = wait(lambda: quarantine(event_id=failed_event['eventId']))
    assert not exists(failed_event['eventId'])
    request('POST', f'http://localhost:8085/notifications/dead-letters/{dead["id"]}/replay')
    wait(lambda: exists(failed_event['eventId']))
    assert exists(event['eventId']) and exists(queued_event['eventId'])
    print('PASS: real Mongo outage exhausted retry to Kafka DLT; quarantine resumed after Mongo restart and unchanged replay delivered once')
finally:
    compose('up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', 'kafka', 'notification-db', 'notification-service')
    for cid in customers:
        request('DELETE', base + f'/customers/{cid}')
print('PASS: Kafka, Mongo and consumer restored; inactive synthetic customers retained with audit records')
