package com.bank.notificationservice;

import com.bank.contracts.events.v1.*;
import com.bank.notificationservice.store.NotificationStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.*;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.mongodb.client.model.Filters.eq;

@Testcontainers
@org.springframework.context.annotation.Import({com.bank.security.SecurityConfiguration.class,com.bank.security.fixtures.SecurityTestConfiguration.class})
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "CONFIG_IMPORT=optional:classpath:/no-config-client.yml","spring.cloud.config.enabled=false","eureka.client.enabled=false"})
class NotificationKafkaIntegrationTest {
    @Container static final KafkaContainer KAFKA=new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));
    @Container static final MongoDBContainer MONGO=new MongoDBContainer(DockerImageName.parse("mongo:8.0"));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.kafka.bootstrap-servers",KAFKA::getBootstrapServers);
        p.add("spring.data.mongodb.uri",()->MONGO.getReplicaSetUrl()+"&serverSelectionTimeoutMS=2000&socketTimeoutMS=3000");
    }
    @Autowired KafkaTemplate<String,String> kafka;
    @Autowired ObjectMapper json;
    @Autowired MongoTemplate mongo;
    @Autowired KafkaListenerEndpointRegistry registry;
    @Autowired TestRestTemplate rest;
    @BeforeEach void authenticatedRequests() { rest.getRestTemplate().setInterceptors(java.util.List.of((request,body,execution) -> { if(!request.getHeaders().containsKey("Authorization"))request.getHeaders().setBearerAuth("admin");return execution.execute(request,body); })); }

    @MockitoSpyBean NotificationStore notifications;

    private BankEventV1 customer() {return event(EventType.CUSTOMER_CREATED,"1",Map.of("customerId","1"));}
    private BankEventV1 event(EventType type,String aggregate,Map<String,String> data) {
        return new BankEventV1(UUID.randomUUID(),type,1,UUID.randomUUID(),type.producer(),Instant.now(),aggregate,data);
    }
    private void send(BankEventV1 event) throws Exception {kafka.send(event.eventType().topic(),event.aggregateId(),json.writeValueAsString(event)).get(15,TimeUnit.SECONDS);}
    private void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
        while(!condition.getAsBoolean()) {if(System.nanoTime()>deadline)throw new AssertionError("Kafka/Mongo condition did not complete");Thread.sleep(100);}
    }
    private Document notification(BankEventV1 event) {return mongo.getCollection("notifications").find(eq("_id",event.eventId().toString())).first();}
    private Document deadLetter(String payload) {return mongo.getCollection("dead_letters").find(eq("payload",payload)).first();}
    private void awaitNotification(BankEventV1 event) throws Exception {await(()->notification(event)!=null);}

    @Test void duplicateDeliveryAndPostWriteFailureProduceOneDurableNotification() throws Exception {
        var event=customer();
        doAnswer(call->{call.callRealMethod();throw new TransientDataAccessResourceException("injected after Mongo acknowledgement");})
                .doCallRealMethod().when(notifications).accept(argThat(e->e.eventId().equals(event.eventId())),anyString(),anyInt(),anyLong());
        send(event);awaitNotification(event);send(event);
        await(()->mockingDetails(notifications).getInvocations().stream().filter(i->i.getMethod().getName().equals("accept") && ((BankEventV1)i.getArgument(0)).eventId().equals(event.eventId())).count()>=3);
        assertThat(mongo.getCollection("notifications").countDocuments(eq("_id",event.eventId().toString()))).isEqualTo(1);
        var response=rest.getForEntity("/notifications/"+event.eventId(),Map.class);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?,?>)response.getBody().get("event")).get("correlationId")).isEqualTo(event.correlationId().toString());
    }
    @Test void allFiveContractsAreConsumedIncludingTransferFailureAndSuspicion() throws Exception {
        var id=UUID.randomUUID().toString();
        var events=List.of(customer(),event(EventType.ACCOUNT_CREATED,"2",Map.of("accountId","2","customerId","1","accountType","SAVINGS","currency","BAM")),
                event(EventType.TRANSACTION_COMPLETED,id,Map.of("transactionId",id,"operationType","TRANSFER","fromAccountId","1","toAccountId","2","amount","5.00","currency","BAM")),
                event(EventType.TRANSACTION_FAILED,id,Map.of("transactionId",id,"operationType","TRANSFER","fromAccountId","1","toAccountId","2","amount","5.00","currency","BAM","failureCode","INSUFFICIENT_FUNDS")),
                event(EventType.SUSPICIOUS_TRANSACTION_DETECTED,"1",Map.of("accountId","1","reason","test rule")));
        for(var e:events)send(e);for(var e:events)awaitNotification(e);
    }
    @Test void transientStorageErrorsRetryBeforeSuccessfulAcknowledgement() throws Exception {
        var event=customer();
        doThrow(new TransientDataAccessResourceException("first failure"),new TransientDataAccessResourceException("second failure"))
                .doCallRealMethod().when(notifications).accept(argThat(e->e.eventId().equals(event.eventId())),anyString(),anyInt(),anyLong());
        send(event);awaitNotification(event);
        verify(notifications,times(3)).accept(argThat(e->e.eventId().equals(event.eventId())),anyString(),anyInt(),anyLong());
        assertThat(deadLetter(json.writeValueAsString(event))).isNull();
    }
    @Test void exhaustedStorageRetryGoesToDltThenReplayUsesSameEventId() throws Exception {
        var event=customer();var payload=json.writeValueAsString(event);
        doThrow(new TransientDataAccessResourceException("persistent failure")).when(notifications).accept(argThat(e->e.eventId().equals(event.eventId())),anyString(),anyInt(),anyLong());
        send(event);await(()->deadLetter(payload)!=null);assertThat(notification(event)).isNull();
        verify(notifications,times(3)).accept(argThat(e->e.eventId().equals(event.eventId())),anyString(),anyInt(),anyLong());
        doCallRealMethod().when(notifications).accept(argThat(e->e.eventId().equals(event.eventId())),anyString(),anyInt(),anyLong());
        var id=deadLetter(payload).getString("_id");
        for(int i=0;i<2;i++)assertThat(rest.postForEntity("/notifications/dead-letters/"+id+"/replay",null,Map.class).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        awaitNotification(event);assertThat(mongo.getCollection("notifications").countDocuments(eq("_id",event.eventId().toString()))).isEqualTo(1);
        assertThat(mongo.getCollection("dead_letters").find(eq("_id",id)).first().getInteger("replayRequests")).isEqualTo(2);
    }
    @Test void malformedUnsupportedAndWrongTopicEventsAreQuarantinedAndCorrectedReplayWorks() throws Exception {
        var event=customer();String malformed="invalid-"+UUID.randomUUID();
        var version=json.writeValueAsString(event).replace("\"version\":1","\"version\":2");
        kafka.send(EventType.CUSTOMER_CREATED.topic(),"1",malformed).get(15,TimeUnit.SECONDS);
        kafka.send(EventType.CUSTOMER_CREATED.topic(),"1",version).get(15,TimeUnit.SECONDS);
        kafka.send(EventType.ACCOUNT_CREATED.topic(),"1",json.writeValueAsString(event)).get(15,TimeUnit.SECONDS);
        await(()->deadLetter(malformed)!=null && deadLetter(version)!=null && deadLetter(jsonUnchecked(event))!=null);
        var id=deadLetter(malformed).getString("_id");
        assertThat(rest.postForEntity("/notifications/dead-letters/"+id+"/replay",null,Map.class).getStatusCode().value()).isEqualTo(422);
        var replay=Map.of("correctedPayload",json.writeValueAsString(event));
        assertThat(rest.postForEntity("/notifications/dead-letters/"+id+"/replay",replay,Map.class).getStatusCode().value()).isEqualTo(202);awaitNotification(event);
    }
    private String jsonUnchecked(BankEventV1 event) {try{return json.writeValueAsString(event);}catch(Exception e){throw new AssertionError(e);}}
    @Test void conflictingReuseOfEventIdIsQuarantinedRatherThanOverwritingNotification() throws Exception {
        var original=customer();send(original);awaitNotification(original);
        var conflict=new BankEventV1(original.eventId(),original.eventType(),1,original.correlationId(),original.producer(),original.occurredAt(),"2",Map.of("customerId","2"));
        send(conflict);await(()->deadLetter(jsonUnchecked(conflict))!=null);
        assertThat(notification(original).getString("aggregateId")).isEqualTo("1");
    }
    @Test void consumerRestartPreservesCommittedOffsetsAndConsumesQueuedRecords() throws Exception {
        var first=customer();send(first);awaitNotification(first);
        try(var admin=AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers()))) {
            await(()->{try{
                var stored=notification(first);var partition=new org.apache.kafka.common.TopicPartition(first.eventType().topic(),stored.getInteger("partition"));
                var offsets=admin.listConsumerGroupOffsets("bank.notifications.v1").partitionsToOffsetAndMetadata().get(5,TimeUnit.SECONDS);
                return offsets.containsKey(partition) && offsets.get(partition).offset()>stored.getLong("offset");
            }catch(Exception e){return false;}});
            var container=registry.getListenerContainer("notifications");container.stop();
            var queued=customer();send(queued);assertThat(notification(queued)).isNull();container.start();awaitNotification(queued);send(first);
            assertThat(mongo.getCollection("notifications").countDocuments(eq("_id",first.eventId().toString()))).isEqualTo(1);
            var topic=admin.describeTopics(List.of(EventType.CUSTOMER_CREATED.topic())).allTopicNames().get(10,TimeUnit.SECONDS);
            assertThat(topic.get(EventType.CUSTOMER_CREATED.topic()).partitions()).hasSize(3);
        }
    }
    @Test void independentConsumerGroupReadsSameRecordWithoutExtraNotification() throws Exception {
        var event=customer();send(event);awaitNotification(event);
        var props=new HashMap<String,Object>();props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG,"independent-"+UUID.randomUUID());props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,false);
        try(var consumer=new KafkaConsumer<String,String>(props,new StringDeserializer(),new StringDeserializer())) {
            consumer.subscribe(List.of(event.eventType().topic()));boolean found=false;
            for(int i=0;i<20 && !found;i++)for(var row:consumer.poll(Duration.ofSeconds(1)))if(row.value().contains(event.eventId().toString()))found=true;
            assertThat(found).isTrue();assertThat(mongo.getCollection("notifications").countDocuments(eq("_id",event.eventId().toString()))).isEqualTo(1);
        }
    }
}
