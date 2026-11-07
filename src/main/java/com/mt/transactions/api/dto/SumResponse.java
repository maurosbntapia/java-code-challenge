package com.mt.transactions.api.dto;

import java.math.BigDecimal;

/**
 * Body of {@code GET /transactions/sum/{transaction_id}}. The sum is exposed as an exact decimal
 * without trailing zeros, so 20000 renders as {@code 20000} and 0.1 + 0.2 as {@code 0.3}.
 */
public record SumResponse(BigDecimal sum) {

    public static SumResponse of(BigDecimal sum) {
        return new SumResponse(sum.stripTrailingZeros());
    }
}
