package com.bank.accountservice.domain;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;
import java.math.BigDecimal;
import java.time.Instant;
@Entity
@Table(name="accounts")
public class Account {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="customer_id",nullable=false) private Long customerId;
    @Column(name="account_number",nullable=false,length=34) private String accountNumber;
    @Enumerated(EnumType.STRING) @Column(name="account_type",nullable=false,length=16) private AccountType accountType;
    @JdbcTypeCode(Types.CHAR) @Column(nullable=false,length=3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private AccountStatus status;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal balance;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;
    protected Account() { }
    public Account(Long customerId, String accountNumber, AccountType type, String currency, Instant now) {
        this.customerId=customerId; this.accountNumber=accountNumber; this.accountType=type; this.currency=currency;
        status=AccountStatus.ACTIVE; balance=BigDecimal.ZERO; createdAt=now; updatedAt=now;
    }
    public void changeStatus(AccountStatus target, Instant now) {
        if (status==target) return;
        if (status==AccountStatus.CLOSED) throw new AccountRuleViolation("Closed accounts cannot be reopened.");
        if (target==AccountStatus.CLOSED && balance.signum()!=0) throw new AccountRuleViolation("An account with a nonzero balance cannot be closed.");
        status=target; updatedAt=now;
    }
    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public String getAccountNumber() { return accountNumber; }
    public AccountType getAccountType() { return accountType; }
    public String getCurrency() { return currency; }
    public AccountStatus getStatus() { return status; }
    public BigDecimal getBalance() { return balance; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
