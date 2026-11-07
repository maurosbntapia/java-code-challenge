package com.mt.transactions.infrastructure;

import com.mt.transactions.domain.SaveResult;
import com.mt.transactions.domain.Transaction;
import com.mt.transactions.domain.TransactionType;
import com.mt.transactions.domain.exception.TransactionConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTransactionRepositoryConcurrencyTest {

    private static final int THREADS = 32;
    private static final TransactionType CARS = new TransactionType("cars");

    private InMemoryTransactionRepository repo;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        repo = new InMemoryTransactionRepository();
        executor = Executors.newFixedThreadPool(THREADS);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    /** Runs all tasks at once, released together by a start gate to maximize contention. */
    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(executor.submit(() -> {
                startGate.await();
                return task.call();
            }));
        }
        startGate.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        return results;
    }

    private static Transaction tx(long id, long amount, Long parentId) {
        return new Transaction(id, BigDecimal.valueOf(amount), CARS, parentId);
    }

    @RepeatedTest(20)
    void sameIdWithDifferentContentCreatesExactlyOne() throws Exception {
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            long amount = 1000 + i;
            tasks.add(() -> {
                try {
                    return repo.save(tx(1L, amount, null)).name();
                } catch (TransactionConflictException e) {
                    return "CONFLICT";
                }
            });
        }

        List<String> results = runConcurrently(tasks);

        assertThat(results).filteredOn("CREATED"::equals).hasSize(1);
        assertThat(results).filteredOn("CONFLICT"::equals).hasSize(THREADS - 1);
        assertThat(repo.findIdsByType(CARS)).containsExactly(1L);
    }

    @RepeatedTest(20)
    void identicalConcurrentRetriesCreateOnceAndReplayTheRest() throws Exception {
        List<Callable<SaveResult>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> repo.save(tx(1L, 5000, null)));
        }

        List<SaveResult> results = runConcurrently(tasks);

        assertThat(results).filteredOn(SaveResult.CREATED::equals).hasSize(1);
        assertThat(results).filteredOn(SaveResult.REPLAYED::equals).hasSize(THREADS - 1);
        assertThat(repo.findIdsByType(CARS)).containsExactly(1L);
    }

    @Test
    void concurrentChildrenOfSameParentAreAllCounted() throws Exception {
        repo.save(tx(0L, 1, null));
        int children = 2_000;
        List<Callable<SaveResult>> tasks = new ArrayList<>();
        for (long id = 1; id <= children; id++) {
            long childId = id;
            tasks.add(() -> repo.save(tx(childId, 1, 0L)));
        }

        assertThat(runConcurrently(tasks)).containsOnly(SaveResult.CREATED);

        assertThat(repo.sumWithDescendants(0L)).isEqualByComparingTo(BigDecimal.valueOf(children + 1));
        assertThat(repo.findIdsByType(CARS))
                .hasSize(children + 1)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(0, children).boxed().toList());
    }

    @Test
    void readsRunSafelyWhileWritesHappen() throws Exception {
        repo.save(tx(0L, 1, null));
        int writes = 1_000;
        List<Callable<BigDecimal>> tasks = new ArrayList<>();
        for (long id = 1; id <= writes; id++) {
            long childId = id;
            tasks.add(() -> {
                repo.save(tx(childId, 1, 0L));
                return null;
            });
            tasks.add(() -> repo.sumWithDescendants(0L));
        }

        List<BigDecimal> results = runConcurrently(tasks);

        assertThat(results).filteredOn(r -> r != null)
                .allSatisfy(sum -> assertThat(sum).isBetween(BigDecimal.ONE, BigDecimal.valueOf(writes + 1)));
        assertThat(repo.sumWithDescendants(0L)).isEqualByComparingTo(BigDecimal.valueOf(writes + 1));
    }

    @Test
    void deepChainDoesNotOverflowTheStack() throws ExecutionException, InterruptedException {
        int depth = 10_000;
        repo.save(tx(1L, 1, null));
        for (long id = 2; id <= depth; id++) {
            repo.save(tx(id, 1, id - 1));
        }

        assertThat(repo.sumWithDescendants(1L)).isEqualByComparingTo(BigDecimal.valueOf(depth));
        assertThat(repo.sumWithDescendants(depth / 2)).isEqualByComparingTo(BigDecimal.valueOf(depth / 2 + 1));
    }
}
