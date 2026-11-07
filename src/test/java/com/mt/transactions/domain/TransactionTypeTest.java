package com.mt.transactions.domain;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTypeTest {

    @Test
    void normalizesToLowerCase() {
        assertThat(new TransactionType("CARS").value()).isEqualTo("cars");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(new TransactionType("  Cars  ").value()).isEqualTo("cars");
    }

    @Test
    void differentCasingsAreTheSameType() {
        assertThat(new TransactionType("CARS")).isEqualTo(new TransactionType(" cars "));
    }

    @Test
    void normalizationDoesNotDependOnDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(new TransactionType("SHIPPING").value()).isEqualTo("shipping");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new TransactionType(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> new TransactionType(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new TransactionType("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
