package com.bank.customerservice.service;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(long id) { super("Customer " + id + " was not found"); }
}
