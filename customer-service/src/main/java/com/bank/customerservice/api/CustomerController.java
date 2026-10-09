package com.bank.customerservice.api;

import com.bank.customerservice.domain.CustomerStatus;
import com.bank.customerservice.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/customers")
@Validated
@Tag(name = "Customers", description = "Customer lifecycle; records are never physically deleted")
public class CustomerController {
    private final CustomerService service;
    public CustomerController(CustomerService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Create an active customer")
    @ApiResponse(responseCode = "201", description = "Customer created")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "409", description = "Email already registered")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        var customer = service.create(request);
        // A relative Location resolves against either /customers or /api/customers.
        var location = URI.create("customers/" + customer.id());
        return ResponseEntity.created(location).body(customer);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a customer, including inactive customers")
    @ApiResponse(responseCode = "404", description = "Customer does not exist")
    public CustomerResponse get(@PathVariable @Positive long id) { return service.get(id); }

    @GetMapping
    @Operation(summary = "List customers in stable ID order, optionally filtered by status")
    public CustomerPage list(@RequestParam(defaultValue = "0") @Min(0) int page,
                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                             @RequestParam(required = false) CustomerStatus status) {
        return service.list(page, size, status);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update customer information without changing status")
    @ApiResponse(responseCode = "404", description = "Customer does not exist")
    @ApiResponse(responseCode = "409", description = "Email or concurrent-update conflict")
    public CustomerResponse update(@PathVariable @Positive long id, @Valid @RequestBody CustomerRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a customer; repeated deactivation succeeds")
    @ApiResponse(responseCode = "204", description = "Customer deactivated")
    @ApiResponse(responseCode = "404", description = "Customer does not exist")
    public ResponseEntity<Void> deactivate(@PathVariable @Positive long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
