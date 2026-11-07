package com.mt.transactions.domain.exception;

public class ParentNotFoundException extends RuntimeException {

    public ParentNotFoundException(long parentId) {
        super("Parent transaction " + parentId + " not found");
    }
}
