package com.bank.transactionservice.api;
import java.util.List;
public record TransactionPage(List<TransactionView> items, int page, int size, long totalElements, int totalPages) { }
