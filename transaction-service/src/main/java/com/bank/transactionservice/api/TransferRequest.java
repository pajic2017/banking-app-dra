package com.bank.transactionservice.api;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record TransferRequest(@NotNull @Positive Long fromAccountId, @NotNull @Positive Long toAccountId,
        @NotNull BigDecimal amount, @NotBlank String currency, @Size(max=240) String description) { }
