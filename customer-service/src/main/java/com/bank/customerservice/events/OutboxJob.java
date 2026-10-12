package com.bank.customerservice.events;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.slf4j.*;
@Component
@EnableScheduling
@ConditionalOnProperty(name="bank.events.relay-enabled",havingValue="true",matchIfMissing=true)
public class OutboxJob {
    private static final Logger LOG=LoggerFactory.getLogger(OutboxJob.class);
    private final OutboxRelay relay;
    public OutboxJob(OutboxRelay relay) {this.relay=relay;}
    @Scheduled(fixedDelayString="${bank.events.delay-ms:500}")
    public void run() {
        try {for(int i=0;i<10 && relay.publishNext();i++){ /* bounded batch */ } }
        catch(RuntimeException failed) {LOG.warn("Outbox cycle failed; committed events remain retryable.");}
    }
}
