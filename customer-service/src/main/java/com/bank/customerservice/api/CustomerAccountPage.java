package com.bank.customerservice.api;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown=true)
public record CustomerAccountPage(List<AccountSummary> items, int page, int size, long totalElements, int totalPages) {
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record AccountSummary(Long id, Long customerId, String accountNumber, String accountType,
                                 String currency, String status, BigDecimal balance, Instant createdAt, Instant updatedAt) { }
}
