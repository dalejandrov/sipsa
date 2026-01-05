-- =================================================================================================
-- EXTENSIONS
-- =================================================================================================

CREATE EXTENSION IF NOT EXISTS citext;

-- =================================================================================================
-- 1. INGESTION CONTROL TABLES
-- =================================================================================================

-- -------------------------------------------------------------------------------------------------
-- 1.1 INGESTION RUNS
-- -------------------------------------------------------------------------------------------------

CREATE TABLE ingestion_runs
(
    run_id             BIGSERIAL PRIMARY KEY,

    -- Correlation & Source
    request_id         VARCHAR(100),
    request_source     VARCHAR(20)           DEFAULT 'MANUAL',

    -- Execution identity
    method_name        VARCHAR(100) NOT NULL,
    window_key         VARCHAR(50)  NOT NULL, -- YYYY-MM-DD | YYYY-MM | YYYY-MM-M8

    -- Lifecycle
    start_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    end_time           TIMESTAMPTZ,
    status             VARCHAR(50)  NOT NULL, -- STARTED, RUNNING, SUCCEEDED, FAILED, SKIPPED_WINDOW

    -- Metrics
    records_seen       INTEGER               DEFAULT 0,
    records_inserted   INTEGER               DEFAULT 0,
    records_updated    INTEGER               DEFAULT 0,
    reject_count       INTEGER               DEFAULT 0,

    -- Fault details
    last_error_message TEXT,
    http_status        INTEGER,
    soap_fault_code    VARCHAR(100),

    -- Idempotency
    CONSTRAINT uq_ingestion_runs_window UNIQUE (method_name, window_key)
);

COMMENT ON TABLE ingestion_runs IS 'Tracks ingestion job executions with correlation and metrics';
COMMENT ON COLUMN ingestion_runs.request_id IS 'UUID from HTTP request for correlation and tracing';
COMMENT ON COLUMN ingestion_runs.request_source IS 'Source of execution: MANUAL, SCHEDULED, SYSTEM';
COMMENT ON COLUMN ingestion_runs.window_key IS 'Time window identifier for idempotent processing';

CREATE INDEX idx_ingestion_runs_request_id ON ingestion_runs (request_id);
CREATE INDEX idx_ingestion_runs_request_source ON ingestion_runs (request_source);

-- -------------------------------------------------------------------------------------------------
-- 1.2 INGESTION AUDIT
-- -------------------------------------------------------------------------------------------------

CREATE TABLE ingestion_audit
(
    audit_id       BIGSERIAL PRIMARY KEY,
    run_id         BIGINT REFERENCES ingestion_runs (run_id),

    -- Correlation & Source
    request_id     VARCHAR(100),
    request_source VARCHAR(20) DEFAULT 'MANUAL',

    -- Event details
    event_type     VARCHAR(50) NOT NULL,
    message        TEXT,
    occurred_at    TIMESTAMPTZ DEFAULT NOW()
);

COMMENT ON TABLE ingestion_audit IS 'Audit trail of all ingestion events';
COMMENT ON COLUMN ingestion_audit.request_id IS 'UUID from HTTP request for correlation and tracing';
COMMENT ON COLUMN ingestion_audit.request_source IS 'Source of execution: MANUAL, SCHEDULED, SYSTEM';

CREATE INDEX idx_ingestion_audit_run_id ON ingestion_audit (run_id);
CREATE INDEX idx_ingestion_audit_request_id ON ingestion_audit (request_id);
CREATE INDEX idx_ingestion_audit_request_source ON ingestion_audit (request_source);

-- -------------------------------------------------------------------------------------------------
-- 1.3 INGESTION REJECTS
-- -------------------------------------------------------------------------------------------------

CREATE TABLE ingestion_rejects
(
    reject_id      BIGSERIAL PRIMARY KEY,
    run_id         BIGINT REFERENCES ingestion_runs (run_id),
    raw_data       TEXT,
    reason         VARCHAR(255),
    is_parse_error BOOLEAN     DEFAULT FALSE,
    created_at     TIMESTAMPTZ DEFAULT NOW()
);

