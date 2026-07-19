package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaParcial;
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
 * JDBC implementation of the concurrency-safe batch insert (TECH-117).
 * <p>
 * <b>Why native SQL:</b> JPA's {@code saveAll + flush} turns a lost
 * {@code SELECT → not exists → INSERT} race into a unique-violation exception that marks
 * the transaction rollback-only and discards the whole batch. PostgreSQL's
 * {@code ON CONFLICT … DO NOTHING} resolves the same race atomically inside the insert,
 * without an exception, and the JDBC update count tells exactly which rows were inserted
 * ({@code 1}) versus dropped by conflict ({@code 0}).
 * <p>
 * <b>Batching and parameter limits:</b> the batch executes as ONE JDBC batch of
 * single-row statements — 16 parameters per statement, far below the PostgreSQL
 * extended-protocol limit of 32,767 <i>per statement</i>, which therefore holds for any
 * batch size (the configurable maximum is 10,000 rows). No multi-row {@code VALUES}
 * concatenation, so no sub-batch splitting is needed. The driver returns exact per-row
 * update counts because {@code reWriteBatchedInserts} is not enabled (and must not be
 * enabled for this path: rewriting collapses the per-row counts the metrics rely on).
 * <p>
 * <b>Generated IDs:</b> deliberately not fetched. Ingestion discards the entities right
 * after each flush ({@code batch.clear()}), so the {@code BIGSERIAL} IDs assigned by
 * PostgreSQL are never needed in memory — skipping {@code RETURNING} keeps the batch a
 * plain, count-returning insert. Conflicting rows keep their original stored values,
 * including the {@code ingestion_run_id} of the run that first inserted them.
 * <p>
 * Participates in the caller's Spring-managed transaction ({@code JdbcTemplate} resolves
 * the transaction-bound connection), so metrics, audit rows and inserts commit together.
 */
@RequiredArgsConstructor
public class SipsaParcialBatchInsertRepositoryImpl implements SipsaParcialBatchInsertRepository {

    /** All persistent columns except the auto-generated {@code id} — 16 parameters/row. */
    private static final String INSERT_SQL = """
            INSERT INTO sipsa_parcial
                (key_hash, muni_id, muni_nombre, dept_nombre, fuen_id, fuen_nombre, futi_id,
                 id_arti_semana, arti_nombre, grup_nombre, enma_fecha,
                 promedio_kg, maximo_kg, minimo_kg, fecha_sincronizacion, ingestion_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (key_hash) DO NOTHING""";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public int[] insertIgnoringConflicts(List<SipsaParcial> rows) {
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

    private static void bindRow(PreparedStatement ps, SipsaParcial row) throws SQLException {
        ps.setString(1, row.getKeyHash());
        ps.setString(2, row.getMuniId());
        ps.setString(3, row.getMuniNombre());
        ps.setString(4, row.getDeptNombre());
        setNullableLong(ps, 5, row.getFuenId());
        ps.setString(6, row.getFuenNombre());
        setNullableLong(ps, 7, row.getFutiId());
        setNullableLong(ps, 8, row.getIdArtiSemana());
        ps.setString(9, row.getArtiNombre());
        ps.setString(10, row.getGrupNombre());
        ps.setTimestamp(11, toTimestamp(row.getEnmaFecha()));
        ps.setBigDecimal(12, row.getPromedioKg());
        ps.setBigDecimal(13, row.getMaximoKg());
        ps.setBigDecimal(14, row.getMinimoKg());
        ps.setTimestamp(15, toTimestamp(row.getFechaSincronizacion()));
        setNullableLong(ps, 16, row.getIngestionRunId());
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
