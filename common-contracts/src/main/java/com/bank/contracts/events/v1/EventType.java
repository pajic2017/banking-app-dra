package com.bank.contracts.events.v1;
public enum EventType {
    CUSTOMER_CREATED("bank.customer.created","customer-service"),
    ACCOUNT_CREATED("bank.account.created","account-service"),
    TRANSACTION_COMPLETED("bank.transaction.completed","transaction-service"),
    TRANSACTION_FAILED("bank.transaction.failed","transaction-service"),
    SUSPICIOUS_TRANSACTION_DETECTED("bank.fraud.detected","fraud-detection-service");
    private final String topic;
    private final String producer;
    EventType(String topic,String producer) { this.topic=topic;this.producer=producer; }
    public String topic() { return topic; }
    public String producer() { return producer; }
}
