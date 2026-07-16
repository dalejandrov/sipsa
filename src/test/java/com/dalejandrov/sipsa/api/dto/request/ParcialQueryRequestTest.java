package com.dalejandrov.sipsa.api.dto.request;

import com.dalejandrov.sipsa.domain.exception.SipsaValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for the TECH-113 filter contract of {@link ParcialQueryRequest}:
 * {@code muniId} as preserved text (DIVIPOLA leading zeros) and the
 * {@code idArtiSemana}/{@code artiId} canonical-plus-alias resolution.
 */
@DisplayName("ParcialQueryRequest — municipality and article filter contract")
class ParcialQueryRequestTest {

    private static ParcialQueryRequest request(String muniId, Long idArtiSemana, Long artiId) {
        return new ParcialQueryRequest(null, null, null, muniId, null,
                idArtiSemana, artiId, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("Without filters: no validation error, both resolved filters are null")
    void noFilters() {
        ParcialQueryRequest req = request(null, null, null);

        assertThat(req.validatedMuniId()).isNull();
        assertThat(req.effectiveArticleId()).isNull();
    }

    @Test
    @DisplayName("muniId '05001' is preserved as text — never converted to 5001")
    void muniIdKeepsLeadingZeros() {
        ParcialQueryRequest req = request("05001", null, null);

        assertThat(req.validatedMuniId())
                .isEqualTo("05001")
                .isNotEqualTo("5001");
        assertThat(req.muniId()).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("muniId without leading zero stays distinct from the zero-padded code")
    void muniIdWithoutLeadingZero() {
        assertThat(request("5001", null, null).validatedMuniId()).isEqualTo("5001");
    }

    @Test
    @DisplayName("muniId is trimmed but otherwise untouched")
    void muniIdIsTrimmed() {
        assertThat(request("  05001  ", null, null).validatedMuniId()).isEqualTo("05001");
    }

    @Test
    @DisplayName("Blank muniId is a client error (400), not an ignored filter")
    void blankMuniIdRejected() {
        assertThatExceptionOfType(SipsaValidationException.class)
                .isThrownBy(() -> request("   ", null, null).validatedMuniId());
    }

    @Test
    @DisplayName("muniId longer than the column bound (50) is rejected")
    void overlongMuniIdRejected() {
        assertThatExceptionOfType(SipsaValidationException.class)
                .isThrownBy(() -> request("x".repeat(51), null, null).validatedMuniId());
    }

    @Test
    @DisplayName("Canonical idArtiSemana alone resolves as-is")
    void canonicalArticleParameter() {
        assertThat(request(null, 101L, null).effectiveArticleId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("Alias artiId alone translates to the idArtiSemana filter")
    void aliasArticleParameter() {
        assertThat(request(null, null, 101L).effectiveArticleId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("Both article parameters with the same value collapse to one condition")
    void bothArticleParametersEqual() {
        assertThat(request(null, 101L, 101L).effectiveArticleId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("Contradictory article parameters are a client error (400)")
    void contradictoryArticleParametersRejected() {
        assertThatExceptionOfType(SipsaValidationException.class)
                .isThrownBy(() -> request(null, 101L, 102L).effectiveArticleId());
    }

    @Test
    @DisplayName("Municipality and article filters combine independently")
    void combinedFilters() {
        ParcialQueryRequest req = request("05001", 101L, null);

        assertThat(req.validatedMuniId()).isEqualTo("05001");
        assertThat(req.effectiveArticleId()).isEqualTo(101L);
    }
}
