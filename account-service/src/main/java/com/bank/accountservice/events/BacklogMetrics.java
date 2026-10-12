package com.bank.accountservice.events;
import io.micrometer.core.instrument.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="bank.monitoring.jobs-enabled",havingValue="true",matchIfMissing=true)
@EnableScheduling
public class BacklogMetrics {
    private final JdbcTemplate jdbc;private final AtomicLong pending=new AtomicLong(),oldest=new AtomicLong();
    public BacklogMetrics(JdbcTemplate jdbc,MeterRegistry meters) {this.jdbc=jdbc;
        Gauge.builder("bank.outbox.pending",pending,AtomicLong::get).register(meters);
        Gauge.builder("bank.outbox.oldest.seconds",oldest,AtomicLong::get).register(meters);
    }
    @Scheduled(fixedDelay=5000,initialDelay=5000) public void refresh() {
        try {var row=jdbc.queryForMap("SELECT COUNT(*) AS pending,MIN(created_at) AS oldest FROM event_outbox WHERE status='PENDING'");
            pending.set(((Number)row.get("pending")).longValue());Object date=row.get("oldest");
            oldest.set(date instanceof Timestamp t?Math.max(0,Instant.now().getEpochSecond()-t.toInstant().getEpochSecond()):0);
        } catch(org.springframework.dao.DataAccessException unavailable) {pending.set(-1);oldest.set(-1);}
    }
}
