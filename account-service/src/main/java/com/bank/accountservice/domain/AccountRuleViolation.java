package com.bank.accountservice.domain;
/** Domain rules are mapped to HTTP only by the API exception handler. */
public class AccountRuleViolation extends RuntimeException {
    public AccountRuleViolation(String message) { super(message); }
}
