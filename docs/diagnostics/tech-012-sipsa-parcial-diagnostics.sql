-- =====================================================================================
-- TECH-012 — Diagnóstico de `sipsa_parcial` (SOLO LECTURA)
-- =====================================================================================
-- Propósito: medir duplicación real, crecimiento y estado de la clave natural candidata
--            (muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha) antes de decidir
--            ADR-001 / TECH-011.
--
-- GARANTÍAS:
--   * Este archivo contiene ÚNICAMENTE sentencias SELECT.
--   * No crea tablas, índices, vistas ni funciones. No modifica datos.
--   * No expone valores de negocio más allá de los campos de la clave y conteos
--     (sin nombres de municipio/artículo/fuente, sin precios en los listados).
--
-- RECOMENDACIONES DE EJECUCIÓN (tabla potencialmente grande: ~619K registros/corrida):
--   1. Ejecutar fuera de la ventana de ingestión diaria (la corrida diaria dispara
--      a las 14:20 America/Bogota; ideal en la mañana).
--   2. OPCIONAL (recomendado): abrir la sesión en modo lectura y con límites. Son
--      ajustes de SESIÓN (se pierden al cerrar la conexión, no cambian nada persistente
--      en el servidor) y por eso van comentados — ejecutarlos manualmente si se desea:
--        -- SET default_transaction_read_only = on;
--        -- SET statement_timeout = '5min';
--        -- SET lock_timeout = '5s';
--   3. Las consultas marcadas [COSTOSA — ESCANEO COMPLETO] recorren toda la tabla
--      (seq scan) con agregación/sort. Con millones de filas pueden tardar minutos.
--      Ejecutarlas de a una.
--   4. Ninguna consulta toma locks más allá del ACCESS SHARE implícito de un SELECT;
--      no bloquean la ingestión, pero compiten por I/O.
--   5. Si la tabla supera ~20M filas, ejecutar primero las secciones 1, 6 y 9
--      (baratas) y decidir si el resto se corre con LIMIT o por rangos de fecha.
--
-- Convención de la clave natural en este script:
--   la tupla (muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha) comparada como
--   ROW(...), tratando NULL como valor distinto (IS NOT DISTINCT FROM lo maneja
--   el GROUP BY nativo de PostgreSQL, que agrupa NULLs juntos — deseado aquí).
-- =====================================================================================


-- =====================================================================================
-- 1. VOLUMEN GLOBAL — barata
--    Total de filas, filas por estado de key_hash, y sanidad del key_hash actual.
-- =====================================================================================
SELECT
    COUNT(*)                                   AS total_filas,
    COUNT(key_hash)                            AS con_key_hash,
    COUNT(*) - COUNT(key_hash)                 AS key_hash_null,
    COUNT(DISTINCT key_hash)                   AS key_hash_distintos,
    -- si key_hash_distintos < con_key_hash hubo colisión del UUID (esperado: 0)
    COUNT(key_hash) - COUNT(DISTINCT key_hash) AS key_hash_repetidos
FROM sipsa_parcial;


-- =====================================================================================
-- 2. CLAVES NATURALES ÚNICAS vs TOTAL — [COSTOSA — ESCANEO COMPLETO] (agregación sobre toda la tabla)
--    Cuántas identidades de negocio existen y cuántas filas sobran.
-- =====================================================================================
SELECT
    COUNT(*)                                                                    AS total_filas,
    COUNT(DISTINCT (muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha))     AS claves_naturales_unicas,
    COUNT(*) - COUNT(DISTINCT (muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha)) AS filas_duplicadas_excedentes
FROM sipsa_parcial;


-- =====================================================================================
-- 3. GRUPOS DUPLICADOS — [COSTOSA — ESCANEO COMPLETO] (GROUP BY sobre toda la tabla)
--    Cuántos grupos de clave natural tienen más de una fila y cuánto exceso acumulan.
-- =====================================================================================
SELECT
    COUNT(*)                    AS grupos_duplicados,
    SUM(repeticiones)           AS filas_en_grupos_duplicados,
    SUM(repeticiones - 1)       AS filas_a_consolidar,
    MAX(repeticiones)           AS max_repeticiones_de_un_grupo,
    ROUND(AVG(repeticiones), 2) AS promedio_repeticiones_por_grupo
