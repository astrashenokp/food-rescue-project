package com.example.foodrescue.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    @Test
    void masksEmail() {
        assertThat(SensitiveDataMasker.maskEmail("volunteer@example.com"))
                .isEqualTo("v***@example.com");
    }

    @Test
    void masksConfirmationCode() {
        assertThat(SensitiveDataMasker.maskCode("482910"))
                .isEqualTo("*****0");
    }
}
