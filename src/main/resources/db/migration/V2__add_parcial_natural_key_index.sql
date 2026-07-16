-- -------------------------------------------------------------------------------------------------
-- V2 — Support index for the SipsaParcial natural business key (TECH-011 expand phase, ADR-001)
--
-- Purpose: composite index over the natural key confirmed by TECH-012 against real DANE
-- responses. It supports the deduplication verification queries and any future uniqueness
-- work on the natural key.
--
-- Deliberately NOT in this migration (expand phase only):
--   * no data changes (no cleanup, no backfill, no consolidation)
--   * no UNIQUE constraint on the natural columns (legacy duplicates may exist)
--   * no NOT NULL changes
--   * no new columns or tables
--
-- Plain CREATE INDEX (not CONCURRENTLY): every environment applying this today is either
-- empty (CI, fresh compose) or a controlled local dataset; there is no live production
-- traffic to protect. No IF NOT EXISTS: if the index already exists the state has drifted
-- and this migration must fail loudly (ADR-009).
-- -------------------------------------------------------------------------------------------------

CREATE INDEX idx_sipsa_parcial_natural_key
    ON sipsa_parcial (muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha);
