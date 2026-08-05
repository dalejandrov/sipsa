package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaAbastecimientosMensual;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * JDBC implementation of the concurrency-safe tmpId batch insert, mirroring
 * {@link SipsaMayoristasMensualTmpBatchInsertRepositoryImpl}'s technique.
 * <p>
 * <b>Why native SQL:</b> a per-row {@code findByTmpId} lookup followed by
 * {@code saveAll + flush} turns a lost {@code SELECT → not exists → INSERT} race into a
 * unique-violation exception that marks the transaction rollback-only and discards the
 * whole batch — and costs one round trip per row. {@code ON CONFLICT (tmp_abas_mes_id)
 * DO NOTHING} (backed by the existing {@code ux_abas_tmp} constraint) resolves both
 * problems at once: the race resolves atomically inside the insert with no exception, and
 * it does so in the same single statement that performs the insert — no separate
 * existence-check query at all.
 * <p>
 * <b>Batching and parameter limits:</b> one JDBC batch of single-row statements — 12
 * parameters per statement, far below PostgreSQL's per-statement limit — so this holds for
 * any batch size. No multi-row {@code VALUES} concatenation, so no sub-batch splitting is
 * needed, and {@code reWriteBatchedInserts} must stay disabled so the driver returns exact
 * per-row update counts (the metrics rely on them).
 * <p>
 * <b>Generated IDs:</b> deliberately not fetched — the caller only needs the insert/skip
 * counts, never the {@code BIGSERIAL} id, so no {@code RETURNING} clause is used.
 * <p>
 * Participates in the caller's Spring-managed transaction ({@code JdbcTemplate} resolves
 * the transaction-bound connection).
 */
@RequiredArgsConstructor
public class SipsaAbastecimientosMensualTmpBatchInsertRepositoryImpl implements SipsaAbastecimientosMensualTmpBatchInsertRepository {

    /** All persistent columns except the auto-generated {@code id} — 12 parameters/row. */
    private static final String INSERT_SQL = """
            INSERT INTO sipsa_abastecimientos_mensual
                (tmp_abas_mes_id, arti_id, arti_nombre, fuen_id, fuen_nombre, futi_id,
                 fecha_mes_ini, fecha_creacion, cantidad_ton, enviado,
                 fecha_sincronizacion, ingestion_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tmp_abas_mes_id) DO NOTHING""";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public int[] insertIgnoringTmpConflicts(List<SipsaAbastecimientosMensual> rows) {
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

    private static void bindRow(PreparedStatement ps, SipsaAbastecimientosMensual row) throws SQLException {
        setNullableLong(ps, 1, row.getTmpAbasMesId());
        setNullableLong(ps, 2, row.getArtiId());
        ps.setString(3, row.getArtiNombre());
        setNullableLong(ps, 4, row.getFuenId());
        ps.setString(5, row.getFuenNombre());
        setNullableLong(ps, 6, row.getFutiId());
        ps.setDate(7, toSqlDate(row.getFechaMesIni()));
        ps.setTimestamp(8, toTimestamp(row.getFechaCreacion()));
        ps.setBigDecimal(9, row.getCantidadTon());
        ps.setBigDecimal(10, row.getEnviado());
        ps.setTimestamp(11, toTimestamp(row.getFechaSincronizacion()));
        setNullableLong(ps, 12, row.getIngestionRunId());
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

    private static Date toSqlDate(LocalDate localDate) {
        return localDate == null ? null : Date.valueOf(localDate);
    }
}
