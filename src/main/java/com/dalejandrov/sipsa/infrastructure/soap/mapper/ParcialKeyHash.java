package com.dalejandrov.sipsa.infrastructure.soap.mapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * Deterministic business-key hash for {@code SipsaParcial} records (ADR-001, Option A).
 * <p>
 * The hash identity is the natural business key confirmed by TECH-012 against real DANE
 * responses: {@code (muniId, fuenId, futiId, idArtiSemana, enmaFecha)}. The same inputs
 * always produce the same hash, so the {@code key_hash UNIQUE} constraint and the
 * skip-first upsert in {@code SipsaParcialRepository} can deduplicate across ingestion
 * runs.
 * <p>
 * <b>Payload layout (version-prefixed, unit-separator delimited):</b>
 * <pre>{@code
 * v2<US>muni=<trimmed muniId><US>fuen=<fuenId><US>futi=<futiId><US>arti=<idArtiSemana><US>fecha=<ISO-8601 date>
 * }</pre>
 * where {@code <US>} is the ASCII unit separator {@code 0x1F} — a character that cannot
 * appear in DIVIPOLA codes or decimal identifiers, so no field combination is ambiguous.
 * The result is the lowercase hex SHA-256 of the UTF-8 payload (64 chars, fits the
 * existing {@code key_hash VARCHAR(100)} column) and is structurally distinguishable
 * from the legacy random UUIDs (36 chars with dashes).
 * <p>
 * <b>TECH-104 — version bump v1 → v2, deliberate hash break, accepted risk:</b>
 * {@code enmaFecha} changed from {@code Instant} to {@code LocalDate} (the V5 migration
 * retyped the underlying column from {@code TIMESTAMPTZ} to {@code DATE} — it's a DANE
 * calendar date, not an instant). {@code LocalDate} has no epoch-millis representation, so
 * the payload's {@code fecha=} component switched from {@code enmaFecha.toEpochMilli()}
 * (v1) to {@code enmaFecha.toString()}, an ISO-8601 date like {@code 2026-07-15} (v2). The
 * version prefix changed too, precisely so a v1 hash and a v2 hash for the same logical
 * record are never mistaken for each other. <b>Consequence, deliberately accepted (see the
 * V5 migration's header comment): a {@code key_hash} computed before this change does not
 * equal the {@code key_hash} computed after it for the same record.</b> A full
 * re-ingestion of already-ingested {@code SipsaParcial} data after this change inserts
 * duplicates instead of deduplicating against pre-migration rows, unless {@code key_hash}
 * is backfilled with the v2 formula first. Accepted because no live production data exists
 * yet (ADR-009/TECH-132 — no {@code terraform apply} has been run against a real AWS
 * account).
 * <p>
 * Null or blank key components are rejected with {@link IllegalArgumentException}:
 * callers ({@code ParcialIngestionHandler}) must have already rejected incomplete
 * records — a silent fallback here would corrupt the identity.
 */
public final class ParcialKeyHash {

    /** ASCII unit separator — cannot occur in DIVIPOLA codes or numeric identifiers. */
    private static final char SEP = '\u001F';
    private static final String VERSION = "v2";

    private ParcialKeyHash() {
    }

    /**
     * Computes the deterministic key hash for a Parcial business key.
     *
     * @param muniId       municipality DIVIPOLA code (kept as text — leading zeros matter)
     * @param fuenId       market source ID
     * @param futiId       source type ID
     * @param idArtiSemana weekly article ID
     * @param enmaFecha    survey calendar date (TECH-104 — see class Javadoc for the
     *                     v1→v2 hash-compatibility break this caused)
     * @return lowercase hex SHA-256, 64 characters
     * @throws IllegalArgumentException if any component is null, blank, or contains the separator
     */
    public static String compute(String muniId, Long fuenId, Long futiId, Long idArtiSemana, LocalDate enmaFecha) {
        String muni = requireText(muniId);
        require(fuenId, "fuenId");
        require(futiId, "futiId");
        require(idArtiSemana, "idArtiSemana");
        require(enmaFecha, "enmaFecha");

        String payload = VERSION
                + SEP + "muni=" + muni
                + SEP + "fuen=" + fuenId
                + SEP + "futi=" + futiId
                + SEP + "arti=" + idArtiSemana
                + SEP + "fecha=" + enmaFecha;

        return HexFormat.of().formatHex(sha256().digest(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static String requireText(String muniId) {
        require(muniId, "muniId");
        String trimmed = muniId.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("muniId must not be blank");
        }
        if (trimmed.indexOf(SEP) >= 0) {
            throw new IllegalArgumentException("muniId contains the reserved separator character");
        }
        return trimmed;
    }

    private static void require(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
