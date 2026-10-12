ALTER TABLE event_outbox ADD COLUMN traceparent VARCHAR(55);
ALTER TABLE transactions ADD COLUMN traceparent VARCHAR(55);
