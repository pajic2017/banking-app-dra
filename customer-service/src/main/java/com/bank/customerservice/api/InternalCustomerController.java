package com.bank.customerservice.api;
import java.util.Map;
import com.bank.customerservice.service.CustomerService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
public class InternalCustomerController {
    private final CustomerService customers;
    public InternalCustomerController(CustomerService customers) { this.customers=customers; }
    @GetMapping("/internal/v1/customers/{id}/status")
    @PreAuthorize("hasAuthority('SERVICE_ACCOUNT')")
    public Map<String,Object> status(@PathVariable long id) { var c=customers.get(id);return Map.of("id",c.id(),"status",c.status()); }
}
