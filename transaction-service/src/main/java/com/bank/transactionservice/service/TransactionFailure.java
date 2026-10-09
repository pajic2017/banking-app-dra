package com.bank.transactionservice.service;
import org.springframework.http.HttpStatus;
public class TransactionFailure extends RuntimeException {
    private final HttpStatus status;
    public TransactionFailure(HttpStatus status, String detail) { super(detail); this.status = status; }
    public HttpStatus status() { return status; }
}
