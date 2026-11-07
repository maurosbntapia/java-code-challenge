package com.mt.transactions.api;

import com.mt.transactions.domain.Transaction;
import com.mt.transactions.infrastructure.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sends raw JSON identical to the contract instead of serializing DTOs,
 * so mapping mistakes (e.g. parent_id) are caught.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryTransactionRepository repository;

    @BeforeEach
    void resetState() {
        repository.clear();
    }

    private ResultActions putTransaction(long id, String json) throws Exception {
        return mockMvc.perform(put("/transactions/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void newTransactionIsCreated() throws Exception {
        putTransaction(10, """
                { "amount": 5000, "type": "cars" }""")
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"status":"ok"}""", true));
    }

    @Test
    void identicalRetryReturnsOk() throws Exception {
        String body = """
                { "amount": 5000, "type": "cars" }""";
        putTransaction(10, body).andExpect(status().isCreated());

        putTransaction(10, body)
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"status":"ok"}""", true));
    }

    @Test
    void retryWithDifferentNumericScaleIsSameContent() throws Exception {
        putTransaction(10, """
                { "amount": 5000, "type": "cars" }""").andExpect(status().isCreated());

        putTransaction(10, """
                { "amount": 5000.0, "type": "cars" }""").andExpect(status().isOk());
    }

    @Test
    void snakeCaseParentIdLinksToParent() throws Exception {
        putTransaction(10, """
                { "amount": 5000, "type": "cars" }""").andExpect(status().isCreated());

        putTransaction(11, """
                { "amount": 10000, "type": "shopping", "parent_id": 10 }""")
                .andExpect(status().isCreated());

        assertThat(repository.findById(11L)).map(Transaction::parentId).contains(10L);
    }

    @Test
    void typeIsNormalizedWhenStored() throws Exception {
        putTransaction(10, """
                { "amount": 5000, "type": " CARS " }""").andExpect(status().isCreated());

        assertThat(repository.findById(10L)).map(tx -> tx.type().value()).contains("cars");
    }

    @Test
    void unknownFieldsAreIgnored() throws Exception {
        putTransaction(10, """
                { "amount": 5000, "type": "cars", "currency": "ARS" }""")
                .andExpect(status().isCreated());
    }

    @Test
    void listsIdsOfTypeInCreationOrder() throws Exception {
        putTransaction(10, """
                { "amount": 5000, "type": "cars" }""").andExpect(status().isCreated());
        putTransaction(11, """
                { "amount": 10000, "type": "shopping", "parent_id": 10 }""").andExpect(status().isCreated());
        putTransaction(12, """
                { "amount": 3000, "type": "cars" }""").andExpect(status().isCreated());

        mockMvc.perform(get("/transactions/types/cars"))
                .andExpect(status().isOk())
                .andExpect(content().json("[10, 12]", true));
    }

    @Test
    void typeIsNormalizedWhenQueried() throws Exception {
        putTransaction(10, """
                { "amount": 5000, "type": "cars" }""").andExpect(status().isCreated());
        putTransaction(11, """
                { "amount": 5000, "type": "CARS" }""").andExpect(status().isCreated());

        mockMvc.perform(get("/transactions/types/CARS"))
                .andExpect(status().isOk())
                .andExpect(content().json("[10, 11]", true));
    }

    @Test
    void typeWithoutTransactionsReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/transactions/types/unknown"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));
    }

    @Test
    void identicalRetryDoesNotDuplicateIdInType() throws Exception {
        String body = """
                { "amount": 5000, "type": "cars" }""";
        putTransaction(10, body).andExpect(status().isCreated());
        putTransaction(10, body).andExpect(status().isOk());

        mockMvc.perform(get("/transactions/types/cars"))
                .andExpect(content().json("[10]", true));
    }

    @Test
    void acceptanceExample() throws Exception {
        putTransaction(10, """
                { "amount": 5000, "type": "cars" }""")
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"status":"ok"}""", true));
        putTransaction(11, """
                { "amount": 10000, "type": "shopping", "parent_id": 10 }""")
                .andExpect(status().isCreated());
        putTransaction(12, """
                { "amount": 5000, "type": "shopping", "parent_id": 11 }""")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/transactions/types/cars"))
                .andExpect(status().isOk())
                .andExpect(content().json("[10]", true));
        mockMvc.perform(get("/transactions/sum/10"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"sum\":20000}"));
        mockMvc.perform(get("/transactions/sum/11"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"sum\":15000}"));
    }

    @Test
    void sumOfLeafIsItsOwnAmount() throws Exception {
        putTransaction(10, """
                { "amount": 5000, "type": "cars" }""").andExpect(status().isCreated());

        mockMvc.perform(get("/transactions/sum/10"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"sum\":5000}"));
    }

    @Test
    void sumAvoidsFloatingPointErrors() throws Exception {
        putTransaction(1, """
                { "amount": 0.1, "type": "t" }""").andExpect(status().isCreated());
        putTransaction(2, """
                { "amount": 0.2, "type": "t", "parent_id": 1 }""").andExpect(status().isCreated());

        mockMvc.perform(get("/transactions/sum/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"sum\":0.3}"));
    }

    @Test
    void sumKeepsSignificantDecimalsAndIsNeverInScientificNotation() throws Exception {
        putTransaction(1, """
                { "amount": 1000000, "type": "t" }""").andExpect(status().isCreated());
        putTransaction(2, """
                { "amount": 0.25, "type": "t", "parent_id": 1 }""").andExpect(status().isCreated());

        mockMvc.perform(get("/transactions/sum/1"))
                .andExpect(content().string("{\"sum\":1000000.25}"));
        mockMvc.perform(get("/transactions/sum/2"))
                .andExpect(content().string("{\"sum\":0.25}"));
    }

    @Test
    void roundNumberSumIsNotInScientificNotation() throws Exception {
        putTransaction(1, """
                { "amount": 1e7, "type": "t" }""").andExpect(status().isCreated());

        mockMvc.perform(get("/transactions/sum/1"))
                .andExpect(content().string("{\"sum\":10000000}"));
    }
}
