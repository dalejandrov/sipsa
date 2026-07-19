package com.dalejandrov.sipsa.infrastructure.soap.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exactness contract of the decimal parsing used for SIPSA price fields (TECH-118).
 * <p>
 * DANE's XSD declares prices as unbounded {@code xs:decimal}; the parser must preserve
 * the textual value exactly — {@code new BigDecimal(String)} — with no {@code double}
 * intermediate (which would corrupt values like {@code 3333.33}) and no rounding of its
 * own. Scale coercion to 2 happens only at the database boundary
 * ({@code NUMERIC(19,2)}), where it is pinned by {@code ParcialDecimalPrecisionTest}.
 */
@DisplayName("XmlParsingUtil.parseDecimal — exact, double-free price parsing")
class XmlParsingUtilDecimalTest {

    @Test
    @DisplayName("plain two-decimal price parses to the exact unscaled value")
    void exactTwoDecimals() {
        assertThat(XmlParsingUtil.parseDecimal("3333.33"))
                .isEqualByComparingTo(new BigDecimal("3333.33"))
                .extracting(BigDecimal::scale).isEqualTo(2);
    }

    @Test
    @DisplayName("a double round-trip would corrupt this value; the parser must not")
    void noBinaryFloatingPointCorruption() {
        // new BigDecimal(0.1d) = 0.1000000000000000055511151231257827…; the string
        // constructor must yield exactly 0.1.
        assertThat(XmlParsingUtil.parseDecimal("0.1"))
                .isEqualTo(new BigDecimal("0.1"));
        assertThat(XmlParsingUtil.parseDecimal("22000.00").toPlainString())
                .isEqualTo("22000.00");
    }

    @Test
    @DisplayName("more than two decimals are preserved exactly at parse time — no silent rounding")
    void extraScalePreserved() {
        assertThat(XmlParsingUtil.parseDecimal("123.456"))
                .isEqualTo(new BigDecimal("123.456"));
    }

    @Test
    @DisplayName("integer, one-decimal, zero, negative and whitespace inputs parse exactly")
    void plainForms() {
        assertThat(XmlParsingUtil.parseDecimal("15500")).isEqualByComparingTo("15500");
        assertThat(XmlParsingUtil.parseDecimal("1.5")).isEqualTo(new BigDecimal("1.5"));
        assertThat(XmlParsingUtil.parseDecimal("0.00")).isEqualByComparingTo(BigDecimal.ZERO);
        // xs:decimal admits a sign; storage has no CHECK constraint (finding recorded
        // in TECH-118): the parser passes the value through exactly.
        assertThat(XmlParsingUtil.parseDecimal("-230.77")).isEqualTo(new BigDecimal("-230.77"));
        assertThat(XmlParsingUtil.parseDecimal("  999.99  ")).isEqualTo(new BigDecimal("999.99"));
    }

    @Test
    @DisplayName("nineteen-digit-precision value (fits NUMERIC(19,2), not DECIMAL(15,2)) parses exactly")
    void nineteenDigitPrecision() {
        assertThat(XmlParsingUtil.parseDecimal("99999999999999999.99"))
                .isEqualTo(new BigDecimal("99999999999999999.99"));
    }

    @Test
    @DisplayName("null and garbage return null instead of throwing")
    void nullAndGarbage() {
        assertThat(XmlParsingUtil.parseDecimal(null)).isNull();
        assertThat(XmlParsingUtil.parseDecimal("N/A")).isNull();
        assertThat(XmlParsingUtil.parseDecimal("")).isNull();
    }
}
