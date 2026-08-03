package com.dalejandrov.sipsa.infrastructure.config;

import com.dalejandrov.sipsa.domain.exception.SipsaValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TECH-157: {@link PaginationConfig}'s pure logic - {@code buildPageable}'s 1-based to
 * 0-based conversion and sort parsing, {@code validatePageable}'s two checks, and
 * {@code validateIds}. No Spring context needed - the class has no dependencies beyond
 * its own configured fields.
 */
class PaginationConfigTest {

    private final PaginationConfig config = new PaginationConfig();

    @Test
    @DisplayName("buildPageable: 1-based API page converts to 0-based Spring Data page index")
    void buildPageable_convertsOneBasedToZeroBased() {
        assertThat(config.buildPageable(1, 20, null).getPageNumber()).isZero();
        assertThat(config.buildPageable(5, 20, null).getPageNumber()).isEqualTo(4);
    }

    @Test
    @DisplayName("buildPageable: page <= 0 clamps to index 0, never negative")
    void buildPageable_nonPositivePage_clampsToZero() {
        assertThat(config.buildPageable(0, 20, null).getPageNumber()).isZero();
        assertThat(config.buildPageable(-5, 20, null).getPageNumber()).isZero();
    }

    @Test
    @DisplayName("buildPageable: size is passed through unchanged")
    void buildPageable_sizePassedThrough() {
        assertThat(config.buildPageable(1, 37, null).getPageSize()).isEqualTo(37);
    }

    @Test
    @DisplayName("buildPageable: null or blank sort -> unsorted")
    void buildPageable_noSort_unsorted() {
        assertThat(config.buildPageable(1, 20, null).getSort().isUnsorted()).isTrue();
        assertThat(config.buildPageable(1, 20, "  ").getSort().isUnsorted()).isTrue();
    }

    @Test
    @DisplayName("buildPageable: \"field\" (no direction) -> ascending")
    void buildPageable_sortNoDirection_ascending() {
        Sort sort = config.buildPageable(1, 20, "fechaCaptura").getSort();
        assertThat(sort.getOrderFor("fechaCaptura").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("buildPageable: \"field,asc\" -> ascending")
    void buildPageable_sortAsc_ascending() {
        Sort sort = config.buildPageable(1, 20, "fechaCaptura,asc").getSort();
        assertThat(sort.getOrderFor("fechaCaptura").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("buildPageable: \"field,desc\" -> descending")
    void buildPageable_sortDesc_descending() {
        Sort sort = config.buildPageable(1, 20, "fechaCaptura,desc").getSort();
        assertThat(sort.getOrderFor("fechaCaptura").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("validatePageable: page size above maxPageSize throws")
    void validatePageable_pageSizeAboveMax_throws() {
        PaginationConfig strict = new PaginationConfig();
        strict.setMaxPageSize(10);

        assertThatThrownBy(() -> strict.validatePageable(strict.buildPageable(1, 11, null)))
                .isInstanceOf(SipsaValidationException.class)
                .hasMessageContaining("10");
    }

    @Test
    @DisplayName("validatePageable: page size at or below maxPageSize does not throw")
    void validatePageable_pageSizeAtMax_doesNotThrow() {
        PaginationConfig strict = new PaginationConfig();
        strict.setMaxPageSize(10);

        assertThatCode(() -> strict.validatePageable(strict.buildPageable(1, 10, null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validatePageable: a negative page number throws (unreachable via buildPageable, which already "
            + "clamps to >= 0, and via Spring's own PageRequest.of, which itself rejects a negative page before "
            + "reaching this method - exercised here directly against a hand-stubbed Pageable to prove the "
            + "method's own contract, not a path any current caller can trigger)")
    void validatePageable_negativePageNumber_throws() {
        Pageable negativePage = mock(Pageable.class);
        when(negativePage.getPageNumber()).thenReturn(-1);
        when(negativePage.getPageSize()).thenReturn(20);

        assertThatThrownBy(() -> config.validatePageable(negativePage))
                .isInstanceOf(SipsaValidationException.class)
                .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("validateIds: null ids are allowed (optional filters)")
    void validateIds_nullIds_allowed() {
        assertThatCode(() -> config.validateIds((Long) null, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateIds: positive ids are allowed")
    void validateIds_positiveIds_allowed() {
        assertThatCode(() -> config.validateIds(1L, 100L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateIds: zero is rejected")
    void validateIds_zero_rejected() {
        assertThatThrownBy(() -> config.validateIds(0L)).isInstanceOf(SipsaValidationException.class);
    }

    @Test
    @DisplayName("validateIds: a negative id is rejected")
    void validateIds_negative_rejected() {
        assertThatThrownBy(() -> config.validateIds(5L, -1L)).isInstanceOf(SipsaValidationException.class);
    }
}
