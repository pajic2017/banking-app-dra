package com.bank.customerservice.service;
public class AccountOverviewUnavailable extends RuntimeException {
    public AccountOverviewUnavailable() { super("Account overview is unavailable; retry later."); }
}
