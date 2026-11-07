package com.mt.transactions.infrastructure;

import com.mt.transactions.domain.SaveResult;
import com.mt.transactions.domain.Transaction;
import com.mt.transactions.domain.TransactionType;
import com.mt.transactions.domain.exception.ParentNotFoundException;
import com.mt.transactions.domain.exception.TransactionConflictException;
import com.mt.transactions.domain.exception.TransactionNotFoundException;
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

    @Test
    void clearRemovesEverything() {
        repo.save(tx(10L, "5000", null));
        repo.save(tx(11L, "5000", 10L));

        repo.clear();

        assertThat(repo.findById(10L)).isEmpty();
        assertThat(repo.findById(11L)).isEmpty();
        assertThat(repo.save(tx(10L, "1", null))).isEqualTo(SaveResult.CREATED);
    }

    @Test
    void findIdsByTypeReturnsIdsInInsertionOrder() {
        repo.save(tx(11L, "100", null));
        repo.save(tx(10L, "200", null));
        repo.save(new Transaction(12L, BigDecimal.ONE, new TransactionType("shopping"), null));

        assertThat(repo.findIdsByType(CARS)).containsExactly(11L, 10L);
    }

    @Test
    void findIdsByTypeReturnsEmptyForUnknownType() {
        assertThat(repo.findIdsByType(new TransactionType("unknown"))).isEmpty();
    }

    @Test
    void findIdsByTypeReturnsSnapshotNotLiveView() {
        repo.save(tx(10L, "100", null));
        var ids = repo.findIdsByType(CARS);

        repo.save(tx(11L, "100", null));

        assertThat(ids).containsExactly(10L);
    }

    @Test
    void sumIncludesSelfAndAllDescendants() {
        repo.save(tx(10L, "5000", null));
        repo.save(tx(11L, "10000", 10L));
        repo.save(tx(12L, "5000", 11L));

        assertThat(repo.sumWithDescendants(10L)).isEqualByComparingTo("20000");
        assertThat(repo.sumWithDescendants(11L)).isEqualByComparingTo("15000");
        assertThat(repo.sumWithDescendants(12L)).isEqualByComparingTo("5000");
    }

    @Test
    void sumCoversEveryBranchButNotAncestorsOrSiblings() {
        repo.save(tx(1L, "1", null));
        repo.save(tx(2L, "10", 1L));
        repo.save(tx(3L, "100", 1L));
        repo.save(tx(4L, "1000", 2L));
        repo.save(tx(5L, "10000", 3L));

        assertThat(repo.sumWithDescendants(1L)).isEqualByComparingTo("11111");
        assertThat(repo.sumWithDescendants(2L)).isEqualByComparingTo("1010");
    }

    @Test
    void sumIsExactWithDecimalAmounts() {
        repo.save(tx(1L, "0.1", null));
        repo.save(tx(2L, "0.2", 1L));

        assertThat(repo.sumWithDescendants(1L)).isEqualByComparingTo("0.3");
    }

    @Test
    void sumOfUnknownIdThrowsNotFound() {
        assertThatThrownBy(() -> repo.sumWithDescendants(999L))
                .isInstanceOf(TransactionNotFoundException.class);
    }
}
