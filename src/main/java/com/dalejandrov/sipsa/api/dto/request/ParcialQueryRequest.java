package com.dalejandrov.sipsa.api.dto.request;

import com.dalejandrov.sipsa.domain.exception.SipsaValidationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Request DTO for partial market data queries.
 * <p>
 * Encapsulates all filtering parameters for partial market data queries,
 * providing better type safety and validation.
 * <p>
 * <b>Municipality filter (TECH-113):</b> {@code muniId} is <b>text</b>, never a number —
 * DIVIPOLA codes carry leading zeros (e.g. {@code "05001"}) that numeric conversion would
 * destroy. The value is trimmed and otherwise preserved exactly as received.
 * <p>
 * <b>Article filter (TECH-113):</b> {@code idArtiSemana} is the canonical parameter,
 * matching the entity attribute and the DANE contract for Parcial. {@code artiId} is kept
 * as a compatibility alias (it is the product-filter name used by the other SIPSA
 * endpoints and was documented for this one). When both are present they must agree —
 * conflicting values are rejected with HTTP 400.
 */
public record ParcialQueryRequest(
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate fechaEncuesta,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate startDate,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate endDate,

    String muniId,

    @Positive(message = "fuenId must be a positive number")
    Long fuenId,

    @Positive(message = "idArtiSemana must be a positive number")
    Long idArtiSemana,

    @Positive(message = "artiId must be a positive number")
    Long artiId,

    String muniNombre,

    String deptNombre,

    String fuenNombre,

    String artiNombre,

    String grupNombre,

    @Min(value = 1, message = "page must be >= 1")
    Integer page,

    @Min(value = 1, message = "size must be >= 1")
    @Max(value = 100, message = "size must be <= 100")
    Integer size,

    String sort
) {
    /** Matches {@code sipsa_parcial.muni_id VARCHAR(50)} — the only hard bound available. */
    private static final int MUNI_ID_MAX_LENGTH = 50;

    public ParcialQueryRequest {
        if (muniId != null) {
            muniId = muniId.trim();
        }
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 20;
        if (size > 100) size = 100;
        if (sort == null || sort.trim().isEmpty()) sort = "enmaFecha,desc";
    }

    /**
     * Returns the validated municipality filter, or null when absent.
     * <p>
     * The value is text and is preserved exactly (leading zeros included, already
     * trimmed by the constructor). A present-but-blank or overlong value is a client
     * error → {@link SipsaValidationException} → HTTP 400. No character pattern is
     * enforced: the DANE contract declares the field as plain {@code xs:string}.
     *
     * @return trimmed municipality code, or null if the filter was not provided
     * @throws SipsaValidationException if the value is blank or longer than 50 chars
     */
    public String validatedMuniId() {
        if (muniId == null) {
            return null;
        }
        if (muniId.isEmpty()) {
            throw new SipsaValidationException("muniId must not be blank when provided");
        }
        if (muniId.length() > MUNI_ID_MAX_LENGTH) {
            throw new SipsaValidationException(
                    "muniId must not exceed " + MUNI_ID_MAX_LENGTH + " characters");
        }
        return muniId;
    }

    /**
     * Resolves the effective article filter from the canonical parameter and its alias.
     * <ul>
     *   <li>only {@code idArtiSemana} → used as-is (canonical)</li>
     *   <li>only {@code artiId} → translated to the {@code idArtiSemana} attribute</li>
     *   <li>both with the same value → single condition</li>
     *   <li>both with different values → {@link SipsaValidationException} → HTTP 400</li>
     * </ul>
     *
     * @return the article ID to filter {@code idArtiSemana} by, or null if absent
     * @throws SipsaValidationException if both parameters are present and disagree
     */
    public Long effectiveArticleId() {
        if (idArtiSemana != null && artiId != null && !Objects.equals(idArtiSemana, artiId)) {
            throw new SipsaValidationException(
                    "idArtiSemana and artiId must have the same value when both are provided"
                            + " (idArtiSemana is canonical; artiId is a compatibility alias)");
        }
        return idArtiSemana != null ? idArtiSemana : artiId;
    }
}
