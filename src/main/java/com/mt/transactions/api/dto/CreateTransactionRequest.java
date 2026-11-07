package com.mt.transactions.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body of {@code PUT /transactions/{transaction_id}}.
 * {@code parent_id} is mapped explicitly; without it Jackson would silently ignore the field.
 */
public record CreateTransactionRequest(
        Double amount,
        String type,
        @JsonProperty("parent_id") Long parentId
) {
}