COMMENT ON TABLE ingestion_rejects IS 'Records rejected due to validation or parsing errors';

CREATE INDEX idx_ingestion_rejects_run_id ON ingestion_rejects (run_id);

-- =================================================================================================
-- 2. DOMAIN DATA TABLES (CURATED)
-- =================================================================================================

-- -------------------------------------------------------------------------------------------------
-- 2.1 SIPSA CIUDAD
-- -------------------------------------------------------------------------------------------------

CREATE TABLE sipsa_ciudad
(
    id               BIGSERIAL PRIMARY KEY,

    reg_id           BIGINT,
    ciudad           VARCHAR(255),
    cod_producto     BIGINT,
    producto         VARCHAR(255),

    fecha_captura    TIMESTAMPTZ,
    fecha_creacion   TIMESTAMPTZ,

    precio_promedio  NUMERIC(19, 2),
    enviado          NUMERIC(19, 2),

    -- Audit
    ingestion_run_id BIGINT NOT NULL REFERENCES ingestion_runs (run_id),
    fecha_ingestion  TIMESTAMPTZ DEFAULT NOW(),

    -- Idempotency
    CONSTRAINT ux_ciudad UNIQUE (reg_id, cod_producto)
);

COMMENT ON TABLE sipsa_ciudad IS 'SIPSA city-level price data';

CREATE INDEX idx_sipsa_ciudad_fecha ON sipsa_ciudad (fecha_captura);
CREATE INDEX idx_sipsa_ciudad_cod_producto ON sipsa_ciudad (cod_producto);
CREATE INDEX idx_sipsa_ciudad_reg_id ON sipsa_ciudad (reg_id);
CREATE INDEX idx_sipsa_ciudad_ingestion_run ON sipsa_ciudad (ingestion_run_id);

-- -------------------------------------------------------------------------------------------------
-- 2.2 SIPSA PARCIAL
-- -------------------------------------------------------------------------------------------------

CREATE TABLE sipsa_parcial
(
    id               BIGSERIAL PRIMARY KEY,

    key_hash         VARCHAR(64) NOT NULL,

    muni_id          VARCHAR(50),
    muni_nombre      VARCHAR(255),
    dept_nombre      VARCHAR(255),

    fuen_id          BIGINT,
    fuen_nombre      VARCHAR(255),
    futi_id          BIGINT,

    id_arti_semana   BIGINT,
    arti_nombre      VARCHAR(255),
    grup_nombre      VARCHAR(255),

    enma_fecha       TIMESTAMPTZ,

    promedio_kg      NUMERIC(19, 2),
    maximo_kg        NUMERIC(19, 2),
    minimo_kg        NUMERIC(19, 2),

    -- Audit
    ingestion_run_id BIGINT      NOT NULL REFERENCES ingestion_runs (run_id),
    last_updated     TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT ux_parcial UNIQUE (key_hash)
);

COMMENT ON TABLE sipsa_parcial IS 'SIPSA partial market data at municipality level';

CREATE INDEX idx_sipsa_parcial_fecha ON sipsa_parcial (enma_fecha);
CREATE INDEX idx_sipsa_parcial_muni ON sipsa_parcial (muni_id);
CREATE INDEX idx_sipsa_parcial_ingestion_run ON sipsa_parcial (ingestion_run_id);

-- -------------------------------------------------------------------------------------------------
-- 2.3 SIPSA MAYORISTAS SEMANAL
-- -------------------------------------------------------------------------------------------------

CREATE TABLE sipsa_mayoristas_semanal
(
    id               BIGSERIAL PRIMARY KEY,

    tmp_mayo_sem_id  BIGINT,

    arti_id          BIGINT,
    arti_nombre      VARCHAR(255),

    fuen_id          BIGINT,
    fuen_nombre      VARCHAR(255),

    futi_id          BIGINT,

    fecha_ini        TIMESTAMPTZ,
    fecha_creacion   TIMESTAMPTZ,

    minimo_kg        NUMERIC(19, 2),
    maximo_kg        NUMERIC(19, 2),
    promedio_kg      NUMERIC(19, 2),
    enviado          NUMERIC(19, 2),

    -- Audit
    ingestion_run_id BIGINT NOT NULL REFERENCES ingestion_runs (run_id),
    last_updated     TIMESTAMPTZ DEFAULT NOW(),

    -- Idempotency
    CONSTRAINT ux_semana_tmp UNIQUE (tmp_mayo_sem_id),
    CONSTRAINT ux_semana_fallback UNIQUE (arti_id, fuen_id, fecha_ini)
);

