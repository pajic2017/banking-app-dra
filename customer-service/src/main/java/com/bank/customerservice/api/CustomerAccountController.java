package com.bank.customerservice.api;
import com.bank.customerservice.service.*;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
@RestController
@Validated
@RequestMapping("/customers/{id}/accounts")
public class CustomerAccountController {
    private final CustomerService customers;
    private final AccountOverviewClient accounts;
    public CustomerAccountController(CustomerService customers,AccountOverviewClient accounts) { this.customers=customers;this.accounts=accounts; }
    @GetMapping
    @Operation(summary="Get a customer's account overview through Account Service")
    public CustomerAccountPage overview(@PathVariable @Positive long id,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        customers.get(id); // Read transaction ends before the downstream request.
        return accounts.get(id,page,size);
    }
}
