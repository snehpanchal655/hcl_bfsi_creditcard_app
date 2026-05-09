package com.creditcard.notification.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DataMaskingUtil}.
 *
 * <p>No Spring context required — pure unit tests, instantiating the class directly.
 */
@DisplayName("DataMaskingUtil")
class DataMaskingUtilTest {

    private final DataMaskingUtil maskingUtil = new DataMaskingUtil();

    // -------------------------------------------------------------------------
    // maskApplicationId
    // -------------------------------------------------------------------------

    @DisplayName("maskApplicationId — standard format")
    @ParameterizedTest(name = "''{0}'' → ''{1}''")
    @CsvSource({
            "APP-2024-00042, APP-XXXX-0042",
            "APP-ABCD-5678,  APP-XXXX-5678",
            "CARD-XYZ-9900,  CARD-XXXX-9900",
    })
    void shouldMaskStandardApplicationId(String raw, String expected) {
        assertThat(maskingUtil.maskApplicationId(raw.trim()))
                .isEqualTo(expected.trim());
    }

    @Test
    @DisplayName("maskApplicationId — short id keeps last 4 chars")
    void shouldMaskShortApplicationId() {
        assertThat(maskingUtil.maskApplicationId("ABCDE12345"))
                .isEqualTo("APP-XXXX-2345");
    }

    @DisplayName("maskApplicationId — null / blank returns safe fallback")
    @ParameterizedTest
    @NullAndEmptySource
    void shouldHandleNullOrBlankApplicationId(String raw) {
        assertThat(maskingUtil.maskApplicationId(raw))
                .isEqualTo("APP-XXXX-****");
    }

    @Test
    @DisplayName("maskApplicationId — result must never contain original middle segment")
    void maskedIdShouldNotContainOriginalMiddleSegment() {
        String masked = maskingUtil.maskApplicationId("APP-2024-00042");
        assertThat(masked).doesNotContain("2024");
    }

    // -------------------------------------------------------------------------
    // maskEmail
    // -------------------------------------------------------------------------

    @DisplayName("maskEmail — standard emails")
    @ParameterizedTest(name = "''{0}'' → starts with first char of local part")
    @CsvSource({
            "john.doe@example.com,  j",
            "alice@company.org,     a",
            "bob.smith@mail.co.uk,  b",
    })
    void maskedEmailShouldStartWithFirstLocalChar(String email, String expectedFirstChar) {
        String masked = maskingUtil.maskEmail(email.trim());
        assertThat(masked).startsWith(expectedFirstChar.trim());
    }

    @Test
    @DisplayName("maskEmail — should contain @ separator")
    void maskedEmailShouldContainAtSign() {
        assertThat(maskingUtil.maskEmail("john@example.com"))
                .contains("@");
    }

    @Test
    @DisplayName("maskEmail — should preserve TLD (.com, .org)")
    void maskedEmailShouldPreserveTld() {
        assertThat(maskingUtil.maskEmail("john@example.com"))
                .endsWith(".com");
    }

    @Test
    @DisplayName("maskEmail — should not contain full local part")
    void maskedEmailShouldNotRevealFullLocalPart() {
        String masked = maskingUtil.maskEmail("johnathan@example.com");
        assertThat(masked).doesNotContain("johnathan");
    }

    @DisplayName("maskEmail — null / blank returns safe fallback")
    @ParameterizedTest
    @NullAndEmptySource
    void shouldHandleNullOrBlankEmail(String email) {
        assertThat(maskingUtil.maskEmail(email))
                .isEqualTo("****@****.***");
    }

    @Test
    @DisplayName("maskEmail — malformed email (no @) returns fallback")
    void shouldHandleMalformedEmail() {
        assertThat(maskingUtil.maskEmail("notanemail"))
                .isEqualTo("****@****.***");
    }
}
