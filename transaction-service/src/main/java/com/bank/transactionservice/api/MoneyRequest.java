package com.bank.transactionservice.api;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record MoneyRequest(@NotNull @Positive Long accountId, @NotNull BigDecimal amount,
        @NotBlank String currency, @Size(max=240) String description) { }
