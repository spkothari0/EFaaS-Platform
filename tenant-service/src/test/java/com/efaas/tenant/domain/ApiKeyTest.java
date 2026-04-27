package com.efaas.tenant.domain;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyTest {

    @Test
    void isValid_returnsTrueForActiveKeyWithNoExpiry() {
        ApiKey key = ApiKey.builder().active(true).build();
        assertThat(key.isValid()).isTrue();
    }

    @Test
    void isValid_returnsFalseForInactiveKey() {
        ApiKey key = ApiKey.builder().active(false).build();
        assertThat(key.isValid()).isFalse();
    }

    @Test
    void isValid_returnsFalseForExpiredKey() {
        ApiKey key = ApiKey.builder()
                .active(true)
                .expiresAt(ZonedDateTime.now().minusDays(1))
                .build();
        assertThat(key.isValid()).isFalse();
    }

    @Test
    void isValid_returnsTrueForKeyWithFutureExpiry() {
        ApiKey key = ApiKey.builder()
                .active(true)
                .expiresAt(ZonedDateTime.now().plusDays(30))
                .build();
        assertThat(key.isValid()).isTrue();
    }

    @Test
    void maskKey_returnsCorrectFormat() {
        String masked = ApiKey.maskKey("efaas_live_abcdefghijklmnopqrst12345");
        assertThat(masked).startsWith("efaas_li");
        assertThat(masked).contains("***");
        assertThat(masked).endsWith("12345");
    }

    @Test
    void maskKey_returnsAsterisksForShortKey() {
        assertThat(ApiKey.maskKey("short")).isEqualTo("***");
    }

    @Test
    void maskKey_returnsAsterisksForNull() {
        assertThat(ApiKey.maskKey(null)).isEqualTo("***");
    }
}
