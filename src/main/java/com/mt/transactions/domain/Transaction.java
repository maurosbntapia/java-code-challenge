package com.mt.transactions.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable transaction. Amounts are strictly positive and kept as {@link BigDecimal}
 * to avoid floating point errors when summing.
 */
public record Transaction(long id, BigDecimal amount, TransactionType type, Long parentId) {

    public Transaction {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (type == null) {
            throw new IllegalArgumentException("Transaction type is required");
        }
    }

    /**
     * Whether both transactions carry the same content. Amounts are compared with
     * {@code compareTo} so that scale is ignored (5000 equals 5000.0).
     */
    public boolean sameContentAs(Transaction other) {
        return amount.compareTo(other.amount) == 0
                && type.equals(other.type)
                && Objects.equals(parentId, other.parentId);
    }
}
