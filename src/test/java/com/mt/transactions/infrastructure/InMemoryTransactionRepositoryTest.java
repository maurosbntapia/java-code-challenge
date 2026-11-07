package com.mt.transactions.infrastructure;

import com.mt.transactions.domain.SaveResult;
import com.mt.transactions.domain.Transaction;
import com.mt.transactions.domain.TransactionType;
import com.mt.transactions.domain.exception.ParentNotFoundException;
import com.mt.transactions.domain.exception.TransactionConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTransactionRepositoryTest {

    private static final TransactionType CARS = new TransactionType("cars");

    private InMemoryTransactionRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryTransactionRepository();
    }

    private static Transaction tx(long id, String amount, Long parentId) {
        return new Transaction(id, new BigDecimal(amount), CARS, parentId);
    }

    @Test
    void firstSaveIsCreatedAndCanBeFound() {
        Transaction tx = tx(1L, "5000", null);

        assertThat(repo.save(tx)).isEqualTo(SaveResult.CREATED);
        assertThat(repo.findById(1L)).contains(tx);
    }

    @Test
    void unknownIdIsNotFound() {
        assertThat(repo.findById(999L)).isEmpty();
    }

    @Test
    void identicalRetryIsReplayed() {
        repo.save(tx(1L, "5000", null));

        assertThat(repo.save(tx(1L, "5000", null))).isEqualTo(SaveResult.REPLAYED);
    }

    @Test
    void retryWithDifferentScaleIsReplayed() {
        repo.save(tx(1L, "5000", null));

        assertThat(repo.save(tx(1L, "5000.0", null))).isEqualTo(SaveResult.REPLAYED);
    }

    @Test
    void sameIdWithDifferentContentIsConflict() {
        Transaction original = tx(1L, "5000", null);
        repo.save(original);

        assertThatThrownBy(() -> repo.save(tx(1L, "9999", null)))
                .isInstanceOf(TransactionConflictException.class);
        assertThat(repo.findById(1L)).contains(original);
    }

    @Test
    void childWithExistingParentIsCreated() {
        repo.save(tx(10L, "5000", null));

        assertThat(repo.save(tx(11L, "10000", 10L))).isEqualTo(SaveResult.CREATED);
    }

    @Test
    void missingParentIsRejectedAndNothingIsStored() {
        assertThatThrownBy(() -> repo.save(tx(2L, "5000", 999L)))
                .isInstanceOf(ParentNotFoundException.class);
        assertThat(repo.findById(2L)).isEmpty();
    }

    @Test
    void selfParentIsRejectedBecauseParentDoesNotExistYet() {
        assertThatThrownBy(() -> repo.save(tx(1L, "5000", 1L)))
                .isInstanceOf(ParentNotFoundException.class);
    }
}