FROM (
    SELECT COUNT(*) AS repeticiones
    FROM sipsa_parcial
    GROUP BY muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha
    HAVING COUNT(*) > 1
) g;


-- =====================================================================================
-- 4. TOP 20 GRUPOS MÁS REPETIDOS — [COSTOSA — ESCANEO COMPLETO] (GROUP BY + sort)
--    La clave se reporta ANONIMIZADA (md5 de la tupla) — los conteos y fechas de
--    inserción bastan para el diagnóstico; ningún valor de negocio sale del servidor.
--    Si un grupo concreto requiere inspección posterior, localizarlo por su hash con
--    una consulta ad-hoc autorizada.
-- =====================================================================================
SELECT
    md5(concat_ws('|', muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha))
                                     AS clave_anonimizada,
    COUNT(*)                         AS repeticiones,
    COUNT(DISTINCT ingestion_run_id) AS corridas_involucradas,
    MIN(fecha_sincronizacion)        AS primera_insercion,
    MAX(fecha_sincronizacion)        AS ultima_insercion
FROM sipsa_parcial
GROUP BY muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha
HAVING COUNT(*) > 1
ORDER BY COUNT(*) DESC, MAX(fecha_sincronizacion) DESC
LIMIT 20;


-- =====================================================================================
-- 5. MISMA CLAVE, VALORES NO-CLAVE DIFERENTES — [COSTOSA — ESCANEO COMPLETO, la más pesada]
--    ¿DANE re-publica la misma clave con precios corregidos? (insumo directo para la
--    pregunta de negocio nº 2 y nº 5 de ADR-001).
--    Solo conteos: cuántos grupos duplicados tienen >1 combinación de precios.
-- =====================================================================================
SELECT
    COUNT(*) FILTER (WHERE variantes_precio > 1)  AS grupos_con_precios_distintos,
    COUNT(*) FILTER (WHERE variantes_precio = 1)  AS grupos_con_precios_identicos,
    COUNT(*)                                      AS grupos_duplicados_total
FROM (
    SELECT COUNT(DISTINCT (promedio_kg, maximo_kg, minimo_kg)) AS variantes_precio
    FROM sipsa_parcial
    GROUP BY muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha
    HAVING COUNT(*) > 1
) v;


-- =====================================================================================
-- 6. CORRIDAS DE INGESTIÓN — barata (tabla ingestion_runs es pequeña)
--    Contexto: cuántas corridas exitosas ha tenido el método y su métrica registrada.
--    (Consulta original del backlog TECH-012, ampliada.)
-- =====================================================================================
SELECT
    status,
    COUNT(*)              AS corridas,
    MIN(start_time)       AS primera,
    MAX(start_time)       AS ultima,
    SUM(records_seen)     AS total_vistos,
    SUM(records_inserted) AS total_insertados,
    SUM(records_updated)  AS total_actualizados
FROM ingestion_runs
WHERE method_name = 'promediosSipsaParcial'
GROUP BY status
ORDER BY status;


-- =====================================================================================
-- 7. CRECIMIENTO POR CORRIDA — media (usa índice idx_sipsa_parcial_ingestion_run)
--    Filas realmente persistidas por cada corrida (últimas 30).
-- =====================================================================================
SELECT
    p.ingestion_run_id,
    r.window_key,
    r.status,
    r.start_time,
    COUNT(*) AS filas_persistidas
FROM sipsa_parcial p
LEFT JOIN ingestion_runs r ON r.run_id = p.ingestion_run_id
GROUP BY p.ingestion_run_id, r.window_key, r.status, r.start_time
ORDER BY r.start_time DESC NULLS LAST
LIMIT 30;


-- =====================================================================================
-- 8. CRECIMIENTO POR DÍA — media
--    Filas por día de sincronización (últimos 60 días con datos).
-- =====================================================================================
SELECT
    fecha_sincronizacion::date AS dia,
    COUNT(*)                   AS filas_insertadas
FROM sipsa_parcial
GROUP BY fecha_sincronizacion::date
ORDER BY dia DESC
LIMIT 60;


