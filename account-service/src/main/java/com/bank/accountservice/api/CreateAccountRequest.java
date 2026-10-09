package com.bank.accountservice.api;
import com.bank.accountservice.domain.AccountType;
import jakarta.validation.constraints.*;
import java.util.Locale;
public record CreateAccountRequest(@NotNull @Positive Long customerId, @NotNull AccountType accountType,
                                   @NotBlank @Pattern(regexp="[A-Z]{3}") String currency) {
    public CreateAccountRequest { currency = currency == null ? null : currency.trim().toUpperCase(Locale.ROOT); }
}
