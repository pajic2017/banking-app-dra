package com.bank.accountservice.service;

import com.bank.accountservice.client.CustomerClient;
import com.bank.accountservice.domain.*;
import com.bank.accountservice.repository.AccountRepository;
import com.bank.contracts.posting.v1.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@org.springframework.context.annotation.Import({com.bank.security.SecurityConfiguration.class,com.bank.security.fixtures.SecurityTestConfiguration.class})
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "CONFIG_IMPORT=optional:classpath:/no-config-client.yml","bank.events.relay-enabled=false","spring.cloud.config.enabled=false","eureka.client.enabled=false"})
class PostingIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("accounts");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",MYSQL::getJdbcUrl); p.add("spring.datasource.username",MYSQL::getUsername); p.add("spring.datasource.password",MYSQL::getPassword);
    }
    @MockitoBean CustomerClient customers;
    @Autowired AccountRepository accounts;
    @Autowired PostingService postings;
    @Autowired LedgerReconciliation reconciliation;
    @Autowired AccountService lifecycle;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate rest;
    @BeforeEach void authenticatedRequests() { rest.getRestTemplate().setInterceptors(java.util.List.of((request,body,execution) -> { if(!request.getHeaders().containsKey("Authorization"))request.getHeaders().setBearerAuth("posting");return execution.execute(request,body); })); }

    @BeforeEach void reset() {
        jdbc.update("DELETE FROM ledger_entries"); jdbc.update("DELETE FROM posting_receipts"); accounts.deleteAllInBatch();
    }
    private long account(String currency) {
        return accounts.saveAndFlush(new Account(1L,"BA"+UUID.randomUUID().toString().replace("-",""),AccountType.CURRENT,currency,Instant.now())).getId();
    }
    private PostingCommandV1 command(PostingType type,Long from,Long to,String amount) {
        return new PostingCommandV1(UUID.randomUUID(),type,from,to,new BigDecimal(amount),"BAM");
    }
    private void deposit(long id,String amount) { assertThat(postings.post(command(PostingType.DEPOSIT,null,id,amount)).status()).isEqualTo(PostingStatus.COMPLETED); }
    private BigDecimal balance(long id) { return accounts.findById(id).orElseThrow().getBalance(); }
    private void healthyLedger() { assertThat(reconciliation.check("BAM").balanced()).isTrue(); }

    @Test void depositWithdrawTransferAndClearingAreBalanced() {
        long a=account("BAM"),b=account("BAM");deposit(a,"100");
        assertThat(postings.post(command(PostingType.TRANSFER,a,b,"35")).status()).isEqualTo(PostingStatus.COMPLETED);
        assertThat(postings.post(command(PostingType.WITHDRAWAL,b,null,"5")).status()).isEqualTo(PostingStatus.COMPLETED);
        assertThat(balance(a)).isEqualByComparingTo("65");assertThat(balance(b)).isEqualByComparingTo("30");
        var report=reconciliation.check("BAM");assertThat(report.customerBalanceTotal()).isEqualByComparingTo("95");
        assertThat(report.clearingLedgerTotal()).isEqualByComparingTo("-95");healthyLedger();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries",Integer.class)).isEqualTo(6);
        assertThatThrownBy(() -> lifecycle.status(a,AccountStatus.CLOSED)).isInstanceOf(AccountRuleViolation.class);
    }
    @Test void duplicateConcurrentPostsApplyExactlyOnce() throws Exception {
        long a=account("BAM");var command=command(PostingType.DEPOSIT,null,a,"100");
        var results=parallel(12, i -> postings.post(command));
        assertThat(results).allMatch(r -> r.status()==PostingStatus.COMPLETED);
        assertThat(results).allMatch(r -> r.equals(results.get(0)));
        assertThat(balance(a)).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries",Integer.class)).isEqualTo(2);healthyLedger();
    }
    @Test void sameIdDifferentPayloadIs409AndNeverMutatesBalance() {
        long a=account("BAM");var c=command(PostingType.DEPOSIT,null,a,"10");postings.post(c);
        var conflicting=new PostingCommandV1(c.operationId(),c.type(),null,a,new BigDecimal("11"),"BAM");
        assertThat(rest.postForEntity("/internal/v1/postings",conflicting,Map.class).getStatusCode().value()).isEqualTo(409);
        assertThat(balance(a)).isEqualByComparingTo("10");healthyLedger();
    }
    @Test void insufficientFundsMissingBlockedAndCurrencyMismatchRejectWithoutLedgerChanges() {
        long a=account("BAM"),b=account("BAM"),jpy=account("JPY");deposit(a,"10");
        var insufficient=command(PostingType.TRANSFER,a,b,"11");
        assertThat(postings.post(insufficient).failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
        deposit(a,"1");assertThat(postings.post(insufficient).failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(postings.post(command(PostingType.TRANSFER,a,999999L,"1")).failureCode()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(postings.post(command(PostingType.TRANSFER,a,jpy,"1")).failureCode()).isEqualTo("CURRENCY_MISMATCH");
        lifecycle.status(b,AccountStatus.BLOCKED);
        assertThat(postings.post(command(PostingType.TRANSFER,a,b,"1")).failureCode()).isEqualTo("ACCOUNT_NOT_ACTIVE");
        assertThat(balance(a)).isEqualByComparingTo("11");assertThat(balance(b)).isZero();healthyLedger();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries",Integer.class)).isEqualTo(4);
    }
    @Test void balanceCapacityIsCheckedBeforeEitherSideChanges() {
        long a=account("BAM"),b=account("BAM");deposit(a,"10");deposit(b,"999999999999999.99");
        assertThat(postings.post(command(PostingType.TRANSFER,a,b,"1")).failureCode()).isEqualTo("BALANCE_LIMIT");
        assertThat(balance(a)).isEqualByComparingTo("10");assertThat(balance(b)).isEqualByComparingTo("999999999999999.99");healthyLedger();
    }
    @Test void twentyConcurrentTransfersCannotOverspendOrCreateMoney() throws Exception {
        long a=account("BAM"),b=account("BAM");deposit(a,"100");
        var results=parallel(20,i -> postings.post(command(PostingType.TRANSFER,a,b,"10")));
        assertThat(results.stream().filter(r->r.status()==PostingStatus.COMPLETED).count()).isEqualTo(10);
        assertThat(results.stream().filter(r->r.status()==PostingStatus.REJECTED).count()).isEqualTo(10);
        assertThat(balance(a)).isZero();assertThat(balance(b)).isEqualByComparingTo("100");healthyLedger();
    }
    @Test void oppositeDirectionTransfersUseConsistentLockOrder() throws Exception {
        long a=account("BAM"),b=account("BAM");deposit(a,"100");deposit(b,"100");
        var results=parallel(20,i -> postings.post(command(PostingType.TRANSFER,i%2==0?a:b,i%2==0?b:a,"1")));
        assertThat(results).allMatch(r->r.status()==PostingStatus.COMPLETED);
        assertThat(balance(a)).isEqualByComparingTo("100");assertThat(balance(b)).isEqualByComparingTo("100");healthyLedger();
    }
    @Test void databaseFailureAfterDebitRollsBackBalancesLedgerAndReceiptThenRetrySucceeds() {
        long a=account("BAM"),b=account("BAM");deposit(a,"100");var c=command(PostingType.TRANSFER,a,b,"25");
        jdbc.execute("ALTER TABLE ledger_entries ADD CONSTRAINT injected_credit_failure CHECK (NOT(operation_id='"+c.operationId()+"' AND line_no=2))");
        try { assertThatThrownBy(()->postings.post(c)).isInstanceOf(org.springframework.dao.DataAccessException.class); }
        finally {jdbc.execute("ALTER TABLE ledger_entries DROP CHECK injected_credit_failure");}
        assertThat(balance(a)).isEqualByComparingTo("100");assertThat(balance(b)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posting_receipts WHERE operation_id=?",Integer.class,c.operationId().toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries",Integer.class)).isEqualTo(2);healthyLedger();
        assertThat(postings.post(c).status()).isEqualTo(PostingStatus.COMPLETED);
        assertThat(balance(a)).isEqualByComparingTo("75");assertThat(balance(b)).isEqualByComparingTo("25");healthyLedger();
    }
    @Test void reconciliationDetectsManualProjectionCorruption() {
        long a=account("BAM");deposit(a,"10");jdbc.update("UPDATE accounts SET balance=11 WHERE id=?",a);
        var report=reconciliation.check("BAM");assertThat(report.balanced()).isFalse();assertThat(report.accountMismatches()).isEqualTo(1);
    }
    private List<PostingResultV1> parallel(int count,java.util.function.IntFunction<PostingResultV1> action) throws Exception {
        var pool=Executors.newFixedThreadPool(8);
        try {
            var futures=new ArrayList<Future<PostingResultV1>>();
            for(int i=0;i<count;i++){final int n=i;futures.add(pool.submit(()->action.apply(n)));}
            var results=new ArrayList<PostingResultV1>();for(var f:futures)results.add(f.get(30,TimeUnit.SECONDS));return results;
        } finally { pool.shutdownNow(); }
    }
}
