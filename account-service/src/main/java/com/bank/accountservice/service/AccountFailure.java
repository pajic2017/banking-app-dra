package com.bank.accountservice.service;
import org.springframework.http.HttpStatus;
public class AccountFailure extends RuntimeException {
    private final HttpStatus status;
    public AccountFailure(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
}
