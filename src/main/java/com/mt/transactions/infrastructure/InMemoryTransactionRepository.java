package com.mt.transactions.infrastructure;

import com.mt.transactions.domain.SaveResult;
import com.mt.transactions.domain.Transaction;
import com.mt.transactions.domain.TransactionRepository;
import com.mt.transactions.domain.TransactionType;
import com.mt.transactions.domain.exception.ParentNotFoundException;
import com.mt.transactions.domain.exception.TransactionConflictException;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory adapter. A single read/write lock guards the three structures as one unit,
 * so validation and writes in {@link #save} are atomic and reads can run in parallel.
 */
@Repository
public class InMemoryTransactionRepository implements TransactionRepository {

    private final Map<Long, Transaction> byId = new HashMap<>();
    private final Map<TransactionType, Set<Long>> idsByType = new HashMap<>();
    private final Map<Long, Set<Long>> childrenByParent = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public SaveResult save(Transaction tx) {
        lock.writeLock().lock();
        try {
            Transaction existing = byId.get(tx.id());
            if (existing != null) {
                if (existing.sameContentAs(tx)) {
                    return SaveResult.REPLAYED;
                }
                throw new TransactionConflictException(tx.id());
            }
            if (tx.parentId() != null && !byId.containsKey(tx.parentId())) {
                throw new ParentNotFoundException(tx.parentId());
            }

            byId.put(tx.id(), tx);
            idsByType.computeIfAbsent(tx.type(), k -> new LinkedHashSet<>()).add(tx.id());
            if (tx.parentId() != null) {
                childrenByParent.computeIfAbsent(tx.parentId(), k -> new LinkedHashSet<>()).add(tx.id());
            }
            return SaveResult.CREATED;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<Transaction> findById(long id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(byId.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }
}
