package com.shadow.backend.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordUtilTest {

    private final PasswordUtil passwordUtil = new PasswordUtil();

    @Test
    void hash_producesArgon2EncodedString() {
        String hashed = passwordUtil.hash("password1");

        assertThat(hashed).startsWith("$argon2");
        assertThat(hashed).isNotEqualTo("password1");
    }

    @Test
    void verify_whenPasswordMatches_returnsTrue() {
        String hashed = passwordUtil.hash("password1");

        assertThat(passwordUtil.verify("password1", hashed)).isTrue();
    }

    @Test
    void verify_whenPasswordDoesNotMatch_returnsFalse() {
        String hashed = passwordUtil.hash("password1");

        assertThat(passwordUtil.verify("wrong-password", hashed)).isFalse();
    }
}
