package com.bank.notificationservice.config;
import com.bank.contracts.events.v1.EventType;
import com.bank.notificationservice.events.InvalidEvent;
import java.time.Duration;
import java.util.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.*;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;
@Configuration
public class KafkaConfiguration {
    @Bean KafkaAdmin.NewTopics bankTopics() {
        var topics=new ArrayList<NewTopic>();
        for(var type:EventType.values())for(var name:new String[]{type.topic(),type.topic()+".DLT"})topics.add(TopicBuilder.name(name).partitions(3).replicas(1).config("retention.ms",name.endsWith(".DLT")?"2592000000":"604800000").config("cleanup.policy","delete").build());
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }
    @Bean ConcurrentKafkaListenerContainerFactory<Object,Object> notificationFactory(ConsumerFactory<Object,Object> consumerFactory,KafkaTemplate<Object,Object> kafka) {
        var factory=base(consumerFactory);
        var recoverer=new DeadLetterPublishingRecoverer(kafka,(record,error)->new TopicPartition(record.topic()+".DLT",record.partition()));
        recoverer.setFailIfSendResultIsError(true);recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(10));
        var handler=new DefaultErrorHandler(recoverer,new FixedBackOff(500,2));
        handler.addNotRetryableExceptions(InvalidEvent.class);
        factory.setCommonErrorHandler(handler);return factory;
    }
    @Bean ConcurrentKafkaListenerContainerFactory<Object,Object> deadLetterFactory(ConsumerFactory<Object,Object> consumerFactory) {
        var factory=base(consumerFactory);
        // DLT storage waits indefinitely for Mongo recovery; it never creates a DLT-of-DLT loop.
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000,FixedBackOff.UNLIMITED_ATTEMPTS)));return factory;
    }
    private ConcurrentKafkaListenerContainerFactory<Object,Object> base(ConsumerFactory<Object,Object> consumerFactory) {
        var f=new ConcurrentKafkaListenerContainerFactory<Object,Object>();f.setConsumerFactory(consumerFactory);f.setConcurrency(3);f.getContainerProperties().setObservationEnabled(true);
        f.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);f.getContainerProperties().setSyncCommits(true);return f;
    }
}
