# TECH-124 — Análisis y optimización del filtro por artículo de `GET /api/sipsa/parcial`

**Fecha:** 2026-07-18 · **Rama:** `perf/sipsa-parcial-article-filter-index` · **Resultado:** índice V4 aplicado

Evidencia de la decisión de añadir `idx_sipsa_parcial_article_date`
(`V4__add_parcial_article_query_index.sql`). Todas las mediciones se hicieron sobre la
**publicación real de DANE** recargada en PostgreSQL 18.0 local (Docker,
`postgres:18.0-alpine3.22`): **677.061 filas**, tabla de 170 MB (344 MB con índices).
Los tiempos locales **no son garantía productiva**; son la base comparativa entre
alternativas sobre datos reales.

---

## 1. Consulta real del endpoint

Capturada del log de PostgreSQL (`log_statement=all`) al invocar el endpoint — no es una
aproximación:

```sql
-- Consulta principal (entidad completa, 17 columnas)
select sp1_0.id, ... from sipsa_parcial sp1_0
where sp1_0.id_arti_semana = $1
order by sp1_0.enma_fecha desc
offset $2 rows fetch first $3 rows only;

-- Count por página (Spring Data lo ejecuta en cada petición Page)
select count(sp1_0.id) from sipsa_parcial sp1_0 where sp1_0.id_arti_semana = $1;
```

Confirmado por captura directa:

- el alias `artiId` produce **exactamente el mismo SQL** (se resuelve a `idArtiSemana`
  en `ParcialQueryRequest.effectiveArticleId()` antes de construir la Specification);
- ambos parámetros contradictorios → `400` sin tocar la base;
- orden por defecto `enmaFecha DESC`, página de 20, count en **cada** página;
- se recupera la **entidad completa** (no hay proyección) — el count es la única
  consulta de proyección reducida (solo `id`).

Detalle decisivo: Hibernate emite `count(sp1_0.id)`, **no** `count(*)`. Un índice que no
cubra `id` no puede servir el count como index-only scan.

## 2. Distribución de `id_arti_semana`

- **36 artículos distintos** (1..36), distribución casi uniforme: del 0,76 %
  (id=34, 5.150 filas) al 3,76 % (id=10, 25.475 filas). Selectividad típica ≈ 2,8 %.
- Las filas de cada artículo están **dispersas por casi todas las páginas del heap**
  (cada semana DANE publica todos los artículos): el count del artículo más frecuente
  toca ~21.000 de ~21.700 páginas aunque use bitmap scan.
- Casos medidos: alta = 10 (25.475), media = 2 (18.519), baja = 34 (5.150),
  inexistente = 999.

## 3. Línea base (sin índice de artículo) — `EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)`

Warm cache (3.ª ejecución); cold-ish (tras reinicio del contenedor) entre paréntesis
cuando difiere de forma relevante:

| Consulta | Plan | Tiempo warm |
|---|---|---|
| Página 0, art. alta (10) | Index Scan Backward `idx_sipsa_parcial_fecha` + Filter (483 filas descartadas) | 0,08 ms |
| Página 0, art. media (2) | ídem | 0,07 ms |
| Página 0, art. baja (34) | ídem (más filas descartadas) | 0,20 ms |
| Página 0, art. inexistente (999) | **Parallel Seq Scan** completo + sort | **18,1 ms** |
| Count art. alta | **Parallel Seq Scan** (2 workers, 21,7K buffers) | **18,4 ms** (cold ~50 ms) |
| Count art. media / baja / inexistente | **Parallel Seq Scan** (idéntico costo: siempre recorre todo) | 17,3–17,8 ms |
| Art. + municipio (10 + 05001) | Index Scan Backward fecha + Filter (7.429 descartadas) | 0,49 ms |
| Art. + rango fechas | Index Scan Backward fecha | 0,06 ms |
| Página 100 (offset 2000) | Gather Merge + Parallel Index Scan fecha | 3,7 ms |
| Página 1000 (offset 20000) | Gather Merge + Parallel Index Scan fecha (20.020 filas producidas) | **30,9 ms** |

Lectura del problema:

- La página 0 ya era sub-milisegundo: el planner recorre `idx_sipsa_parcial_fecha`
  hacia atrás y con ~2,8 % de selectividad encuentra 20 filas rápido. El "Parallel
  Sequential Scan" del reporte original corresponde al **count** (y al artículo
  inexistente), no a la primera página.
- El **count es el costo dominante y se paga en cada petición**, con costo plano
  ~17–28 ms independiente de la cardinalidad.
- `idx_sipsa_parcial_natural_key (muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha)`
  no sirve: `id_arti_semana` es su 4.ª columna; sin igualdad sobre las tres primeras no
  hay acceso ordenado por artículo (un skip scan de PG18 sobre `muni_id × fuen_id ×
  futi_id` no compite y el planner nunca lo eligió en las mediciones).

## 4. Alternativas evaluadas

`hypopg` no está disponible en la imagen; se midieron **índices reales temporales**
(creados y eliminados uno a uno, con `ANALYZE` entre cambios). Tiempos warm en ms:

