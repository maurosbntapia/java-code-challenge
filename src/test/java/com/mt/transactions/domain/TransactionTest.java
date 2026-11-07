package com.mt.transactions.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private static final TransactionType CARS = new TransactionType("cars");
    private static final TransactionType SHOPPING = new TransactionType("shopping");

    private static Transaction tx(String amount, TransactionType type, Long parentId) {
        return new Transaction(1L, new BigDecimal(amount), type, parentId);
    }

    @Test
    void identicalContentIsSameContent() {
        assertThat(tx("5000", CARS, null).sameContentAs(tx("5000", CARS, null))).isTrue();
    }

    @Test
    void amountScaleIsIgnoredWhenComparingContent() {
        assertThat(tx("5000", CARS, 10L).sameContentAs(tx("5000.0", CARS, 10L))).isTrue();
    }

    @Test
    void differentAmountIsDifferentContent() {
        assertThat(tx("5000", CARS, null).sameContentAs(tx("6000", CARS, null))).isFalse();
    }

    @Test
    void differentTypeIsDifferentContent() {
        assertThat(tx("5000", CARS, null).sameContentAs(tx("5000", SHOPPING, null))).isFalse();
    }

    @Test
    void differentParentIsDifferentContent() {
        assertThat(tx("5000", CARS, null).sameContentAs(tx("5000", CARS, 2L))).isFalse();
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> tx("0", CARS, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> tx("-1", CARS, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new Transaction(1L, null, CARS, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullType() {
        assertThatThrownBy(() -> new Transaction(1L, BigDecimal.TEN, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
