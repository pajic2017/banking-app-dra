package com.bank.customerservice.events;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class OutboxRelay {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String,String> kafka;
    private final Clock clock;
    public OutboxRelay(JdbcTemplate jdbc,KafkaTemplate<String,String> kafka,Clock clock) {this.jdbc=jdbc;this.kafka=kafka;this.clock=clock;}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public boolean publishNext() {
        var rows=jdbc.queryForList("SELECT * FROM event_outbox WHERE status='PENDING' AND next_attempt_at<=? ORDER BY next_attempt_at,created_at,event_id LIMIT 1 FOR UPDATE SKIP LOCKED",Timestamp.from(clock.instant()));
        if(rows.isEmpty())return false;
        var row=rows.get(0);var id=row.get("event_id").toString();
        try { kafka.send(row.get("topic").toString(),row.get("aggregate_id").toString(),row.get("payload").toString()).get(9,TimeUnit.SECONDS); }
        catch(Exception unavailable) {
            if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();
            long seconds=Math.min(60,1L<<Math.min(((Number)row.get("attempts")).intValue(),6));
            jdbc.update("UPDATE event_outbox SET attempts=attempts+1,next_attempt_at=? WHERE event_id=?",Timestamp.from(clock.instant().plusSeconds(seconds)),id);return true;
        }
        // Broker acknowledgement can precede a failed DB commit: replay is safe by stable event ID.
        jdbc.update("UPDATE event_outbox SET status='PUBLISHED',attempts=attempts+1,published_at=? WHERE event_id=?",Timestamp.from(clock.instant()),id);
        return true;
    }
}
