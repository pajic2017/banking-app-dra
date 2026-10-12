package com.bank.accountservice.api;
import com.bank.accountservice.service.AccountService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;
@RestController
@Validated
public class InternalAccountController {
    private final AccountService accounts;
    public InternalAccountController(AccountService accounts) { this.accounts=accounts; }
    @GetMapping("/internal/v1/accounts/customer/{id}")
    @PreAuthorize("hasAuthority('SERVICE_CUSTOMER')")
    public AccountPage overview(@PathVariable @Positive long id,@RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return accounts.byCustomer(id,page,size); }
}
