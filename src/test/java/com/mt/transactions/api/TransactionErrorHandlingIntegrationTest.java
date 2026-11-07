package com.mt.transactions.api;

import com.mt.transactions.infrastructure.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Error responses follow RFC 9457 Problem Details.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryTransactionRepository repository;

    @BeforeEach
    void resetState() {
        repository.clear();
    }

    private ResultActions putTransaction(String id, String json) throws Exception {
        return mockMvc.perform(put("/transactions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static void expectProblem(ResultActions result, int status, String instance) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(instance));
    }

    @Test
    void sameIdWithDifferentContentIsConflict() throws Exception {
        putTransaction("10", """
                { "amount": 5000, "type": "cars" }""").andExpect(status().isCreated());

        ResultActions result = putTransaction("10", """
                { "amount": 6000, "type": "cars" }""");

        expectProblem(result, 409, "/transactions/10");
        result.andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Transaction 10 already exists with different content"));
    }

    @Test
    void missingParentIsUnprocessable() throws Exception {
        ResultActions result = putTransaction("11", """
                { "amount": 5000, "type": "cars", "parent_id": 999 }""");

        expectProblem(result, 422, "/transactions/11");
        result.andExpect(jsonPath("$.detail").value(containsString("999")));
    }

    @Test
    void selfParentIsUnprocessable() throws Exception {
        expectProblem(putTransaction("10", """
                { "amount": 5000, "type": "cars", "parent_id": 10 }"""), 422, "/transactions/10");
    }

    @Test
    void rejectedTransactionIsNotStored() throws Exception {
        putTransaction("11", """
                { "amount": 5000, "type": "cars", "parent_id": 999 }""").andExpect(status().is(422));

        mockMvc.perform(get("/transactions/types/cars"))
                .andExpect(content().json("[]", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{ \"type\": \"cars\" }",
            "{ \"amount\": null, \"type\": \"cars\" }",
            "{ \"amount\": 0, \"type\": \"cars\" }",
            "{ \"amount\": -5, \"type\": \"cars\" }",
            "{ \"amount\": 1e309, \"type\": \"cars\" }"
    })
    void invalidAmountIsBadRequest(String body) throws Exception {
        expectProblem(putTransaction("10", body), 400, "/transactions/10");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{ \"amount\": 5000 }",
            "{ \"amount\": 5000, \"type\": \"\" }",
            "{ \"amount\": 5000, \"type\": \"   \" }"
    })
    void invalidTypeIsBadRequest(String body) throws Exception {
        expectProblem(putTransaction("10", body), 400, "/transactions/10");
    }

    @Test
    void validationErrorNamesTheField() throws Exception {
        putTransaction("10", """
                { "amount": 0, "type": "cars" }""")
                .andExpect(jsonPath("$.detail").value(containsString("amount")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{ \"amount\": 5000, ", "", "{ \"amount\": \"abc\", \"type\": \"cars\" }"})
    void malformedBodyIsBadRequest(String body) throws Exception {
        expectProblem(putTransaction("10", body), 400, "/transactions/10");
    }

    @Test
    void nonNumericTransactionIdIsBadRequest() throws Exception {
        expectProblem(putTransaction("abc", """
                { "amount": 5000, "type": "cars" }"""), 400, "/transactions/abc");
    }

    @Test
    void sumOfUnknownTransactionIsNotFound() throws Exception {
        ResultActions result = mockMvc.perform(get("/transactions/sum/999"));

        expectProblem(result, 404, "/transactions/sum/999");
        result.andExpect(jsonPath("$.detail").value("Transaction 999 not found"));
    }

    @Test
    void concurrentPutsWithSameIdAndDifferentContentCreateOnlyOne() throws Exception {
        int threads = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String body = "{ \"amount\": " + (1000 + i) + ", \"type\": \"cars\" }";
                statuses.add(executor.submit(() -> {
                    startGate.await();
                    return putTransaction("10", body).andReturn().getResponse().getStatus();
                }));
            }
            startGate.countDown();

            List<Integer> results = new ArrayList<>();
            for (Future<Integer> status : statuses) {
                results.add(status.get(10, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(s -> s == 201).hasSize(1);
            assertThat(results).filteredOn(s -> s == 409).hasSize(threads - 1);
        } finally {
            executor.shutdownNow();
        }
    }
}
