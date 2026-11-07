package com.mt.transactions.domain;

import com.mt.transactions.domain.exception.ParentNotFoundException;
import com.mt.transactions.domain.exception.TransactionConflictException;

import java.util.Optional;

/**
 * Storage port for transactions. Implementations must enforce the integrity
 * invariants atomically with the write.
 */
public interface TransactionRepository {

    /**
     * Stores the transaction atomically.
     *
     * @return {@link SaveResult#CREATED} if it is new, {@link SaveResult#REPLAYED} if an
     *         identical transaction already exists
     * @throws TransactionConflictException if the id exists with different content
     * @throws ParentNotFoundException      if the parent transaction does not exist
     */
    SaveResult save(Transaction transaction);

    Optional<Transaction> findById(long id);
}
