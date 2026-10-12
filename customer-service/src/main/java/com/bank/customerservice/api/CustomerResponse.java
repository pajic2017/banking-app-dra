package com.bank.customerservice.api;

import com.bank.customerservice.domain.Customer;
import com.bank.customerservice.domain.CustomerStatus;
import java.time.Instant;

public record CustomerResponse(Long id, String firstName, String lastName, String email,
                               String phone, CustomerStatus status, Instant createdAt, Instant updatedAt) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getFirstName(), customer.getLastName(),
                customer.getEmail(), customer.getPhone(), customer.getStatus(),
                customer.getCreatedAt(), customer.getUpdatedAt());
    }
}