| Consulta | Base | A `(id_arti_semana)` | A+ `(id_arti_semana) INCLUDE (id)` | B `(id_arti_semana, enma_fecha DESC)` | **B+ `(…, enma_fecha DESC) INCLUDE (id)`** | C `(id_arti_semana, muni_id, enma_fecha DESC)` |
|---|--:|--:|--:|--:|--:|--:|
| Página 0 alta | 0,08 | 0,08 | 0,10 | 0,08 | 0,08 | 0,10 |
| Página 0 baja | 0,20 | 0,22 | 0,23 | 0,08 | **0,06** | 0,21 |
| Página 0 inexistente | 18,1 | 0,05 | 0,06 | 0,03 | **0,02** | 0,05 |
| Count alta | 18,4 | 23,8 | 2,3 | 23,8 | **2,1** | 27,6 |
| Count baja | 17,3 | 7,1 | 0,5 | 7,1 | **0,5** | 7,0 |
| Count inexistente | 17,3 | 0,04 | 0,02 | 0,04 | **0,02** | 0,05 |
| Art.+muni | 0,49 | 0,66 | 0,74 | 0,66 | 0,64 | 0,03 |
| Art.+fechas | 0,06 | 0,09 | 0,07 | 0,06 | 0,06 | 0,08 |
| Página 1000 | 30,9 | 26,3 | 32,0 | 26,6 | 30,0 | 32,6 |
| **Tamaño** | — | 4,5 MB | 20 MB | 6,9 MB | **26 MB** | 26 MB |
| **Creación (677K)** | — | 0,14 s | 0,19 s | 0,16 s | **0,20 s** | 0,29 s |

Conclusiones por alternativa:

- **A y B no arreglan el count** — `count(id)` exige visitar el heap (Bitmap Heap Scan
  de 7–24 ms; para el artículo frecuente lee ~21.000 páginas porque sus filas están en
  todas partes). Solo las variantes `INCLUDE (id)` habilitan el Index Only Scan
  (medido: `Heap Fetches: 0`, 129 buffers).
- **C** solo mejora artículo+municipio (0,66 → 0,03 ms, ya sub-milisegundo hoy), no
  soporta el `ORDER BY` global del filtro por artículo y no arregla el count. Descartada.
- **B+ vs A+** (+6 MB): B+ además incorpora el ordenamiento real del endpoint
  (`enma_fecha DESC` como 2.ª columna), con lo que cualquier cardinalidad de artículo
  tiene un recorrido ordenado sin depender del truco del índice global de fecha, y los
  rangos artículo+fecha son contiguos en el índice. El ordenamiento **forma parte del
  índice porque es parte fija del contrato de la consulta** (orden por defecto del
  endpoint) y es lo que convierte el peor caso estructural (artículo raro o inexistente
  + ORDER BY) en un recorrido acotado.
- `INCLUDE` se limita a `id`: la consulta principal recupera la entidad completa y
  cubrir más columnas solo inflaría el índice sin posibilidad real de index-only scan.

**Ganadora: B+** → `CREATE INDEX idx_sipsa_parcial_article_date ON sipsa_parcial
(id_arti_semana, enma_fecha DESC) INCLUDE (id);`

## 5. Costo de escritura medido

- Inserción masiva de 677.061 filas (`INSERT … SELECT` local): 0,36–0,40 s sin índices;
  1,10–1,19 s solo con B+ (**≈ +0,75 s por 677K filas ≈ +0,55 ms por batch de 500**);
  4,8 s con el juego completo de índices actual (B+ añade ~15–20 % al mantenimiento de
  índices, que a su vez es una fracción pequeña de la ingesta real dominada por SOAP).
- **Reingestión todo-skip real** (segunda corrida DANE completa con el índice ya
  presente): 45 s, 677.061 vistos, 0 insertados — el camino de deduplicación no toca el
  índice nuevo.
- **Carga completa real** con el índice presente desde base limpia: misma magnitud que
  la carga sin índice (ver entrada V4 del database-changelog), dominada por la descarga
  SOAP.
- Almacenamiento: +26 MB (tabla 170 MB); WAL de creación ≈ tamaño del índice;
  mantenimiento por insert y overhead de vacuum/analyze asumidos y documentados.

## 6. Paginación profunda y count — fuera de alcance (TECH-124 no los cambia)

- La degradación de páginas profundas (offset 20000 ≈ 23–31 ms) es **inherente a
  OFFSET** (produce y descarta 20.020 filas de entidad completa): ningún índice la
  elimina. Con ~1.274 páginas por artículo hoy es un caso marginal y no se formaliza
  historia de keyset pagination; **umbral**: formalizarla si aparecen consumidores que
  paginen sistemáticamente más allá de ~page 100 o si el volumen multiplica el actual.
- El count por página quedó en 0,5–2,3 ms warm. Si el API algún día no necesita
  `totalElements` (hoy el contrato lo expone como `count`/`pages`), un endpoint `Slice`
  sin count sería una historia separada; con el count ya index-only no hay evidencia
  para abrirla.

## 7. Umbral de reevaluación

Revisar este análisis con métricas reales (TECH-032) si:

- el volumen de `sipsa_parcial` supera ~5× el actual (~3,4 M filas), o
- aparecen artículos nuevos que cambien la cardinalidad (hoy 36) u
- observabilidad productiva muestra p95 > 100 ms en el endpoint con filtro de artículo, o
- se detecta uso sistemático de páginas profundas (candidato: keyset pagination).

## 8. Limitaciones de la evidencia

- Base local (Docker sobre macOS/ARM64, PostgreSQL 18.0): sin concurrencia, cache
  favorable, sin latencia de red. Los deltas relativos entre alternativas son la
  evidencia; los absolutos no son SLA.
- El Index Only Scan del count depende del visibility map: tras cada ingesta semanal,
  las páginas nuevas requieren heap fetches hasta el siguiente `VACUUM` — el patrón de
  carga (batch semanal + autovacuum) lo mantiene efectivo.
- Sin `hypopg` en la imagen, las alternativas se midieron con índices reales en la base
  local desechable (creados/eliminados uno a uno); no se modificaron migraciones hasta
  la decisión final.
