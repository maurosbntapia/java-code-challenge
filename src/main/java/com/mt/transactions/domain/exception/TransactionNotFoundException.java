package com.mt.transactions.domain.exception;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(long id) {
        super("Transaction " + id + " not found");
    }
}
