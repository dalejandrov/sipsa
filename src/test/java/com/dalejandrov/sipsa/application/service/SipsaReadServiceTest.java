package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.api.dto.request.AbastecimientosMensualQueryRequest;
import com.dalejandrov.sipsa.api.dto.request.CiudadQueryRequest;
import com.dalejandrov.sipsa.api.dto.request.MayoristasMensualQueryRequest;
import com.dalejandrov.sipsa.api.dto.request.MayoristasSemanalQueryRequest;
import com.dalejandrov.sipsa.api.dto.request.ParcialQueryRequest;
import com.dalejandrov.sipsa.api.dto.response.SipsaAbastecimientosMensualResponse;
import com.dalejandrov.sipsa.api.dto.response.SipsaCiudadResponse;
import com.dalejandrov.sipsa.api.dto.response.SipsaMayoristasMensualResponse;
import com.dalejandrov.sipsa.api.dto.response.SipsaMayoristasSemanalResponse;
import com.dalejandrov.sipsa.api.dto.response.SipsaParcialResponse;
import com.dalejandrov.sipsa.api.mapper.SipsaAbastecimientosMensualMapper;
import com.dalejandrov.sipsa.api.mapper.SipsaCiudadMapper;
import com.dalejandrov.sipsa.api.mapper.SipsaMayoristasMensualMapper;
import com.dalejandrov.sipsa.api.mapper.SipsaMayoristasSemanalMapper;
import com.dalejandrov.sipsa.api.mapper.SipsaParcialMapper;
import com.dalejandrov.sipsa.domain.entity.SipsaAbastecimientosMensual;
import com.dalejandrov.sipsa.domain.entity.SipsaCiudad;
import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasMensual;
import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasSemanal;
import com.dalejandrov.sipsa.domain.entity.SipsaParcial;
import com.dalejandrov.sipsa.domain.exception.SipsaValidationException;
import com.dalejandrov.sipsa.infrastructure.config.PaginationConfig;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaAbastecimientosMensualRepository;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaCiudadRepository;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaMayoristasMensualRepository;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaMayoristasSemanalRepository;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaParcialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TECH-157: {@link SipsaReadService}'s own responsibilities in {@code executeQuery} -
 * 1-based-to-0-based pagination conversion, {@code PaginationConfig.validateIds}
 * rejecting non-positive filter IDs before the repository is ever called, and the
 * repository result being mapped through the entity-specific mapper. This does not
 * re-verify which {@code Specification} predicates {@code SpecificationBuilder} builds
 * for a given field name - that contract is already covered directly by
 * {@code SpecificationBuilderTest}/{@code SpecificationBuilderPostgresTest}.
 * <p>
 * Uses a real {@link PaginationConfig} (pure logic, no need to mock) and mocked
 * repositories/mappers for all 5 query methods; {@link #getCiudad} is covered fully as
 * the representative case, the other 4 lightly (they share the same
 * {@code executeQuery} template).
 */
class SipsaReadServiceTest {

    private SipsaCiudadRepository ciudadRepository;
    private SipsaMayoristasMensualRepository mensualRepository;
    private SipsaParcialRepository parcialRepository;
    private SipsaMayoristasSemanalRepository semanalRepository;
    private SipsaAbastecimientosMensualRepository abasRepository;
    private SipsaCiudadMapper ciudadMapper;
    private SipsaMayoristasMensualMapper mensualMapper;
    private SipsaParcialMapper parcialMapper;
    private SipsaMayoristasSemanalMapper semanalMapper;
    private SipsaAbastecimientosMensualMapper abasMapper;
    private SipsaReadService service;

    @BeforeEach
    void setUp() {
        ciudadRepository = mock(SipsaCiudadRepository.class);
        mensualRepository = mock(SipsaMayoristasMensualRepository.class);
        parcialRepository = mock(SipsaParcialRepository.class);
        semanalRepository = mock(SipsaMayoristasSemanalRepository.class);
        abasRepository = mock(SipsaAbastecimientosMensualRepository.class);
        ciudadMapper = mock(SipsaCiudadMapper.class);
        mensualMapper = mock(SipsaMayoristasMensualMapper.class);
        parcialMapper = mock(SipsaParcialMapper.class);
        semanalMapper = mock(SipsaMayoristasSemanalMapper.class);
        abasMapper = mock(SipsaAbastecimientosMensualMapper.class);

        service = new SipsaReadService(ciudadRepository, mensualRepository, parcialRepository,
                semanalRepository, abasRepository, ciudadMapper, mensualMapper, parcialMapper,
                semanalMapper, abasMapper, new PaginationConfig());
    }

    @Test
    @DisplayName("getCiudad: page 3 (1-based, API) becomes Pageable index 2 (0-based, Spring Data)")
    void getCiudad_convertsOneBasedPageToZeroBased() {
        SipsaCiudad entity = SipsaCiudad.builder().regId(1L).build();
        SipsaCiudadResponse dto = mock(SipsaCiudadResponse.class);
        when(ciudadMapper.toDto(entity)).thenReturn(dto);
        when(ciudadRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        CiudadQueryRequest request = new CiudadQueryRequest(null, null, null, null, null, null, null, 3, 10, null);
        Page<SipsaCiudadResponse> result = service.getCiudad(request);

        assertThat(result.getContent()).containsExactly(dto);
        org.mockito.ArgumentCaptor<Pageable> pageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(ciudadRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("getCiudad: negative artiId -> SipsaValidationException, repository never called")
    void getCiudad_negativeArtiId_rejectedBeforeRepository() {
        CiudadQueryRequest request = new CiudadQueryRequest(null, null, null, -5L, null, null, null, 1, 20, null);

        assertThatThrownBy(() -> service.getCiudad(request)).isInstanceOf(SipsaValidationException.class);

        verifyNoInteractions(ciudadRepository);
    }

    @Test
    @DisplayName("getCiudad: negative fuenId -> SipsaValidationException, repository never called")
    void getCiudad_negativeFuenId_rejectedBeforeRepository() {
        CiudadQueryRequest request = new CiudadQueryRequest(null, null, null, null, -1L, null, null, 1, 20, null);

        assertThatThrownBy(() -> service.getCiudad(request)).isInstanceOf(SipsaValidationException.class);

        verify(ciudadRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("getCiudad: page size above PaginationConfig.maxPageSize is rejected (unreachable via this DTO in "
            + "practice - CiudadQueryRequest's own compact constructor clamps size to <=100, well under the "
            + "default maxPageSize of 1000 - documented here as the method's real contract, not exercised through "
            + "the DTO)")
    void getCiudad_pageSizeAboveMax_rejected() {
        PaginationConfig strictConfig = new PaginationConfig();
        strictConfig.setMaxPageSize(5);
        SipsaReadService strictService = new SipsaReadService(ciudadRepository, mensualRepository, parcialRepository,
                semanalRepository, abasRepository, ciudadMapper, mensualMapper, parcialMapper,
                semanalMapper, abasMapper, strictConfig);
        CiudadQueryRequest request = new CiudadQueryRequest(null, null, null, null, null, null, null, 1, 10, null);

        assertThatThrownBy(() -> strictService.getCiudad(request)).isInstanceOf(SipsaValidationException.class);

        verifyNoInteractions(ciudadRepository);
    }

    @Test
    @DisplayName("getMayoristasMensual: valid request wires repository + mapper through the shared template")
    void getMayoristasMensual_validRequest_repositoryAndMapperWired() {
        SipsaMayoristasMensual entity = SipsaMayoristasMensual.builder().artiId(1L).build();
        SipsaMayoristasMensualResponse dto = mock(SipsaMayoristasMensualResponse.class);
        when(mensualMapper.toDto(entity)).thenReturn(dto);
        when(mensualRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        MayoristasMensualQueryRequest request =
                new MayoristasMensualQueryRequest(null, null, null, null, null, null, 2, 15, null);
        Page<SipsaMayoristasMensualResponse> result = service.getMayoristasMensual(request);

        assertThat(result.getContent()).containsExactly(dto);
        verify(mensualRepository).findAll(any(Specification.class), eqPageable(1, 15));
    }

    @Test
    @DisplayName("getMayoristasMensual: negative artiId -> SipsaValidationException, repository never called")
    void getMayoristasMensual_negativeArtiId_rejected() {
        MayoristasMensualQueryRequest request =
                new MayoristasMensualQueryRequest(null, null, null, -1L, null, null, 1, 20, null);

        assertThatThrownBy(() -> service.getMayoristasMensual(request)).isInstanceOf(SipsaValidationException.class);

        verifyNoInteractions(mensualRepository);
    }

    @Test
    @DisplayName("getMayoristasSemanal: valid request wires repository + mapper through the shared template")
    void getMayoristasSemanal_validRequest_repositoryAndMapperWired() {
        SipsaMayoristasSemanal entity = SipsaMayoristasSemanal.builder().artiId(1L).build();
        SipsaMayoristasSemanalResponse dto = mock(SipsaMayoristasSemanalResponse.class);
        when(semanalMapper.toDto(entity)).thenReturn(dto);
        when(semanalRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        MayoristasSemanalQueryRequest request =
                new MayoristasSemanalQueryRequest(null, null, null, null, null, null, null, 1, 20, null);
        Page<SipsaMayoristasSemanalResponse> result = service.getMayoristasSemanal(request);

        assertThat(result.getContent()).containsExactly(dto);
        verify(semanalRepository).findAll(any(Specification.class), eqPageable(0, 20));
    }

    @Test
    @DisplayName("getMayoristasSemanal: negative fuenId -> SipsaValidationException, repository never called")
    void getMayoristasSemanal_negativeFuenId_rejected() {
        MayoristasSemanalQueryRequest request =
                new MayoristasSemanalQueryRequest(null, null, null, null, -2L, null, null, 1, 20, null);

        assertThatThrownBy(() -> service.getMayoristasSemanal(request)).isInstanceOf(SipsaValidationException.class);

        verifyNoInteractions(semanalRepository);
    }

    @Test
    @DisplayName("getAbastecimientosMensual: valid request wires repository + mapper through the shared template")
    void getAbastecimientosMensual_validRequest_repositoryAndMapperWired() {
        SipsaAbastecimientosMensual entity = SipsaAbastecimientosMensual.builder().artiId(1L).build();
        SipsaAbastecimientosMensualResponse dto = mock(SipsaAbastecimientosMensualResponse.class);
        when(abasMapper.toDto(entity)).thenReturn(dto);
        when(abasRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        AbastecimientosMensualQueryRequest request =
                new AbastecimientosMensualQueryRequest(null, null, null, null, null, null, null, 1, 20, null);
        Page<SipsaAbastecimientosMensualResponse> result = service.getAbastecimientosMensual(request);

        assertThat(result.getContent()).containsExactly(dto);
        verify(abasRepository).findAll(any(Specification.class), eqPageable(0, 20));
    }

    @Test
    @DisplayName("getAbastecimientosMensual: negative fuenId -> SipsaValidationException, repository never called")
    void getAbastecimientosMensual_negativeFuenId_rejected() {
        AbastecimientosMensualQueryRequest request =
                new AbastecimientosMensualQueryRequest(null, null, null, null, -3L, null, null, 1, 20, null);

        assertThatThrownBy(() -> service.getAbastecimientosMensual(request)).isInstanceOf(SipsaValidationException.class);

        verifyNoInteractions(abasRepository);
    }

    @Test
    @DisplayName("getParcial: valid request wires repository + mapper through the shared template")
    void getParcial_validRequest_repositoryAndMapperWired() {
        SipsaParcial entity = SipsaParcial.builder().muniId("05001").build();
        SipsaParcialResponse dto = mock(SipsaParcialResponse.class);
        when(parcialMapper.toDto(entity)).thenReturn(dto);
        when(parcialRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        ParcialQueryRequest request = new ParcialQueryRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, 1, 20, null);
        Page<SipsaParcialResponse> result = service.getParcial(request);

        assertThat(result.getContent()).containsExactly(dto);
        verify(parcialRepository).findAll(any(Specification.class), eqPageable(0, 20));
    }

    @Test
    @DisplayName("getParcial: negative fuenId -> SipsaValidationException, repository never called")
    void getParcial_negativeFuenId_rejected() {
        ParcialQueryRequest request = new ParcialQueryRequest(
                null, null, null, null, -4L, null, null, null, null, null, null, null, 1, 20, null);

        assertThatThrownBy(() -> service.getParcial(request)).isInstanceOf(SipsaValidationException.class);

        verifyNoInteractions(parcialRepository);
    }

    private static Pageable eqPageable(int pageNumber, int pageSize) {
        return org.mockito.ArgumentMatchers.argThat(p -> p != null
                && p.getPageNumber() == pageNumber
                && p.getPageSize() == pageSize);
    }
}
