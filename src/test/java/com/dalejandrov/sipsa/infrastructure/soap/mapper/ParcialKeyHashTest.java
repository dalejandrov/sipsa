package com.dalejandrov.sipsa.infrastructure.soap.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link ParcialKeyHash} (TECH-011, ADR-001 Option A; TECH-104 — {@code v2}
 * payload, {@code enmaFecha} is a {@link LocalDate}, not an {@code Instant}).
 * <p>
 * The hash IS the deduplication identity: these tests pin determinism, sensitivity to
 * every key component, normalization, and the rejection of incomplete keys.
 */
@DisplayName("ParcialKeyHash — deterministic natural-key hash")
class ParcialKeyHashTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 7, 15);

    @Test
    @DisplayName("Same business inputs always produce the same hash")
    void deterministic() {
        String a = ParcialKeyHash.compute("05001", 1L, 2L, 101L, FECHA);
        String b = ParcialKeyHash.compute("05001", 1L, 2L, 101L, FECHA);

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("Output is 64-char lowercase hex (fits key_hash VARCHAR(100), distinct from UUID-36)")
    void hexFormat() {
        String hash = ParcialKeyHash.compute("05001", 1L, 2L, 101L, FECHA);

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("Changing any single key component changes the hash")
    void sensitivityPerField() {
        String base = ParcialKeyHash.compute("05001", 1L, 2L, 101L, FECHA);

        assertThat(ParcialKeyHash.compute("05002", 1L, 2L, 101L, FECHA)).isNotEqualTo(base);
        assertThat(ParcialKeyHash.compute("05001", 9L, 2L, 101L, FECHA)).isNotEqualTo(base);
        assertThat(ParcialKeyHash.compute("05001", 1L, 9L, 101L, FECHA)).isNotEqualTo(base);
        assertThat(ParcialKeyHash.compute("05001", 1L, 2L, 999L, FECHA)).isNotEqualTo(base);
        assertThat(ParcialKeyHash.compute("05001", 1L, 2L, 101L, FECHA.plusDays(1)))
                .isNotEqualTo(base);
    }

    @Test
    @DisplayName("Adjacent numeric fields cannot collide by concatenation (delimited payload)")
    void noConcatenationAmbiguity() {
        // Without delimiters "1"+"23" and "12"+"3" would collide.
        String a = ParcialKeyHash.compute("1", 23L, 2L, 101L, FECHA);
        String b = ParcialKeyHash.compute("12", 3L, 2L, 101L, FECHA);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("muniId is trimmed but otherwise preserved (leading zeros matter)")
    void muniIdNormalization() {
        String trimmed = ParcialKeyHash.compute("05001", 1L, 2L, 101L, FECHA);

        assertThat(ParcialKeyHash.compute("  05001  ", 1L, 2L, 101L, FECHA)).isEqualTo(trimmed);
        assertThat(ParcialKeyHash.compute("5001", 1L, 2L, 101L, FECHA)).isNotEqualTo(trimmed);
    }

    @Test
    @DisplayName("Null or blank key components are rejected, never silently defaulted")
    void rejectsIncompleteKeys() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ParcialKeyHash.compute(null, 1L, 2L, 101L, FECHA));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ParcialKeyHash.compute("   ", 1L, 2L, 101L, FECHA));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ParcialKeyHash.compute("05001", null, 2L, 101L, FECHA));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ParcialKeyHash.compute("05001", 1L, null, 101L, FECHA));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ParcialKeyHash.compute("05001", 1L, 2L, null, FECHA));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ParcialKeyHash.compute("05001", 1L, 2L, 101L, null));
    }

    @Test
    @DisplayName("muniId containing the reserved separator is rejected")
    void rejectsReservedSeparator() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ParcialKeyHash.compute("05001", 1L, 2L, 101L, FECHA));
    }
}