COMMENT ON TABLE sipsa_mayoristas_semanal IS 'SIPSA weekly wholesale market prices';

CREATE INDEX idx_sipsa_semana_fecha ON sipsa_mayoristas_semanal (fecha_ini);
CREATE INDEX idx_sipsa_semana_ingestion_run ON sipsa_mayoristas_semanal (ingestion_run_id);

-- -------------------------------------------------------------------------------------------------
-- 2.4 SIPSA MAYORISTAS MENSUAL
-- -------------------------------------------------------------------------------------------------

CREATE TABLE sipsa_mayoristas_mensual
(
    id               BIGSERIAL PRIMARY KEY,

    tmp_mayo_mes_id  BIGINT,

    arti_id          BIGINT      NOT NULL,
    arti_nombre      VARCHAR(255),

    fuen_id          BIGINT      NOT NULL,
    fuen_nombre      VARCHAR(255),

    futi_id          BIGINT      NOT NULL,

    fecha_mes_ini    TIMESTAMPTZ NOT NULL,
    fecha_creacion   TIMESTAMPTZ,

    minimo_kg        NUMERIC(19, 2),
    maximo_kg        NUMERIC(19, 2),
    promedio_kg      NUMERIC(19, 2),
    enviado          NUMERIC(19, 2),

    -- Audit
    ingestion_run_id BIGINT      NOT NULL REFERENCES ingestion_runs (run_id),
    last_updated     TIMESTAMPTZ DEFAULT NOW(),

    -- Idempotency
    CONSTRAINT ux_mes_tmp UNIQUE (tmp_mayo_mes_id),
    CONSTRAINT ux_mes_fallback UNIQUE (arti_id, fuen_id, fecha_mes_ini)
);

COMMENT ON TABLE sipsa_mayoristas_mensual IS 'SIPSA monthly wholesale market prices';

CREATE INDEX idx_sipsa_mes_fecha ON sipsa_mayoristas_mensual (fecha_mes_ini);
CREATE INDEX idx_sipsa_mes_ingestion_run ON sipsa_mayoristas_mensual (ingestion_run_id);

-- -------------------------------------------------------------------------------------------------
-- 2.5 SIPSA ABASTECIMIENTOS MENSUAL
-- -------------------------------------------------------------------------------------------------

CREATE TABLE sipsa_abastecimientos_mensual
(
    id               BIGSERIAL PRIMARY KEY,

    tmp_abas_mes_id  BIGINT,

    arti_id          BIGINT      NOT NULL,
    arti_nombre      VARCHAR(255),

    fuen_id          BIGINT      NOT NULL,
    fuen_nombre      VARCHAR(255),

    futi_id          BIGINT      NOT NULL,

    fecha_mes_ini    TIMESTAMPTZ NOT NULL,
    fecha_creacion   TIMESTAMPTZ,

    cantidad_ton     NUMERIC(19, 2),
    enviado          NUMERIC(19, 2),

    -- Audit
    ingestion_run_id BIGINT      NOT NULL REFERENCES ingestion_runs (run_id),
    fecha_ingestion  TIMESTAMPTZ DEFAULT NOW(),

    -- Idempotency
    CONSTRAINT ux_abas_tmp UNIQUE (tmp_abas_mes_id),
    CONSTRAINT ux_abas_fallback UNIQUE (arti_id, fuen_id, fecha_mes_ini)
);

COMMENT ON TABLE sipsa_abastecimientos_mensual IS 'SIPSA monthly supply quantities in tons';

CREATE INDEX idx_sipsa_abas_fecha ON sipsa_abastecimientos_mensual (fecha_mes_ini);
CREATE INDEX idx_sipsa_abas_ingestion_run ON sipsa_abastecimientos_mensual (ingestion_run_id);
