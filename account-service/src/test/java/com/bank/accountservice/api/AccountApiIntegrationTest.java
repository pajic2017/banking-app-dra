package com.bank.accountservice.api;
import com.bank.accountservice.domain.*;
import com.bank.accountservice.repository.AccountRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "CONFIG_IMPORT=optional:classpath:/no-config-client.yml","bank.events.relay-enabled=false","spring.cloud.config.enabled=false","eureka.client.enabled=false"})
class AccountApiIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("accounts");
    static final ExecutorService threads=Executors.newCachedThreadPool();
    static final HttpServer customer;
    static volatile String mode="ACTIVE";
    static {
        try {
            customer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            customer.setExecutor(threads);
            customer.createContext("/customers/",exchange->{
                var id=exchange.getRequestURI().getPath().substring("/customers/".length());
                int status=200;
                String body="{\"id\":"+id+",\"status\":\""+mode+"\"}";
                if (mode.equals("MISSING")) { status=404;body="{}"; }
                if (mode.equals("FAIL")) { status=503;body="{}"; }
                if (mode.equals("BAD")) body="not json";
                if (mode.equals("TIMEOUT")) { try {Thread.sleep(4500);} catch(InterruptedException ignored) {Thread.currentThread().interrupt();} }
                var bytes=body.getBytes(StandardCharsets.UTF_8);
                try { exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes); }
                finally {exchange.close();}
            });customer.start();
        } catch(java.io.IOException error) { throw new ExceptionInInitializerError(error); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);
        p.add("bank.customer-service-url",()->"http://127.0.0.1:"+customer.getAddress().getPort());
    }
    @AfterAll static void shutdown() {customer.stop(0);threads.shutdownNow();}
    @Autowired TestRestTemplate rest;
    @Autowired AccountRepository repository;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void reset() {mode="ACTIVE";jdbc.update("DELETE FROM event_outbox");repository.deleteAllInBatch();}
    private AccountResponse create(long customerId,AccountType type,String currency) {
        var r=rest.postForEntity("/accounts",new CreateAccountRequest(customerId,type,currency),AccountResponse.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getHeaders().getLocation().toString()).isEqualTo("accounts/"+r.getBody().id());return r.getBody();
    }
    @Test void createLookupBalanceAndCustomerPagination() {
        var first=create(41L,AccountType.CURRENT,"bam");var second=create(41L,AccountType.SAVINGS,"BAM");
        create(42L,AccountType.SAVINGS,"JPY");
        assertThat(first.accountNumber()).matches("BA[A-F0-9]{32}");
        assertThat(second.accountNumber()).isNotEqualTo(first.accountNumber());
        assertThat(rest.getForObject("/accounts/"+first.id(),AccountResponse.class)).isEqualTo(first);
        var balance=rest.getForObject("/accounts/"+first.id()+"/balance",BalanceResponse.class);
        assertThat(balance.balance()).isEqualByComparingTo("0.00");assertThat(balance.currency()).isEqualTo("BAM");
        var page=rest.getForObject("/accounts/customer/41?page=1&size=1",AccountPage.class);
        assertThat(page.totalElements()).isEqualTo(2);assertThat(page.items()).extracting(AccountResponse::id).containsExactly(second.id());
    }
    @Test void supportsAllStatusTransitionsAndRejectsReopening() {
        var a=create(41L,AccountType.CURRENT,"BAM");
        for (var status:new String[]{"BLOCKED","ACTIVE","CLOSED","CLOSED"}) {
            var r=rest.exchange("/accounts/"+a.id()+"/status",HttpMethod.PATCH,new HttpEntity<>(Map.of("status",status)),AccountResponse.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(r.getBody().status().name()).isEqualTo(status);
        }
        assertThat(rest.exchange("/accounts/"+a.id()+"/status",HttpMethod.PATCH,new HttpEntity<>(Map.of("status","ACTIVE")),Map.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repository.findById(a.id()).orElseThrow().getStatus()).isEqualTo(AccountStatus.CLOSED);
    }
    @Test void nonzeroBalanceCannotBeClosedAndIsNotModified() {
        var a=create(41L,AccountType.CURRENT,"BAM");
        jdbc.update("UPDATE accounts SET balance=10 WHERE id=?",a.id());
        assertThat(rest.exchange("/accounts/"+a.id()+"/status",HttpMethod.PATCH,new HttpEntity<>(Map.of("status","CLOSED")),Map.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repository.findById(a.id()).orElseThrow().getBalance()).isEqualByComparingTo("10");
        assertThat(repository.findById(a.id()).orElseThrow().getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
    @ParameterizedTest @ValueSource(strings={"MISSING","INACTIVE","FAIL","BAD","TIMEOUT","UNKNOWN"})
    void rejectedOrUnavailableCustomerDoesNotCreateAccount(String behavior) {
        mode=behavior;
        int expected=behavior.equals("MISSING")?422:behavior.equals("INACTIVE")?409:503;
        var r=rest.postForEntity("/accounts",new CreateAccountRequest(41L,AccountType.CURRENT,"BAM"),Map.class);
        assertThat(r.getStatusCode().value()).isEqualTo(expected);assertThat(repository.count()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"ZZZ","XXX","XAU"})
    void invalidMonetaryCurrencyReturns400(String code) {
        assertThat(rest.postForEntity("/accounts",new CreateAccountRequest(41L,AccountType.CURRENT,code),Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(repository.count()).isZero();
    }
    @Test void invalidBodyAndEnumsAre400() {
        assertThat(rest.postForEntity("/accounts",Map.of("customerId",0,"accountType","CURRENT","currency","BAM"),Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.postForEntity("/accounts",Map.of("customerId",41,"accountType","UNKNOWN","currency","BAM"),Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.postForEntity("/accounts",Map.of(),Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void missingAccountsAndInvalidPaginationAreExplicit() {
        assertThat(rest.getForEntity("/accounts/999999",Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/accounts/999999/balance",Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/accounts/customer/41?size=101",Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.getForEntity("/accounts/0",Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void optimisticLockRejectsStaleStatusUpdates() {
        var a=create(41L,AccountType.CURRENT,"BAM");var first=repository.findById(a.id()).orElseThrow();var stale=repository.findById(a.id()).orElseThrow();
        first.changeStatus(AccountStatus.BLOCKED,Instant.now());repository.saveAndFlush(first);
        stale.changeStatus(AccountStatus.CLOSED,Instant.now());
        assertThatThrownBy(()->repository.saveAndFlush(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repository.findById(a.id()).orElseThrow().getStatus()).isEqualTo(AccountStatus.BLOCKED);
    }
    @Test void migrationAndOpenApiArePresent() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND success=1",Integer.class)).isEqualTo(1);
        var doc=rest.getForObject("/v3/api-docs",Map.class);var paths=(Map<?,?>)doc.get("paths");
        assertThat(paths.containsKey("/accounts")).isTrue();assertThat(paths.containsKey("/accounts/{id}/balance")).isTrue();
    }
    @Test void accountCreationOutboxFailureRollsBackTheAccount() {
        jdbc.execute("ALTER TABLE event_outbox ADD CONSTRAINT injected_capture_failure CHECK (event_type<>'ACCOUNT_CREATED')");
        try {
            var response=rest.postForEntity("/accounts",new CreateAccountRequest(41L,AccountType.CURRENT,"BAM"),Map.class);
            assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
        } finally {jdbc.execute("ALTER TABLE event_outbox DROP CHECK injected_capture_failure");}
        assertThat(repository.count()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox",Integer.class)).isZero();
        create(41L,AccountType.CURRENT,"BAM");assertThat(jdbc.queryForObject("SELECT event_type FROM event_outbox",String.class)).isEqualTo("ACCOUNT_CREATED");
    }

}
