package com.mt.transactions.domain.exception;

public class TransactionConflictException extends RuntimeException {

    public TransactionConflictException(long id) {
        super("Transaction " + id + " already exists with different content");
    }
}
