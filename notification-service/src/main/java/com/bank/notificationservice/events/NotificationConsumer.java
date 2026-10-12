package com.bank.notificationservice.events;
import com.bank.notificationservice.store.*;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import org.slf4j.*;
@Component
public class NotificationConsumer {
    private static final Logger LOG=LoggerFactory.getLogger(NotificationConsumer.class);
    private final EventCodec codec;private final NotificationStore notifications;private final DeadLetterStore deadLetters;
    public NotificationConsumer(EventCodec codec,NotificationStore notifications,DeadLetterStore deadLetters) {this.codec=codec;this.notifications=notifications;this.deadLetters=deadLetters;}
    @KafkaListener(id="notifications",groupId="${bank.notifications.group:bank.notifications.v1}",topics={
            "bank.customer.created","bank.account.created","bank.transaction.completed","bank.transaction.failed","bank.fraud.detected"},containerFactory="notificationFactory")
    public void receive(ConsumerRecord<String,String> record) {
        var event=codec.decode(record.value(),record.topic());
        var previous=MDC.get("correlationId");MDC.put("correlationId",event.correlationId().toString());
        try {
        if(notifications.accept(event,record.topic(),record.partition(),record.offset())) {
            LOG.info("Notification stored eventId={} type={} correlationId={}",event.eventId(),event.eventType(),event.correlationId());
        }
        } finally {if(previous==null)MDC.remove("correlationId");else MDC.put("correlationId",previous);}
    }
    @KafkaListener(id="notification-dlt",groupId="${bank.notifications.dlt-group:bank.notifications.dlt.v1}",topics={
            "bank.customer.created.DLT","bank.account.created.DLT","bank.transaction.completed.DLT","bank.transaction.failed.DLT","bank.fraud.detected.DLT"},containerFactory="deadLetterFactory")
    public void quarantine(ConsumerRecord<String,String> record) {
        var header=record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC);
        var original=header==null?record.topic().substring(0,record.topic().length()-4):new String(header.value(),StandardCharsets.UTF_8);
        deadLetters.retain(record.topic(),record.partition(),record.offset(),original,record.key(),record.value());
    }
}
