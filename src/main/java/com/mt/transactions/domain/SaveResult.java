package com.mt.transactions.domain;

/**
 * Outcome of an atomic save: a new transaction was stored, or an identical one already existed.
 */
public enum SaveResult {
    CREATED,
    REPLAYED
}
