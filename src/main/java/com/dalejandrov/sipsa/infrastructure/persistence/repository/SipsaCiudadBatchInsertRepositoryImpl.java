package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaCiudad;
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
 * JDBC implementation of the concurrency-safe batch insert, mirroring
 * {@link SipsaParcialBatchInsertRepositoryImpl}'s TECH-117 technique.
 * <p>
 * <b>Why native SQL:</b> a bulk {@code findByBusinessKeys} lookup followed by
 * {@code saveAll + flush} turns a lost {@code SELECT → not exists → INSERT} race into a
 * unique-violation exception that marks the transaction rollback-only and discards the
 * whole batch. {@code ON CONFLICT (reg_id, cod_producto) DO NOTHING} (backed by the
 * existing {@code ux_ciudad} constraint) resolves the race atomically inside the insert,
 * without an exception, and the JDBC update count tells exactly which rows were inserted
 * ({@code 1}) versus dropped by conflict ({@code 0}).
 * <p>
 * <b>Batching and parameter limits:</b> one JDBC batch of single-row statements — 10
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
public class SipsaCiudadBatchInsertRepositoryImpl implements SipsaCiudadBatchInsertRepository {

    /** All persistent columns except the auto-generated {@code id} — 10 parameters/row. */
    private static final String INSERT_SQL = """
            INSERT INTO sipsa_ciudad
                (reg_id, ciudad, cod_producto, producto, fecha_captura, fecha_creacion,
                 precio_promedio, enviado, fecha_sincronizacion, ingestion_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (reg_id, cod_producto) DO NOTHING""";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public int[] insertIgnoringConflicts(List<SipsaCiudad> rows) {
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

    private static void bindRow(PreparedStatement ps, SipsaCiudad row) throws SQLException {
        setNullableLong(ps, 1, row.getRegId());
        ps.setString(2, row.getCiudad());
        setNullableLong(ps, 3, row.getCodProducto());
        ps.setString(4, row.getProducto());
        ps.setDate(5, toSqlDate(row.getFechaCaptura()));
        ps.setTimestamp(6, toTimestamp(row.getFechaCreacion()));
        ps.setBigDecimal(7, row.getPrecioPromedio());
        ps.setBigDecimal(8, row.getEnviado());
        ps.setTimestamp(9, toTimestamp(row.getFechaSincronizacion()));
        setNullableLong(ps, 10, row.getIngestionRunId());
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
