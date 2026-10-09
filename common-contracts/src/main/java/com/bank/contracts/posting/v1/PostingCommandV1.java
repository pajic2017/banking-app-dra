package com.bank.contracts.posting.v1;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Currency;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** Versioned wire instruction, never a shared persistence entity. */
public record PostingCommandV1(UUID operationId, PostingType type, Long fromAccountId,
        Long toAccountId, BigDecimal amount, String currency) {
    public static final BigDecimal MAX_BALANCE = new BigDecimal("999999999999999.9999");

    public PostingCommandV1 {
        if (operationId == null || type == null || amount == null || currency == null) {
            throw new IllegalArgumentException("Operation, type, amount and currency are required.");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        int scale;
        try { scale = Currency.getInstance(currency).getDefaultFractionDigits(); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Unsupported ISO monetary currency."); }
        if (scale < 0 || scale > 4) throw new IllegalArgumentException("Unsupported ISO monetary currency.");
        if (amount.signum() <= 0 || amount.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException("Amount must be positive and within DECIMAL(19,4) capacity.");
        }
        if (amount.stripTrailingZeros().scale() > scale) throw new IllegalArgumentException("Amount exceeds currency precision.");
        try { amount = amount.setScale(scale, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException("Amount exceeds currency precision."); }
        boolean valid = switch (type) {
            case DEPOSIT -> fromAccountId == null && positive(toAccountId);
            case WITHDRAWAL -> positive(fromAccountId) && toAccountId == null;
            case TRANSFER -> positive(fromAccountId) && positive(toAccountId) && !fromAccountId.equals(toAccountId);
        };
        if (!valid) throw new IllegalArgumentException("Invalid accounts for posting type; transfers require different accounts.");
    }

    private static boolean positive(Long id) { return id != null && id > 0; }

    public String instructionHash() {
        return hash(type + "|" + fromAccountId + "|" + toAccountId + "|" +
                amount.stripTrailingZeros().toPlainString() + "|" + currency);
    }

    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
