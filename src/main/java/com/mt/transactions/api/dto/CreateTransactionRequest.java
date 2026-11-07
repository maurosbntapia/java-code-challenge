package com.mt.transactions.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body of {@code PUT /transactions/{transaction_id}}.
 * {@code parent_id} is mapped explicitly; without it Jackson would silently ignore the field.
 */
public record CreateTransactionRequest(
        @NotNull @Positive Double amount,
        @NotBlank String type,
        @JsonProperty("parent_id") Long parentId
) {

    /** Literals such as {@code 1e309} deserialize as Infinity, which {@code @Positive} accepts. */
    @JsonIgnore
    @AssertTrue(message = "amount must be a finite number")
    public boolean isAmountFinite() {
        return amount == null || Double.isFinite(amount);
    }
}
