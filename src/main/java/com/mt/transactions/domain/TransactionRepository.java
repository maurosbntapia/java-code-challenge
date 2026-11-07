package com.mt.transactions.domain;

import com.mt.transactions.domain.exception.ParentNotFoundException;
import com.mt.transactions.domain.exception.TransactionConflictException;
import com.mt.transactions.domain.exception.TransactionNotFoundException;

import java.math.BigDecimal;
import java.util.List;
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

    /** Ids of all transactions of the given type, in creation order. Empty if none. */
    List<Long> findIdsByType(TransactionType type);

    /**
     * Amount of the transaction plus the amounts of all its descendants (children, grandchildren, ...).
     *
     * @throws TransactionNotFoundException if the transaction does not exist
     */
    BigDecimal sumWithDescendants(long id);
}
