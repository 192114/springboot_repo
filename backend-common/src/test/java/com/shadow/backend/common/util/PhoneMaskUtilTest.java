package com.shadow.backend.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneMaskUtilTest {

    @ParameterizedTest
    @CsvSource({"13800138000,138****8000", "123,****", "123456,****"})
    void mask_doesNotExposeFullPhone(String phone, String expected) {
        assertThat(PhoneMaskUtil.mask(phone)).isEqualTo(expected);
    }

    @Test
    void mask_acceptsAbsentPhone() {
        assertThat(PhoneMaskUtil.mask(null)).isNull();
        assertThat(PhoneMaskUtil.mask("")).isEmpty();
    }
}
