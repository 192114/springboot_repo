package com.shadow.backend.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdStartupValidatorTest {

    private static final Map<String, String> REQUIRED_PROPERTIES = Map.of(
            "spring.datasource.password", "test-db-password",
            "spring.data.redis.password", "test-redis-password",
            "app.cors.allowed-origins", "https://app.example.test"
    );

    @Test
    void afterPropertiesSet_acceptsOnlyInfrastructureSettingsWithoutAdminPassword() {
        MockEnvironment environment = new MockEnvironment();
        REQUIRED_PROPERTIES.forEach(environment::setProperty);
        ProdStartupValidator validator = new ProdStartupValidator(environment);

        assertThat(environment.getProperty("app.security.initial-admin-password")).isNull();
        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();

        environment.setProperty("app.security.initial-admin-password", " \t\n");
        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "spring.datasource.password, DB_PASSWORD",
            "spring.data.redis.password, REDIS_PASSWORD",
            "app.cors.allowed-origins, CORS_ALLOWED_ORIGINS"
    })
    void afterPropertiesSet_rejectsEachMissingOrBlankInfrastructureSetting(
            String property, String environmentVariable) {
        MockEnvironment environment = new MockEnvironment();
        REQUIRED_PROPERTIES.forEach((key, value) -> {
            if (!key.equals(property)) {
                environment.setProperty(key, value);
            }
        });
        ProdStartupValidator validator = new ProdStartupValidator(environment);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(property)
                .hasMessageContaining(environmentVariable);

        for (String blank : new String[]{"", " ", "\t\r\n"}) {
            environment.setProperty(property, blank);
            assertThatThrownBy(validator::afterPropertiesSet)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(property)
                    .hasMessageContaining(environmentVariable);
        }

        environment.setProperty(property, REQUIRED_PROPERTIES.get(property));
        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }
}