-- =====================================================================================
-- 9. TAMAÑOS FÍSICOS — barata (catálogo)
-- =====================================================================================
SELECT
    pg_size_pretty(pg_total_relation_size('sipsa_parcial')) AS tamano_total,
    pg_size_pretty(pg_relation_size('sipsa_parcial'))       AS tamano_datos,
    pg_size_pretty(pg_indexes_size('sipsa_parcial'))        AS tamano_indices;

SELECT
    indexrelname                                       AS indice,
    pg_size_pretty(pg_relation_size(indexrelid))       AS tamano,
    idx_scan                                           AS veces_usado
FROM pg_stat_user_indexes
WHERE relname = 'sipsa_parcial'
ORDER BY pg_relation_size(indexrelid) DESC;


-- =====================================================================================
-- 10. CAMPOS DE LA CLAVE EN NULL — [COSTOSA — ESCANEO COMPLETO] (full scan, sin sort)
--     El handler rechaza registros con clave incompleta ANTES de persistir, así que
--     lo esperado es 0 en los cuatro primeros. `enma_fecha_null` es CRÍTICO: si es
--     alto, DANE emite xs:dateTime sin zona horaria y `Instant.parse` está fallando
--     en silencio (ParcialIngestionHandler.parseDate devuelve null) — hallazgo H-1
--     del spike.
-- =====================================================================================
SELECT
    COUNT(*) FILTER (WHERE muni_id IS NULL)        AS muni_id_null,
    COUNT(*) FILTER (WHERE fuen_id IS NULL)        AS fuen_id_null,
    COUNT(*) FILTER (WHERE futi_id IS NULL)        AS futi_id_null,
    COUNT(*) FILTER (WHERE id_arti_semana IS NULL) AS id_arti_semana_null,
    COUNT(*) FILTER (WHERE enma_fecha IS NULL)     AS enma_fecha_null,
    COUNT(*)                                       AS total
FROM sipsa_parcial;


-- =====================================================================================
-- 11. DISTRIBUCIÓN TEMPORAL DE enma_fecha — media
--     Rango y densidad mensual; también revela si enma_fecha trae hora ≠ 00:00
--     (relevante para decidir si la identidad es fecha o instante — pregunta nº 7).
-- =====================================================================================
SELECT
    MIN(enma_fecha)                                        AS fecha_minima,
    MAX(enma_fecha)                                        AS fecha_maxima,
    COUNT(DISTINCT enma_fecha)                             AS instantes_distintos,
    COUNT(DISTINCT enma_fecha::date)                       AS dias_distintos,
    COUNT(*) FILTER (WHERE enma_fecha::time <> '00:00:00'
                       AND enma_fecha IS NOT NULL)         AS filas_con_hora_no_medianoche
FROM sipsa_parcial;

SELECT
    date_trunc('month', enma_fecha)::date AS mes,
    COUNT(*)                              AS filas
FROM sipsa_parcial
WHERE enma_fecha IS NOT NULL
GROUP BY 1
ORDER BY 1 DESC
LIMIT 24;


-- =====================================================================================
-- 12. IMPACTO ESTIMADO DE UNA CONSOLIDACIÓN — [COSTOSA]
--     Si se conservara 1 fila por clave natural (la de mayor fecha_sincronizacion),
--     cuántas filas se eliminarían y cuántos bytes se recuperarían (estimado por
--     tamaño medio de fila).
-- =====================================================================================
SELECT
    total_filas,
    claves_unicas,
    total_filas - claves_unicas                                   AS filas_a_eliminar,
    ROUND(100.0 * (total_filas - claves_unicas)
          / NULLIF(total_filas, 0), 2)                            AS porcentaje_a_eliminar,
    pg_size_pretty(
        ((total_filas - claves_unicas)
         * (pg_total_relation_size('sipsa_parcial')
            / NULLIF(total_filas, 0)))::bigint)                   AS espacio_estimado_a_recuperar
FROM (
    SELECT
        COUNT(*) AS total_filas,
        COUNT(DISTINCT (muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha)) AS claves_unicas
    FROM sipsa_parcial
) t;


-- =====================================================================================
-- FIN — Resultados a documentar en el informe TECH-012:
--   total, claves únicas, filas excedentes, grupos duplicados, ratio filas/corrida,
--   % enma_fecha NULL (hallazgo H-1), % grupos con precios distintos (preguntas 2 y 5),
--   tamaños, y la decisión resultante: ¿se requiere limpieza antes de TECH-011?
-- =====================================================================================
