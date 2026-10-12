package com.bank.accountservice.api;
import com.bank.accountservice.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.tags.Tag;
@org.springframework.security.access.prepost.PreAuthorize("@access.staff()")
@RestController
@RequestMapping("/accounts")
@Validated
@Tag(name="Accounts",description="Account lifecycle; no monetary mutations in Phase 3")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service=service; }
    @PostMapping public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        var result=service.create(request);return ResponseEntity.created(URI.create("accounts/"+result.id())).body(result);
    }
    @org.springframework.security.access.prepost.PreAuthorize("@accountAccess.read(#id)")
    @GetMapping("/{id}") public AccountResponse get(@PathVariable @Positive long id) { return service.get(id); }
    @org.springframework.security.access.prepost.PreAuthorize("@access.staff() or @access.customer(#customerId)")
    @GetMapping("/customer/{customerId}") public AccountPage byCustomer(@PathVariable @Positive long customerId,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.byCustomer(customerId,page,size); }
    @org.springframework.security.access.prepost.PreAuthorize("@accountAccess.read(#id)")
    @GetMapping("/{id}/balance") public BalanceResponse balance(@PathVariable @Positive long id) { return service.balance(id); }
    @PatchMapping("/{id}/status") public AccountResponse status(@PathVariable @Positive long id,@Valid @RequestBody AccountStatusRequest request) { return service.status(id,request.status()); }
}
