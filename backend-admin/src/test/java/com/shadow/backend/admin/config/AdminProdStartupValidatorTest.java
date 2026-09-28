package com.shadow.backend.admin.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminProdStartupValidatorTest {

    private static final String PASSWORD_PROPERTY = "app.security.initial-admin-password";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n"})
    void afterPropertiesSet_rejectsMissingOrBlankAdminPassword(String password) {
        MockEnvironment environment = new MockEnvironment();
        if (password != null) {
            environment.setProperty(PASSWORD_PROPERTY, password);
        }
        AdminProdStartupValidator validator = new AdminProdStartupValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PASSWORD_PROPERTY)
                .hasMessageContaining("ADMIN_INITIAL_PASSWORD");
    }

    @Test
    void afterPropertiesSet_acceptsAdminPasswordWithoutRepeatingInfrastructureValidation() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(PASSWORD_PROPERTY, "test-admin-password");
        AdminProdStartupValidator validator = new AdminProdStartupValidator(environment);

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }
}
