package com.bank.customerservice.api;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable pagination contract independent of Spring's internal Page serialization. */
public record CustomerPage(List<CustomerResponse> items, int page, int size,
                           long totalElements, int totalPages) {
    public static CustomerPage from(Page<CustomerResponse> result) {
        return new CustomerPage(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
