package com.mt.transactions.application;

import com.mt.transactions.domain.SaveResult;
import com.mt.transactions.domain.Transaction;
import com.mt.transactions.domain.TransactionRepository;
import com.mt.transactions.domain.TransactionType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Transaction use cases. Orchestrates the domain and the repository port;
 * integrity invariants are enforced atomically by the repository.
 */
@Service
public class TransactionService {

    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    public SaveResult createTransaction(long id, BigDecimal amount, String type, Long parentId) {
        return repository.save(new Transaction(id, amount, new TransactionType(type), parentId));
    }

    public List<Long> getIdsByType(String type) {
        return repository.findIdsByType(new TransactionType(type));
    }
}
