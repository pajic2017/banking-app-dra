package com.bank.contracts.events.v1;

import com.bank.contracts.posting.v1.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Explicit v1 JSON envelope. Contains identifiers/business values, never customer contact details. */
public record BankEventV1(UUID eventId, EventType eventType, int version, UUID correlationId,
        String producer, Instant occurredAt, String aggregateId, Map<String,String> data) {
    public BankEventV1 {
        if (eventId==null || eventType==null || version!=1 || correlationId==null || occurredAt==null ||
                !eventType.producer().equals(producer) || aggregateId==null || data==null) {
            throw new IllegalArgumentException("Invalid or unsupported event envelope.");
        }
        data=Collections.unmodifiableMap(new TreeMap<>(data));
        switch(eventType) {
            case CUSTOMER_CREATED -> { positive(data,"customerId");match(aggregateId,data.get("customerId")); }
            case ACCOUNT_CREATED -> {
                positive(data,"accountId");positive(data,"customerId");match(aggregateId,data.get("accountId"));
                if (!Set.of("CURRENT","SAVINGS").contains(data.get("accountType"))) throw new IllegalArgumentException("Invalid account type.");
                if (Currency.getInstance(required(data,"currency")).getDefaultFractionDigits()<0) throw new IllegalArgumentException("Invalid currency.");
            }
            case TRANSACTION_COMPLETED, TRANSACTION_FAILED -> {
                var id=UUID.fromString(required(data,"transactionId"));match(aggregateId,id.toString());
                new PostingCommandV1(id,PostingType.valueOf(required(data,"operationType")),
                        optionalId(data,"fromAccountId"),optionalId(data,"toAccountId"),
                        new BigDecimal(required(data,"amount")),required(data,"currency"));
                if (eventType==EventType.TRANSACTION_FAILED) required(data,"failureCode");
            }
            case SUSPICIOUS_TRANSACTION_DETECTED -> { positive(data,"accountId");required(data,"reason"); }
        }
    }
    private static String required(Map<String,String> data,String key) {
        var value=data.get(key);if(value==null || value.isBlank() || value.length()>500) throw new IllegalArgumentException("Invalid event data.");return value;
    }
    private static Long optionalId(Map<String,String> data,String key) { return data.containsKey(key)?positive(data,key):null; }
    private static long positive(Map<String,String> data,String key) {
        long id=Long.parseLong(required(data,key));if(id<=0)throw new IllegalArgumentException("Invalid event reference.");return id;
    }
    private static void match(String aggregate,String reference) { if(!aggregate.equals(reference)) throw new IllegalArgumentException("Mismatched event aggregate."); }
}
