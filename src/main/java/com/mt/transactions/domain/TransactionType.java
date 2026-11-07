package com.mt.transactions.domain;

import java.util.Locale;

/**
 * Normalized transaction type: trimmed and lower-cased with {@link Locale#ROOT}
 * so that "CARS", " Cars " and "cars" are the same type regardless of server locale.
 */
public record TransactionType(String value) {

    public TransactionType {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Transaction type must not be blank");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
    }
}
