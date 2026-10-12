package com.bank.transactionservice.api;

import com.bank.contracts.posting.v1.PostingType;
import com.bank.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@org.springframework.security.access.prepost.PreAuthorize("@access.staff()")
@RestController
@Validated
@Tag(name="Transactions", description="Durable asynchronous monetary requests; poll Location for terminal status")
public class TransactionController {
    private final TransactionService service;
    public TransactionController(TransactionService service) { this.service = service; }

    @PostMapping("/transactions/deposit")
    public ResponseEntity<TransactionView> deposit(@RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9._:-]{1,100}") String key,
            @Valid @RequestBody MoneyRequest request) {
        return accepted(service.submitAuthenticated(key, PostingType.DEPOSIT, null, request.accountId(), request.amount(), request.currency(), request.description()));
    }
    @org.springframework.security.access.prepost.PreAuthorize("@transactionAccess.account(#request.accountId())")
    @PostMapping("/transactions/withdraw")
    public ResponseEntity<TransactionView> withdraw(@RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9._:-]{1,100}") String key,
            @Valid @RequestBody MoneyRequest request) {
        return accepted(service.submitAuthenticated(key, PostingType.WITHDRAWAL, request.accountId(), null, request.amount(), request.currency(), request.description()));
    }
    @org.springframework.security.access.prepost.PreAuthorize("@transactionAccess.account(#request.fromAccountId())")
    @PostMapping("/transfers")
    public ResponseEntity<TransactionView> transfer(@RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9._:-]{1,100}") String key,
            @Valid @RequestBody TransferRequest request) {
        return acceptedTransfer(service.submitAuthenticated(key, PostingType.TRANSFER, request.fromAccountId(), request.toAccountId(), request.amount(), request.currency(), request.description()));
    }
    @org.springframework.security.access.prepost.PreAuthorize("@transactionAccess.transaction(#id)")
    @GetMapping("/transactions/{id}")
    public TransactionView get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping("/transactions")
    public TransactionPage search(@RequestParam(required=false) TransactionStatus status, @RequestParam(required=false) PostingType type,
            @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.page(null, status, type, page, size);
    }
    @org.springframework.security.access.prepost.PreAuthorize("@transactionAccess.account(#accountId)")
    @GetMapping("/transactions/account/{accountId}")
    public TransactionPage history(@PathVariable @Positive long accountId,
            @RequestParam(required=false) TransactionStatus status, @RequestParam(required=false) PostingType type,
            @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.page(accountId, status, type, page, size);
    }
    private ResponseEntity<TransactionView> acceptedTransfer(TransactionView result) {
        return response(result, "transactions/" + result.id());
    }
    private ResponseEntity<TransactionView> accepted(TransactionView result) {
        return response(result, "../transactions/" + result.id());
    }
    private ResponseEntity<TransactionView> response(TransactionView result, String location) {
        // Relative to the root public /api path, including when submitted at /api/transfers.
        return ResponseEntity.status(result.status() == TransactionStatus.PENDING ? 202 : 200)
                .location(URI.create(location)).body(result);
    }
}
