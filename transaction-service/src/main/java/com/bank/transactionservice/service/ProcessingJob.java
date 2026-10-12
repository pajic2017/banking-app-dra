package com.bank.transactionservice.service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
@ConditionalOnProperty(name="bank.processing.enabled", havingValue="true", matchIfMissing=true)
public class ProcessingJob {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessingJob.class);
    private final TransactionProcessor processor;
    public ProcessingJob(TransactionProcessor processor) { this.processor = processor; }
    @Scheduled(fixedDelayString="${bank.processing.delay-ms:500}")
    public void run() {
        try { for (int i = 0; i < 10 && processor.processNext(); i++) { /* bounded batch */ } }
        catch (RuntimeException failure) { LOG.warn("Processing cycle failed; durable commands remain retryable."); }
    }
}
