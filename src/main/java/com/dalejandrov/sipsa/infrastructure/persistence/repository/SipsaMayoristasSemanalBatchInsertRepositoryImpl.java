package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasSemanal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

/**
 * JDBC implementation of the concurrency-safe fallback batch insert (TECH-060), mirroring
 * {@link SipsaParcialBatchInsertRepositoryImpl}'s TECH-117 technique.
 * <p>
 * <b>Why native SQL:</b> a per-row {@code findByBusinessKeys} lookup followed by
 * {@code saveAll + flush} turns a lost {@code SELECT → not exists → INSERT} race into a
 * unique-violation exception that marks the transaction rollback-only and discards the
 * whole batch — and costs one round trip per row. {@code ON CONFLICT (arti_id, fuen_id,
 * fecha_ini) DO NOTHING} (backed by the existing {@code ux_semana_fallback} constraint)
 * resolves both problems at once: the race resolves atomically inside the insert with no
 * exception, and it does so in the same single statement that performs the insert — no
 * separate existence-check query at all.
 * <p>
 * <b>Batching and parameter limits:</b> one JDBC batch of single-row statements — 14
 * parameters per statement, far below PostgreSQL's per-statement limit — so this holds for
 * any batch size (the configurable ingestion maximum is 10,000 rows). No multi-row
 * {@code VALUES} concatenation, so no sub-batch splitting is needed, and
 * {@code reWriteBatchedInserts} must stay disabled so the driver returns exact per-row
 * update counts (the metrics rely on them).
 * <p>
 * <b>Generated IDs:</b> deliberately not fetched — the caller only needs the insert/skip
 * counts, never the {@code BIGSERIAL} id, so no {@code RETURNING} clause is used.
 * <p>
 * Participates in the caller's Spring-managed transaction ({@code JdbcTemplate} resolves
 * the transaction-bound connection).
 */
@RequiredArgsConstructor
public class SipsaMayoristasSemanalBatchInsertRepositoryImpl implements SipsaMayoristasSemanalBatchInsertRepository {

    /** All persistent columns except the auto-generated {@code id} — 14 parameters/row. */
    private static final String INSERT_SQL = """
            INSERT INTO sipsa_mayoristas_semanal
                (tmp_mayo_sem_id, arti_id, arti_nombre, fuen_id, fuen_nombre, futi_id,
                 fecha_ini, fecha_creacion, minimo_kg, maximo_kg, promedio_kg, enviado,
                 fecha_sincronizacion, ingestion_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (arti_id, fuen_id, fecha_ini) DO NOTHING""";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public int[] insertIgnoringConflicts(List<SipsaMayoristasSemanal> rows) {
        if (rows == null || rows.isEmpty()) {
            return new int[0];
        }
        return jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                bindRow(ps, rows.get(i));
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    private static void bindRow(PreparedStatement ps, SipsaMayoristasSemanal row) throws SQLException {
        setNullableLong(ps, 1, row.getTmpMayoSemId());
        setNullableLong(ps, 2, row.getArtiId());
        ps.setString(3, row.getArtiNombre());
        setNullableLong(ps, 4, row.getFuenId());
        ps.setString(5, row.getFuenNombre());
        setNullableLong(ps, 6, row.getFutiId());
        ps.setTimestamp(7, toTimestamp(row.getFechaIni()));
        ps.setTimestamp(8, toTimestamp(row.getFechaCreacion()));
        ps.setBigDecimal(9, row.getMinimoKg());
        ps.setBigDecimal(10, row.getMaximoKg());
        ps.setBigDecimal(11, row.getPromedioKg());
        ps.setBigDecimal(12, row.getEnviado());
        ps.setTimestamp(13, toTimestamp(row.getFechaSincronizacion()));
        setNullableLong(ps, 14, row.getIngestionRunId());
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
