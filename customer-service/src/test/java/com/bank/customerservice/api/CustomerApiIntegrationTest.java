package com.bank.customerservice.api;

import com.bank.customerservice.domain.CustomerStatus;
import com.bank.customerservice.repository.CustomerRepository;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.dao.OptimisticLockingFailureException;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@org.springframework.context.annotation.Import({com.bank.security.SecurityConfiguration.class,com.bank.security.fixtures.SecurityTestConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "CONFIG_IMPORT=optional:classpath:/no-config-client.yml", "bank.events.relay-enabled=false","spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"})
class CustomerApiIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("customers");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired TestRestTemplate rest;
    @BeforeEach void authenticatedRequests() { rest.getRestTemplate().setInterceptors(java.util.List.of((request,body,execution) -> { if(!request.getHeaders().containsKey("Authorization"))request.getHeaders().setBearerAuth("admin");return execution.execute(request,body); })); }

    @Autowired CustomerRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.bank.customerservice.events.OutboxRelay relay;
    @Autowired com.bank.customerservice.service.CustomerService service;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.springframework.kafka.core.KafkaTemplate<String,String> kafka;


    @BeforeEach void reset() { jdbc.update("DELETE FROM event_outbox");repository.deleteAllInBatch();org.mockito.Mockito.reset(kafka); }

    @Test void completeLifecycleRetainsTheRowAndCreationTime() {
        var created = create(" CUSTOMER@EXAMPLE.COM ");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var customer = created.getBody();
        assertThat(customer).isNotNull();
        assertThat(created.getHeaders().getLocation().getPath()).isEqualTo("customers/" + customer.id());
        assertThat(customer.email()).isEqualTo("customer@example.com");
        assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(rest.getForEntity("/customers/" + customer.id(), CustomerResponse.class).getBody()).isEqualTo(customer);

        var updated = rest.exchange("/customers/" + customer.id(), HttpMethod.PUT,
                new HttpEntity<>(new CustomerRequest("Updated", "Name", "new@example.com", "+38762123456")), CustomerResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().firstName()).isEqualTo("Updated");
        assertThat(updated.getBody().createdAt()).isEqualTo(customer.createdAt());

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThat(rest.exchange("/customers/" + customer.id(), HttpMethod.DELETE, HttpEntity.EMPTY, Void.class)
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
        var persisted = repository.findById(customer.id()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
        assertThat(repository.count()).isEqualTo(1);
        var inactive = rest.getForEntity("/customers/" + customer.id(), CustomerResponse.class).getBody();
        assertThat(inactive.status()).isEqualTo(CustomerStatus.INACTIVE);
        assertThat(inactive.createdAt()).isEqualTo(customer.createdAt());
    }

    @Test void listIsPaginatedInIdOrderAndFiltersStatus() {
        var first = create("first@example.com").getBody();
        var second = create("second@example.com").getBody();
        rest.delete("/customers/" + first.id());
        var page = rest.getForObject("/customers?page=1&size=1", CustomerPage.class);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items()).extracting(CustomerResponse::id).containsExactly(second.id());
        var active = rest.getForObject("/customers?status=ACTIVE", CustomerPage.class);
        assertThat(active.items()).extracting(CustomerResponse::id).containsExactly(second.id());
        var inactive = rest.getForObject("/customers?status=INACTIVE", CustomerPage.class);
        assertThat(inactive.items()).extracting(CustomerResponse::id).containsExactly(first.id());
    }

    @Test void rejectsInvalidCustomerWithFieldErrorsAndNoInsert() {
        var bad = new CustomerRequest(" ", " ", "invalid", "123");
        var response = rest.postForEntity("/customers", bad, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        var errors = (Map<?, ?>) response.getBody().get("errors");
        for (var field : List.of("firstName", "lastName", "email", "phone")) {
            assertThat(errors.containsKey(field)).as("field error: " + field).isTrue();
        }
        assertThat(repository.count()).isZero();
    }

    @Test void duplicateNormalizedEmailReturnsConflictWithoutSecondInsert() {
        create("same@example.com");
        var response = rest.postForEntity("/customers", request(" SAME@EXAMPLE.COM "), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().toString()).doesNotContain("insert into", "same@example.com");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test void conflictingUpdateRollsBackAllInformation() {
        var first = create("first@example.com").getBody();
        create("second@example.com");
        var response = rest.exchange("/customers/" + first.id(), HttpMethod.PUT,
                new HttpEntity<>(new CustomerRequest("Should rollback", "Name", "second@example.com", "+38762123456")), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var unchanged = rest.getForObject("/customers/" + first.id(), CustomerResponse.class);
        assertThat(unchanged).isEqualTo(first);
    }

    @Test void simultaneousDuplicateRequestsCannotCreateTwoCustomers() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Integer> call = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
            return rest.postForEntity("/customers", request("concurrent@example.com"), Map.class).getStatusCode().value();
        };
        try {
            var a = executor.submit(call); var b = executor.submit(call);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
            assertThat(repository.count()).isEqualTo(1);
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test void missingRecordsReturn404ForReadUpdateAndDeactivation() {
        assertThat(rest.getForEntity("/customers/999999", Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.exchange("/customers/999999", HttpMethod.PUT, new HttpEntity<>(request("x@example.com")), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.exchange("/customers/999999", HttpMethod.DELETE, HttpEntity.EMPTY, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/customers?page=-1", "/customers?size=0", "/customers?size=101",
            "/customers?status=UNKNOWN", "/customers/not-a-number", "/customers/0"})
    void invalidParametersReturn400(String url) {
        assertThat(rest.getForEntity(url, Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test void malformedAndMissingBodiesReturn400() {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        assertThat(rest.postForEntity("/customers", new HttpEntity<>("{", headers), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.postForEntity("/customers", new HttpEntity<>("{}", headers), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test void inactiveCustomerKeepsItsReservedEmail() {
        var customer = create("reserved@example.com").getBody();
        rest.delete("/customers/" + customer.id());
        assertThat(rest.postForEntity("/customers", request("RESERVED@example.com"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test void staleDatabaseUpdateCannotOverwriteAnAlreadyCommittedChange() {
        var customer = create("version@example.com").getBody();
        var first = repository.findById(customer.id()).orElseThrow();
        var stale = repository.findById(customer.id()).orElseThrow();
        first.update("First update", "Name", "version@example.com", "+38761123456", Instant.now());
        repository.saveAndFlush(first);
        stale.update("Stale update", "Name", "version@example.com", "+38761123456", Instant.now());
        assertThatThrownBy(() -> repository.saveAndFlush(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repository.findById(customer.id()).orElseThrow().getFirstName()).isEqualTo("First update");
    }

    @Test void flywayMigrationAndOpenApiAreAvailable() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1", Integer.class))
                .isEqualTo(1);
        var response = rest.getForEntity("/v3/api-docs", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var paths = (Map<?, ?>) response.getBody().get("paths");
        assertThat(paths.containsKey("/customers")).isTrue();
        assertThat(paths.containsKey("/customers/{id}")).isTrue();
    }

    private CustomerRequest request(String email) { return new CustomerRequest("Dragan", "Pajic", email, "+38761123456"); }
    private ResponseEntity<CustomerResponse> create(String email) {
        var result = rest.postForEntity("/customers", request(email), CustomerResponse.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return result;
    }
    @Test void businessAndOutboxCaptureAreAtomicAndExcludeContactDetails() {
        jdbc.execute("ALTER TABLE event_outbox ADD CONSTRAINT injected_capture_failure CHECK (event_type<>'CUSTOMER_CREATED')");
        try {assertThatThrownBy(()->service.create(new CustomerRequest("A","B","atomic@example.com","+38761123456"))).isInstanceOf(org.springframework.dao.DataAccessException.class);}
        finally {jdbc.execute("ALTER TABLE event_outbox DROP CHECK injected_capture_failure");}
        assertThat(repository.count()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox",Integer.class)).isZero();
        var customer=create("atomic@example.com").getBody();
        var payload=jdbc.queryForObject("SELECT payload FROM event_outbox",String.class);
        assertThat(payload).contains("CUSTOMER_CREATED",customer.id().toString()).doesNotContain("atomic@example.com","firstName","phone");
    }
    @Test void brokerFailureRetainsPendingEventThenRetryPublishesSameId() {
        create("relay@example.com");
        org.mockito.Mockito.when(kafka.send(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new java.util.concurrent.TimeoutException()),java.util.concurrent.CompletableFuture.completedFuture(null));
        assertThat(relay.publishNext()).isTrue();assertThat(relay.publishNext()).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM event_outbox",String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT attempts FROM event_outbox",Integer.class)).isEqualTo(1);
        jdbc.update("UPDATE event_outbox SET next_attempt_at='2000-01-01'");assertThat(relay.publishNext()).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM event_outbox",String.class)).isEqualTo("PUBLISHED");
        var payload=org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(kafka,org.mockito.Mockito.times(2)).send(org.mockito.ArgumentMatchers.eq("bank.customer.created"),org.mockito.ArgumentMatchers.anyString(),payload.capture());
        assertThat(payload.getAllValues().get(0)).isEqualTo(payload.getAllValues().get(1));
    }
    @Test void brokerAckBeforeDatabaseFailureCanReplayIdenticalEnvelope() {
        create("ack@example.com");
        org.mockito.Mockito.when(kafka.send(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        jdbc.execute("ALTER TABLE event_outbox ADD CONSTRAINT injected_mark_failure CHECK (status<>'PUBLISHED')");
        try {assertThatThrownBy(()->relay.publishNext()).isInstanceOf(org.springframework.dao.DataAccessException.class);}
        finally {jdbc.execute("ALTER TABLE event_outbox DROP CHECK injected_mark_failure");}
        assertThat(jdbc.queryForObject("SELECT status FROM event_outbox",String.class)).isEqualTo("PENDING");
        assertThat(relay.publishNext()).isTrue();
        var payload=org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(kafka,org.mockito.Mockito.times(2)).send(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),payload.capture());
        assertThat(payload.getAllValues().get(0)).isEqualTo(payload.getAllValues().get(1));
    }


    @Test void customersCannotReadOrUpdateOthersAndCannotListOrCreate() {
        var own=create("owner@example.com").getBody();var other=create("other@example.com").getBody();
        var headers=new HttpHeaders();headers.setBearerAuth("customer-"+own.id());
        assertThat(rest.exchange("/customers/"+own.id(),HttpMethod.GET,new HttpEntity<>(headers),Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.exchange("/customers/"+other.id(),HttpMethod.GET,new HttpEntity<>(headers),Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange("/customers/"+other.id(),HttpMethod.PUT,new HttpEntity<>(new CustomerRequest("Changed","Name","changed@example.com","+38761123456"),headers),Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(service.get(other.id()).email()).isEqualTo("other@example.com");
        assertThat(rest.exchange("/customers",HttpMethod.GET,new HttpEntity<>(headers),Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.postForEntity("/customers",new HttpEntity<>(new CustomerRequest("New","Name","new@example.com","+38761123456"),headers),Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test void humanAdminCannotUseInternalStatusAndServiceDoesNotExposeContactData() {
        var row=create("internal@example.com").getBody();String path="/internal/v1/customers/"+row.id()+"/status";
        assertThat(rest.getForEntity(path,Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var headers=new HttpHeaders();headers.setBearerAuth("account-service");
        var response=rest.exchange(path,HttpMethod.GET,new HttpEntity<>(headers),Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(response.getBody().keySet()).containsExactlyInAnyOrder("id","status");
        assertThat(rest.exchange("/customers/"+row.id(),HttpMethod.GET,new HttpEntity<>(headers),Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
