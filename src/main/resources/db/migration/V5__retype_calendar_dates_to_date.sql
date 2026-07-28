-- -------------------------------------------------------------------------------------------------
-- V5 — Retype the 4 DANE calendar-date fields from TIMESTAMPTZ to DATE (TECH-104)
--
-- Problem: fecha_captura, fecha_mes_ini (x2 tables), fecha_ini, and enma_fecha are DANE
-- calendar/period-start dates, not instants — DANE documents them as a survey/period date,
-- always effectively midnight in Colombia. They were typed TIMESTAMPTZ (Instant in Java),
-- which meant the correct calendar day depended on every reader converting via the fixed
-- America/Bogota business zone (TimezoneUtil.toBusinessLocalDate) rather than being
-- guaranteed by the type itself. The API response layer was already fixed to LocalDate
-- (PR #36, TECH-100/103/106); this migration retypes the underlying column/entity to match,
-- closing the SPIKE TECH-104 confirmed feasible (see docs/backlog/technical-backlog.md).
--
-- USING clause: every stored value today is exact America/Bogota midnight (the ingestion
-- pipeline has only ever written it that way), so
-- `(col AT TIME ZONE 'America/Bogota')::date` recovers the intended calendar day exactly —
-- the same computation TimezoneUtil.toBusinessLocalDate already performs in Java, just
-- moved from "every read" to "once, here". PostgreSQL rewrites each column's dependent
-- indexes automatically as part of ALTER COLUMN ... TYPE — no separate DROP/CREATE INDEX
-- needed for idx_sipsa_ciudad_fecha, idx_sipsa_parcial_fecha (plus V2's natural-key index
-- and V4's covering index, both on enma_fecha), idx_sipsa_semana_fecha, idx_sipsa_mes_fecha,
-- or idx_sipsa_abas_fecha. NOT NULL and the 3 fallback UNIQUE constraints
-- (ux_semana_fallback, ux_mes_fallback, ux_abas_fallback) are preserved unchanged by a type
-- change alone.
--
-- ACCEPTED, DOCUMENTED RISK — SipsaParcial deduplication (ADR-001/TECH-012):
-- ParcialKeyHash.compute()'s deterministic key_hash payload used to include
-- enma_fecha.toEpochMilli(). A LocalDate has no epoch-millis representation, so the hash
-- payload changes (see ParcialKeyHash's "v2" Javadoc for the exact new formula). This
-- means: existing key_hash values already stored in sipsa_parcial do NOT match what would
-- be computed for the same logical record after this migration + the corresponding code
-- change. A future full re-ingestion of already-ingested SipsaParcial data will insert
-- duplicates rather than deduplicate against pre-migration rows. This was a deliberate,
-- explicitly accepted trade-off (no live production data exists yet — see ADR-009/TECH-132,
-- no `terraform apply` has been run against a real AWS account), not an oversight. If this
-- table ever holds real production data before this migration ships, key_hash must be
-- backfilled with the new formula first, or a full clean re-ingestion accepted deliberately.
-- -------------------------------------------------------------------------------------------------

ALTER TABLE sipsa_ciudad
    ALTER COLUMN fecha_captura TYPE DATE USING (fecha_captura AT TIME ZONE 'America/Bogota')::date;

ALTER TABLE sipsa_parcial
    ALTER COLUMN enma_fecha TYPE DATE USING (enma_fecha AT TIME ZONE 'America/Bogota')::date;

ALTER TABLE sipsa_mayoristas_semanal
    ALTER COLUMN fecha_ini TYPE DATE USING (fecha_ini AT TIME ZONE 'America/Bogota')::date;

ALTER TABLE sipsa_mayoristas_mensual
    ALTER COLUMN fecha_mes_ini TYPE DATE USING (fecha_mes_ini AT TIME ZONE 'America/Bogota')::date;

ALTER TABLE sipsa_abastecimientos_mensual
    ALTER COLUMN fecha_mes_ini TYPE DATE USING (fecha_mes_ini AT TIME ZONE 'America/Bogota')::date;
