package com.bank.transactionservice;

import com.bank.contracts.posting.v1.*;
import com.bank.transactionservice.api.*;
import com.bank.transactionservice.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "CONFIG_IMPORT=optional:classpath:/no-config-client.yml","bank.events.relay-enabled=false","spring.cloud.config.enabled=false","eureka.client.enabled=false","bank.processing.enabled=false"})
class TransactionIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17");
    static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    static final Map<UUID,PostingResultV1> receipts=new ConcurrentHashMap<>();
    static final AtomicInteger applications=new AtomicInteger();
    static final ExecutorService threads=Executors.newCachedThreadPool();
    static final HttpServer account;
    static volatile String mode="COMPLETED";
    static {
        try {
            account=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);account.setExecutor(threads);
            account.createContext("/internal/v1/postings",exchange->{
                try {
                    var c=JSON.readValue(exchange.getRequestBody(),PostingCommandV1.class);
                    int status=200;byte[] body;
                    if(mode.equals("UNAVAILABLE")){status=503;body="{}".getBytes();}
                    else if(mode.equals("BAD")){body="not json".getBytes();}
                    else {
                        var result=receipts.computeIfAbsent(c.operationId(),id->{applications.incrementAndGet();return new PostingResultV1(id,
                                mode.equals("REJECTED")?PostingStatus.REJECTED:PostingStatus.COMPLETED,mode.equals("REJECTED")?"INSUFFICIENT_FUNDS":null,Instant.now(),c.instructionHash());});
                        if(mode.equals("DROP_AFTER_COMMIT")){exchange.close();return;}
                        if(mode.equals("WRONG_ID"))result=new PostingResultV1(UUID.randomUUID(),result.status(),result.failureCode(),result.recordedAt(),result.instructionHash());
                        body=JSON.writeValueAsBytes(result);
                    }
                    exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);
                } finally {exchange.close();}
            });account.start();
        } catch(java.io.IOException error){throw new ExceptionInInitializerError(error);}
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",POSTGRES::getJdbcUrl);p.add("spring.datasource.username",POSTGRES::getUsername);p.add("spring.datasource.password",POSTGRES::getPassword);
        p.add("bank.account-service-url",()->"http://127.0.0.1:"+account.getAddress().getPort());
    }
    @AfterAll static void shutdown(){account.stop(0);threads.shutdownNow();}
    @Autowired TransactionService service;
    @Autowired TransactionProcessor processor;
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void reset(){jdbc.update("DELETE FROM event_outbox");jdbc.update("DELETE FROM transactions");receipts.clear();applications.set(0);mode="COMPLETED";}
    private TransactionView submit(String key){return service.submit(key,PostingType.DEPOSIT,null,1L,new BigDecimal("10"),"BAM","test");}
    private void due(){jdbc.update("UPDATE transactions SET next_attempt_at=now()-interval '1 second' WHERE status='PENDING'");}

    @Test void durableAcceptProcessGetAndHistory() {
        var headers=new HttpHeaders();headers.set("Idempotency-Key","api-test");
        var response=rest.postForEntity("/transactions/deposit",new HttpEntity<>(new MoneyRequest(1L,new BigDecimal("10"),"BAM","test"),headers),TransactionView.class);
        assertThat(response.getStatusCode().value()).isEqualTo(202);var row=response.getBody();assertThat(row.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("../transactions/"+row.id());
        assertThat(processor.processNext()).isTrue();assertThat(processor.processNext()).isFalse();
        assertThat(rest.getForObject("/transactions/"+row.id(),TransactionView.class).status()).isEqualTo(TransactionStatus.COMPLETED);
        var page=rest.getForObject("/transactions/account/1?status=COMPLETED&type=DEPOSIT",TransactionPage.class);
        assertThat(page.totalElements()).isEqualTo(1);assertThat(page.items().get(0).amount()).isEqualByComparingTo("10");
        assertThat(rest.postForEntity("/transactions/deposit",new HttpEntity<>(new MoneyRequest(1L,new BigDecimal("10.00"),"bam","test"),headers),TransactionView.class).getStatusCode().value()).isEqualTo(200);
    }
    @Test void concurrentIdempotencySubmissionsProduceOneDurableCommand() throws Exception {
        var pool=Executors.newFixedThreadPool(8);
        try {
            var futures=new ArrayList<Future<TransactionView>>();for(int i=0;i<12;i++)futures.add(pool.submit(()->submit("same-key")));
            var ids=new HashSet<UUID>();for(var f:futures)ids.add(f.get(20,TimeUnit.SECONDS).id());assertThat(ids).hasSize(1);
        } finally {pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions",Integer.class)).isEqualTo(1);
    }
    @Test void conflictingKeyPayloadIs409() {
        var row=submit("same-key");var headers=new HttpHeaders();headers.set("Idempotency-Key","same-key");
        assertThat(rest.postForEntity("/transactions/deposit",new HttpEntity<>(new MoneyRequest(1L,new BigDecimal("11"),"BAM","test"),headers),Map.class).getStatusCode().value()).isEqualTo(409);
        assertThat(service.get(row.id()).amount()).isEqualByComparingTo("10");
    }
    @Test void lostResponseAfterRemoteCommitRetriesSameOperationWithoutSecondApplication() {
        var row=submit("lost-response");mode="DROP_AFTER_COMMIT";processor.processNext();
        assertThat(applications.get()).isEqualTo(1);assertThat(service.get(row.id()).status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(service.get(row.id()).attempts()).isEqualTo(1);assertThat(processor.processNext()).isFalse();
        mode="COMPLETED";due();processor.processNext();
        assertThat(service.get(row.id()).status()).isEqualTo(TransactionStatus.COMPLETED);assertThat(applications.get()).isEqualTo(1);
    }
    @Test void localDatabaseCommitFailureAfterRemoteApplicationIsRecoverable() {
        var row=submit("local-failure");
        jdbc.execute("CREATE FUNCTION fail_complete() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='COMPLETED' THEN RAISE EXCEPTION 'injected local persistence failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fail_complete BEFORE UPDATE ON transactions FOR EACH ROW EXECUTE FUNCTION fail_complete()");
        try {assertThatThrownBy(()->processor.processNext()).isInstanceOf(org.springframework.dao.DataAccessException.class);}
        finally {jdbc.execute("DROP TRIGGER fail_complete ON transactions");jdbc.execute("DROP FUNCTION fail_complete()");}
        assertThat(service.get(row.id()).status()).isEqualTo(TransactionStatus.PENDING);assertThat(applications.get()).isEqualTo(1);
        processor.processNext();assertThat(service.get(row.id()).status()).isEqualTo(TransactionStatus.COMPLETED);assertThat(applications.get()).isEqualTo(1);
    }
    @Test void dependencyAndMalformedResponsesStayPendingThenRecover() {
        var row=submit("dependency");
        for(var behavior:new String[]{"UNAVAILABLE","BAD","WRONG_ID"}) {
            mode=behavior;due();processor.processNext();assertThat(service.get(row.id()).status()).isEqualTo(TransactionStatus.PENDING);
            assertThat(service.get(row.id()).lastRetryCode()).isEqualTo("POSTING_UNCONFIRMED");
        }
        mode="COMPLETED";due();processor.processNext();assertThat(service.get(row.id()).status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(applications.get()).isEqualTo(1);
    }
    @Test void durableBusinessRejectionIsTerminal() {
        var row=submit("rejected");mode="REJECTED";processor.processNext();
        var rejected=service.get(row.id());assertThat(rejected.status()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(rejected.failureCode()).isEqualTo("INSUFFICIENT_FUNDS");assertThat(processor.processNext()).isFalse();
    }
    @Test void skipLockedLetsAnotherWorkerProgressWithoutDoubleProcessing() throws Exception {
        var first=submit("first");var second=submit("second");
        try(var connection=java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())){
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("SELECT id FROM transactions WHERE id=? FOR UPDATE")){
                statement.setObject(1,first.id());statement.executeQuery();
                assertThat(processor.processNext()).isTrue();assertThat(service.get(second.id()).status()).isEqualTo(TransactionStatus.COMPLETED);
                assertThat(service.get(first.id()).status()).isEqualTo(TransactionStatus.PENDING);
            }connection.rollback();
        }
        processor.processNext();assertThat(applications.get()).isEqualTo(2);
    }
    @Test void invalidAmountsIdsKeysAndPaginationAre400AndMissingIs404() {
        var h=new HttpHeaders();h.set("Idempotency-Key","valid-key");
        for(var amount:new String[]{"0","-1","1.001"}){
            assertThat(rest.postForEntity("/transactions/deposit",new HttpEntity<>(new MoneyRequest(1L,new BigDecimal(amount),"BAM",""),h),Map.class).getStatusCode().value()).isEqualTo(400);
        }
        assertThat(rest.postForEntity("/transactions/deposit",new MoneyRequest(1L,BigDecimal.ONE,"BAM",""),Map.class).getStatusCode().value()).isEqualTo(400);
        h.set("Idempotency-Key","contains space");assertThat(rest.postForEntity("/transactions/deposit",new HttpEntity<>(new MoneyRequest(1L,BigDecimal.ONE,"BAM",""),h),Map.class).getStatusCode().value()).isEqualTo(400);
        assertThat(rest.getForEntity("/transactions?size=101",Map.class).getStatusCode().value()).isEqualTo(400);
        assertThat(rest.getForEntity("/transactions/"+UUID.randomUUID(),Map.class).getStatusCode().value()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions",Integer.class)).isZero();
    }
    @Test void migrationAndOpenApiContainTransfer() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND success",Integer.class)).isEqualTo(1);
        var doc=rest.getForObject("/v3/api-docs",Map.class);assertThat(((Map<?,?>)doc.get("paths")).containsKey("/transfers")).isTrue();
    }
    @Test void terminalOutboxFailureRollsBackStatusAndRetryCreatesOneEvent() {
        var row=submit("outbox-failure");
        jdbc.execute("ALTER TABLE event_outbox ADD CONSTRAINT injected_capture_failure CHECK (event_type<>'TRANSACTION_COMPLETED')");
        try {assertThatThrownBy(()->processor.processNext()).isInstanceOf(org.springframework.dao.DataAccessException.class);}
        finally {jdbc.execute("ALTER TABLE event_outbox DROP CONSTRAINT injected_capture_failure");}
        assertThat(service.get(row.id()).status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox",Integer.class)).isZero();assertThat(applications.get()).isEqualTo(1);
        processor.processNext();assertThat(service.get(row.id()).status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox",Integer.class)).isEqualTo(1);assertThat(applications.get()).isEqualTo(1);
        assertThat(processor.processNext()).isFalse();
    }
    @Test void requestCorrelationIsPersistedAndTerminalFailureUsesItsOwnTopic() throws Exception {
        var correlation=UUID.randomUUID();var h=new HttpHeaders();h.set("Idempotency-Key","correlated");h.set("X-Correlation-ID",correlation.toString());
        var response=rest.postForEntity("/transactions/deposit",new HttpEntity<>(new MoneyRequest(1L,new BigDecimal("10"),"BAM",""),h),TransactionView.class);
        assertThat(response.getHeaders().getFirst("X-Correlation-ID")).isEqualTo(correlation.toString());assertThat(response.getBody().correlationId()).isEqualTo(correlation);
        mode="REJECTED";processor.processNext();
        var event=JSON.readValue(jdbc.queryForObject("SELECT payload FROM event_outbox",String.class),com.bank.contracts.events.v1.BankEventV1.class);
        assertThat(event.correlationId()).isEqualTo(correlation);assertThat(event.eventType()).isEqualTo(com.bank.contracts.events.v1.EventType.TRANSACTION_FAILED);
        assertThat(jdbc.queryForObject("SELECT topic FROM event_outbox",String.class)).isEqualTo("bank.transaction.failed");
        h.set("X-Correlation-ID","invalid");assertThat(rest.postForEntity("/transactions/deposit",new HttpEntity<>(new MoneyRequest(1L,new BigDecimal("10"),"BAM",""),h),Map.class).getStatusCode().value()).isEqualTo(400);
    }

}
