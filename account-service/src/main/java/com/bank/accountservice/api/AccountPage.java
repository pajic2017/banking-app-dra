package com.bank.accountservice.api;
import java.util.List;
import org.springframework.data.domain.Page;
public record AccountPage(List<AccountResponse> items, int page, int size, long totalElements, int totalPages) {
    public static AccountPage from(Page<AccountResponse> p) { return new AccountPage(p.getContent(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages()); }
}
