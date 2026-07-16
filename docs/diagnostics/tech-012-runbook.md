# Runbook — Ejecución controlada de TECH-012 y plan de transición de `SipsaParcial`

**Fecha:** 2026-07-16
**Script:** [`tech-012-sipsa-parcial-diagnostics.sql`](tech-012-sipsa-parcial-diagnostics.sql) (solo `SELECT`, fusionado en PR #19)
**Relacionados:** [SPIKE de integridad](../architecture/sipsa-parcial-data-integrity-spike.md) ·
[ADR-001](../adr/ADR-001-data-deduplication.md) (`Accepted` 2026-07-16) · [ADR-009](../adr/ADR-009-database-migration-strategy.md) ·
[Database Changelog](../database/database-changelog.md) · TECH-010/011/012/081
**Este documento no cambia el estado de ninguna historia ni de ningún ADR.**

---

# Parte I — Ejecución de TECH-012

> **Ejecución realizada (2026-07-16, ambiente local controlado):** el script se ejecutó
> tres veces contra un PostgreSQL 18 de Docker Compose cargado con ingestas reales del
> endpoint de DANE (no existe base externa histórica conocida; ese supuesto sigue
> pendiente de confirmación formal — TECH-012 mitad externa / TECH-115). Resultados:
> corrida completa = 676.210 registros (histórico 2020-02 → hoy re-publicado íntegro en
> cada llamada); pre-fix, la segunda corrida duplicó el 100% (todas las repeticiones ×2,
> precios idénticos); post-fix (TECH-011), corridas 2 y 3 → `inserted=0`,
> `skipped=676.210`, 0 grupos duplicados. `enma_fecha_null=0` (H-1 no confirmado).
> El script referencia `start_time` (corregido — el esquema real nunca tuvo `started_at`).

## 1. Prerrequisitos (los 5 son bloqueantes)

| # | Requisito | Detalle |
|---|---|---|
| 1 | Autorización escrita | Del dueño del dato, indicando el ambiente exacto |
| 2 | Endpoint | Host/puerto + confirmación de si es **réplica de lectura, restauración, copia anonimizada o producción** |
| 3 | Usuario **solo lectura** | `SELECT` sobre `sipsa_parcial`, `ingestion_runs` y catálogo (`pg_stat_user_indexes`); sin permisos `CREATE`/`TEMP` |
| 4 | Ventana de ejecución | Fuera de 14:20–23:59 `America/Bogota` (ventana de ingestión diaria) si el ambiente recibe escrituras |
| 5 | Cliente | `psql` ≥ 14, acceso de red (o bastión/túnel), script en la revisión fusionada |

**Orden de preferencia de ambiente:** 1) réplica de lectura, 2) restauración reciente,
3) copia anonimizada, 4) producción **solo como última opción**, con autorización explícita
y limitada a las consultas permitidas en §4. La restauración es la opción más segura en
términos absolutos (cero impacto sobre producción y permite las consultas pesadas sin
restricción); la réplica da datos más frescos — registrar el lag si aplica.

## 2. Variables y credenciales

```bash
export TECH012_HOST=...      TECH012_PORT=5432
export TECH012_DB=...        TECH012_USER=sipsa_readonly
export TECH012_ENV=replica   # replica | restore | anon-copy | prod
```

- Contraseña **nunca** en la línea de comandos ni en el historial del shell: usar
  `~/.pgpass` (`chmod 600`, formato `host:port:db:user:password`) o `PGPASSWORD`
  exportada desde el gestor de secretos.
- El nombre del archivo de salida incluye `TECH012_ENV` para que nunca haya duda de
  contra qué ambiente se corrió.

## 3. Comando y límites de sesión

```bash
mkdir -p ~/tech012-results
psql "host=$TECH012_HOST port=$TECH012_PORT dbname=$TECH012_DB user=$TECH012_USER sslmode=verify-full" \
  --set=ON_ERROR_STOP=1 -P pager=off \
  -o ~/tech012-results/tech012-$(date +%Y%m%d-%H%M)-$TECH012_ENV.txt \
  -f docs/diagnostics/tech-012-sipsa-parcial-diagnostics.sql
```

- **SSL:** `sslmode=verify-full` con el CA del servidor en `~/.postgresql/root.crt`;
  mínimo aceptable `require` si no hay CA distribuible.
- **Límites de sesión** (primera acción dentro de `psql`; son ajustes de sesión no
  persistentes, mueren con la conexión):

```sql
SET default_transaction_read_only = on;
SET statement_timeout = '5min';
SET lock_timeout = '5s';
```

- **Primera ejecución: por bloques, no el archivo entero.** Orden: (a) Q6, Q9, Q10
  (baratas, dan contexto y tamaño); (b) con el tamaño conocido, decidir si el resto va
  directo o con `EXPLAIN` previo; (c) Q11, Q7, Q8, Q12, Q13 (moderadas); (d) Q1, Q2, Q14
  (costosas); (e) Q3, Q4, Q5 (las más pesadas — Q5 siempre la última).
- **`EXPLAIN` sin ejecutar:** anteponer `EXPLAIN` (a secas — **nunca** `EXPLAIN ANALYZE`,
  que sí ejecuta la consulta) a toda consulta marcada costosa y revisar el plan antes de
  lanzarla.
- **Cuándo detenerse:** una consulta excede el `statement_timeout` dos veces;
  `pg_stat_activity` muestra impacto sobre sesiones de la aplicación (solo aplica fuera
  de réplica/copia); Q9 revela una tabla ≥ 50 GB (replantear Q3–Q5 por rangos de
  `enma_fecha`); cualquier error de permisos (el usuario no es el pactado).
- **Evidencia compartible:** el `.txt` ya evita valores de negocio (el top-20 sale como
  `md5`). Antes de compartir: retirar hostnames/IP/DSN del encabezado; adjuntar solo
  conteos, tamaños y hashes.

## 4. Clasificación de las 14 consultas

El script tiene 12 secciones; las secciones 9 y 11 contienen 2 `SELECT` cada una → 14
consultas (Q1–Q14).

| Q | Sección | Lee | Escaneo completo | Índices | Salida | Riesgo | Clase | Resultado esperado |
|---|---|---|---|---|---|---|---|---|
| Q1 | §1 volumen global | `sipsa_parcial` | Sí (o index-only sobre `idx_key_hash`) | posible | 1 fila | Medio | Costosa | `key_hash_distintos = con_key_hash`; total ≈ 619K × corridas exitosas |
| Q2 | §2 claves únicas | `sipsa_parcial` | Sí + agregación de tupla | no | 1 fila | Alto | Costosa — `EXPLAIN` previo | Excedente ≈ total − (total/corridas) si PS-01 es cierta |
| Q3 | §3 grupos duplicados | `sipsa_parcial` | Sí + GROUP BY + HAVING | no | 1 fila | Alto | Costosa — solo réplica/copia, `EXPLAIN` previo | `max_repeticiones ≈ nº corridas exitosas` |
| Q4 | §4 top-20 anonimizado | `sipsa_parcial` | Sí + GROUP BY + sort | no | 20 filas | Alto | Costosa — solo réplica/copia, `EXPLAIN` previo | Repeticiones homogéneas si DANE re-publica todo cada día |
| Q5 | §5 variantes de precio | `sipsa_parcial` | Sí + doble agregación (la más pesada) | no | 1 fila | Muy alto | Costosa — solo réplica/copia, `EXPLAIN` previo, ejecutar la última | Decide skip vs upsert |
| Q6 | §6 corridas | `ingestion_runs` (pequeña) | Sí, tabla mínima | n/a | ≤ 5 filas | Nulo | Rápida | Corridas SUCCEEDED + métricas |
| Q7 | §7 filas por corrida | ambas tablas | Sí (GROUP BY baja cardinalidad) | `idx_ingestion_run` ayuda | ≤ 30 filas | Medio | Moderada | Filas/corrida ≈ constante |
| Q8 | §8 filas por día | `sipsa_parcial` | Sí | no | ≤ 60 filas | Medio | Moderada | Crecimiento diario ≈ una corrida |
| Q9 | §9a tamaño tabla | catálogo | No | n/a | 1 fila | Nulo | Rápida | Dimensiona Q3–Q5 y la limpieza |
| Q10 | §9b tamaño índices | `pg_stat_user_indexes` | No | n/a | 4 filas | Nulo | Rápida | `idx_key_hash` grande, `idx_scan = 0` |
| Q11 | §10 NULLs de clave | `sipsa_parcial` | Sí (una pasada, sin sort) | no | 1 fila | Medio | Moderada | 4 primeros = 0; **`enma_fecha_null` = veredicto de H-1** |
| Q12 | §11a rango temporal | `sipsa_parcial` | Sí (+2 COUNT DISTINCT) | `idx_enma_fecha` para min/max | 1 fila | Medio-alto | Moderada — `EXPLAIN` si > 20M filas | Decide identidad instante-vs-fecha |
| Q13 | §11b filas por mes | `sipsa_parcial` | Sí | no | ≤ 24 filas | Medio | Moderada | Densidad mensual estable |
| Q14 | §12 impacto consolidación | `sipsa_parcial` | Sí (misma agregación que Q2) | no | 1 fila | Alto | Costosa — `EXPLAIN` previo | % a eliminar y espacio a recuperar |

**En producción (última opción):** solo Q6, Q9, Q10, Q11 sin restricciones; Q1/Q2/Q14 con
`EXPLAIN` previo y dentro de la ventana; **Q3–Q5 únicamente en réplica o copia**.

## 5. Plantilla de registro de resultados

```markdown
## Resultados TECH-012 — sipsa_parcial
| Campo | Valor | Consulta |
|---|---|---|
| Fecha y hora de ejecución (COT) |  | — |
| Ambiente (réplica/restauración/copia/prod) y lag si aplica |  | — |
| Ejecutado por / autorizado por |  | — |
| Total de filas |  | Q1 |
| key_hash distintos / nulos |  | Q1 |
| Claves naturales distintas |  | Q2 |
| Filas duplicadas excedentes (y % sobre total) |  | Q2 |
| Grupos duplicados / filas a consolidar / máx. repeticiones |  | Q3 |
| Top-20: patrón de repetición (hashes md5, sin valores) |  | Q4 |
| Grupos con precios distintos vs idénticos |  | Q5 |
| Corridas SUCCEEDED / FAILED del método |  | Q6 |
| Filas por corrida (mín/máx/promedio) |  | Q7 |
| Crecimiento diario promedio |  | Q8 |
| Tamaño tabla / datos / índices |  | Q9–Q10 |
| Campos clave en NULL (muni/fuen/futi/idArtiSemana) |  | Q11 |
| **enma_fecha NULL (H-1)** — nº y % |  | Q11 |
| Instantes distintos vs días distintos; filas con hora ≠ 00:00 |  | Q12 |
| Estabilidad de idArtiSemana (inferida de Q3–Q5 + Q4) |  | Q3–Q5 |
| Filas a eliminar / % / espacio estimado a recuperar |  | Q14 |
| Anomalías u observaciones |  | — |
```

Regla: solo conteos, porcentajes, tamaños y hashes — ningún valor de negocio crudo.

## 6. Criterios de relevancia de duplicados (multidimensional)

Ningún umbral único (p. ej. "< 1%") determina la relevancia. Se evalúan conjuntamente:

| Dimensión | Fuente | Lectura |
|---|---|---|
| Porcentaje sobre el total | Q2/Q14 | Contexto, nunca criterio único |
| Número absoluto | Q2 | > ~100K filas condiciona la mecánica del backfill aunque el % sea bajo |
| Divergencia de campos no clave | Q5 | **Cualquier divergencia > 0 es relevante por sí sola** (define qué versión sirve la API) |
| Distribución temporal | Q4 (`ultima_insercion`) + Q13 | Duplicados de los últimos 30 días pesan más: con orden `enmaFecha,desc`, la API los sirve en las primeras páginas |
| Tasa de crecimiento | Q7 (tendencia) | Crecimiento sostenido por corrida = problema activo, no legado |
| Concentración | Q4 (patrón del top-20) | Concentración en ciertos municipios/fuentes sugiere causa sistemática distinta de PS-01 — investigar antes de consolidar |
| Impacto en consultas | Q9/Q10 + planes | Índices inflados, páginas duplicadas visibles |
| Impacto en almacenamiento | Q14 | Dimensiona la limpieza, no la urgencia |

**Clasificación:** **Críticos** — divergencia > 0, o duplicados recientes visibles por la
API, o crecimiento activo → tratar antes de aceptar ADR-001. **Relevantes** — volumen o
almacenamiento significativos, sin divergencia ni recencia → consolidación planificada.
**Menores** — residuales, antiguos, idénticos, sin crecimiento → pueden convivir hasta la
fase contract.

## 7. Árbol de decisiones según resultados

- **Caso A — sin duplicados relevantes** (según §6, no un umbral aislado): validar
  igualmente la clave con Q4/Q5 (puede no haber duplicados solo porque nunca hubo dos
  corridas del mismo período); si la clave se sostiene → hash determinista + insert-only
  + skip; ADR-001 a `Accepted` con la evidencia; TECH-011 sin migración de limpieza
  (solo transición de hashes legados).
- **Caso B — duplicados idénticos** (Q5 ≈ 0): consolidar por clave conservando la fila
  canónica (`max(fecha_sincronizacion)`, empate → `max(id)`); evaluar tabla de archivo
  según Q14 y la pregunta de negocio nº 4; después TECH-011.
- **Caso C — duplicados con valores distintos** (Q5 > 0): **no borrar nada**; presentar a
  negocio la proporción con ejemplos anonimizados; decidir la versión que prevalece y la
  estrategia (upsert / historial / híbrido); ADR-001 se reescribe antes de aceptarse.
- **Caso D — `enma_fecha` frecuentemente NULL** (Q11): H-1 pasa a **bug confirmado**;
  primer PR de código = corrección de parseo con rechazo explícito (historia propuesta
  TECH-114), **antes** de TECH-011; reingesta/reconstrucción solo tras evaluar impacto.
- **Caso E — `idArtiSemana` inestable** (Q3–Q5 muestran casi-duplicados con 4/5 campos
  iguales y repeticiones muy por debajo del nº de corridas): descartar la clave candidata,
  **no aceptar ADR-001**; investigar con DANE un identificador estable o una clave
  derivada — nuevo mini-SPIKE.

D y E se evalúan primero: invalidan la interpretación de A–C.

## 8. Anexo — inventario complementario por ambiente (misma sesión de solo lectura)

**Historia Flyway** (insumo para la historia propuesta de `baseline-on-migrate`, fuera de
TECH-011):

```sql
SELECT EXISTS (SELECT FROM information_schema.tables
               WHERE table_name = 'flyway_schema_history');
SELECT installed_rank, version, description, type, checksum, installed_on, success
FROM flyway_schema_history ORDER BY installed_rank;   -- ¿hay fila type='BASELINE'?
SELECT count(*) FROM information_schema.tables
WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history';
```

**Funciones de hash** (detección por catálogo, sin invocar la función — no falla si no
existe):

```sql
SELECT version();
SELECT to_regprocedure('sha256(bytea)');   -- NULL = no disponible como nativa
SELECT name, default_version, installed_version
FROM pg_available_extensions WHERE name = 'pgcrypto';
```

Nota: este inventario es informativo. La recomendación principal (Parte II §5) es calcular
el hash **exclusivamente en Java**, con lo cual la disponibilidad de `sha256()`/`pgcrypto`
en SQL deja de ser un requisito.

---

# Parte II — Plan de transición del `key_hash` (post-diagnóstico)

Condicionado a los resultados de TECH-012 y a las decisiones de negocio de ADR-001.
**Nada de esta parte se implementa hasta que ADR-001 sea `Accepted`.**

## 1. Numeración de migraciones — conceptual, no definitiva

Las migraciones se nombran aquí **E1 (expand), E2 (constraint), E3 (contract)**. La
numeración Flyway real (`V{N}`) se asigna desde `main` actualizado **inmediatamente antes
de implementar**, porque otros PR pueden fusionar migraciones entre este diagnóstico y la
implementación. Ningún documento debe fijar `V2/V3/V4` por adelantado.

## 2. Alternativa A — consolidar antes de cambiar la escritura (ventana de mantenimiento)

1. **Pausar todas las escrituras con garantía verificable** (ver §4 — un acuerdo verbal de
   "no invocar el endpoint" no es una pausa).
2. Consolidar duplicados existentes según decisión de negocio (Flyway o job según §6).
3. Recalcular `key_hash` de las filas canónicas al formato determinista, en la columna
   existente (no requiere columna nueva).
4. Verificar unicidad por consulta (el `key_hash UNIQUE` existente pasa a ser
   significativo).
5. Desplegar la aplicación que escribe hashes deterministas.
6. Reanudar escrituras con el checklist de §4.

## 3. Alternativa B — columna nueva y transición dual (sin downtime)

1. **E1 (Flyway):** `ADD COLUMN business_key_hash VARCHAR(64) NULL` — nada más.
2. Deploy de código compatible: calcula `business_key_hash` en inserciones nuevas y
   deduplica a nivel de aplicación contra esa columna; `key_hash` sigue funcionando como
   hasta ahora. La versión anterior ignora la columna nullable.
3. Backfill controlado de filas legadas (job operacional, §6).
4. Detección y resolución de colisiones según decisión de negocio.
5. **E2 (Flyway):** guarda que falla si quedan `NULL` o colisiones + índice **único**
   sobre `business_key_hash` (`CONCURRENTLY` si el tamaño lo exige → script no
   transaccional, ver Database Changelog §Migraciones no transaccionales).
6. Deploy fase 2: lecturas y unicidad usan solo la columna nueva.
7. **E3 (Flyway, contract):** `NOT NULL business_key_hash`; retiro del `key_hash` legado,
   su UNIQUE y el índice redundante, tras un período de retención.

### ⚠️ Duplicados durante la transición — límite explícito de la Alternativa B

La Alternativa B **evita duplicados nuevo-vs-nuevo** (dedupe de aplicación sobre
`business_key_hash`), pero **no puede impedir por sí sola duplicados nuevo-vs-legado**
mientras existan filas históricas con `business_key_hash = NULL`: una nueva publicación
del mismo registro lógico no encuentra a su gemela legada (que aún no tiene hash) y se
inserta. Durante la transición pueden por tanto coexistir y colisionar:

- una fila histórica aún sin `business_key_hash`;
- una nueva publicación del mismo registro lógico (con hash determinista);
- otra fila nueva que ya recibió hash determinista.

Estas colisiones **deben medirse y resolverse durante el backfill** (el paso 4 las trata
como datos, no como violaciones de constraint) y son la razón por la que E2 lleva guarda.

### Protección transitoria contra nuevo-vs-legado — tres opciones (decisión diferida)

| Criterio | **B1 — aceptar y reconciliar** | **B2 — lookup dual temporal** | **B3 — backfill prioritario** |
|---|---|---|---|
| Mecánica | Solo dedupe nuevo-vs-nuevo; los nuevo-vs-legado se aceptan, se miden y se resuelven en el backfill; transición de duración limitada | Antes de insertar: (1) lookup por `business_key_hash`, (2) lookup por clave natural para filas legadas sin hash | Desplegar la columna → backfillear PRIMERO los registros recientes/re-publicables → desplegar el escritor nuevo → completar el histórico |
| Costo de consultas | Mínimo (1 lookup por lote) | Doble lookup por lote; el (2) necesita índice compuesto sobre la clave natural para no degenerar en scans | Mínimo tras el pre-backfill |
| Riesgo de carrera | Duplicados transitorios garantizados si DANE re-publica períodos aún no backfilleados | Bajo; queda la carrera clásica lookup→insert (mitigada solo por constraint, que aún no existe) | Bajo si la ventana "reciente" se eligió bien; residual para re-publicaciones antiguas |
| Impacto en lotes | Ninguno | Lotes más lentos (segunda consulta por tupla o por lote) | Ninguno tras el pre-backfill |
| Índices necesarios | Ninguno extra | Índice compuesto natural-key **temporal** (crear en E1, retirar en E3) | Ninguno extra (el pre-backfill puede apoyarse en `idx_enma_fecha`) |
| Múltiples instancias | Compatible | Compatible | Compatible |
| Complejidad temporal a retirar | Ninguna | Código de lookup dual + índice temporal — deben eliminarse en la fase contract | Orquestación del backfill en dos fases |

**Decisión condicionada a TECH-012:** B1 si Q7/Q8 muestran que DANE solo re-publica el
período corriente (ventana de exposición corta) y el backfill puede completarse en días;
B3 si la re-publicación se concentra en registros recientes identificables; B2 solo si la
divergencia (Q5) hace inaceptable cualquier duplicado transitorio y el volumen permite el
doble lookup. **No se elige ninguna hasta conocer Q9 y Q14.**

### Rolling deployment — convivencia de versiones

No se asume cambio simultáneo de instancias. Si conviven versión anterior (no escribe
`business_key_hash`) y nueva (sí lo escribe), una instancia vieja seguirá insertando
filas con la columna en `NULL` durante todo el rollout.

**Regla dura: E2 (índice único) y E3 (`NOT NULL`) no se aplican mientras exista CUALQUIER
escritor capaz de producir `NULL` en la columna** — incluye instancias viejas en rollout,
rollbacks de emergencia a la versión anterior y procesos externos.

Controles posibles (elegir al implementar):

1. Despliegue de instancia única durante la transición.
2. Pausa temporal de escrituras mientras se reemplazan todas las instancias (§4).
3. Feature flag del escritor nuevo, activado solo tras confirmar que **todas** las
   instancias ejecutan la versión compatible.
4. Doble escritura compatible introducida en una versión preparatoria anterior.
5. Validación post-deploy (consulta de versiones activas + prueba de inserción) antes de
   habilitar el flujo nuevo.

**Recomendación para la escala actual** (producción de instancia única, una corrida diaria):
control 1 + control 5 — desplegar una sola instancia y verificar
(`SELECT count(*) FROM sipsa_parcial WHERE business_key_hash IS NULL AND fecha_sincronizacion > <deploy>`
= 0 tras la primera corrida) antes de dar el rollout por bueno. Si el despliegue pasa a
multi-instancia antes de esta transición, adoptar el control 3 (flag) + 5.

## 4. Pausa de escrituras con garantía verificable (requerida por la Alternativa A)

"Acordar no invocar el endpoint" **no es una pausa**. Mecanismos con garantía real, en
orden de preferencia para la escala actual:

1. `sipsa.scheduling.enabled=false` (redeploy/restart con la propiedad) — apaga el
   scheduler con certeza.
2. Revocación temporal del scope `sipsa/ingestion.execute` (o desactivación del app
   client M2M) en el emisor de tokens — las llamadas manuales reciben `403`.
3. Bloqueo de la ruta en API Gateway (cuando exista, TECH-131).
4. Feature flag de solo-lectura de ingestión (si se introduce como parte de TECH-011).
5. Escalar a cero las instancias que ejecutan ingestión (si la lectura la sirve otra
   instancia) o configuración de mantenimiento equivalente.

**Checklist de verificación ANTES de consolidar:**

- [ ] `SELECT count(*) FROM ingestion_runs WHERE status = 'RUNNING'` → 0.
- [ ] Cero llamadas activas al endpoint (logs/gateway en los últimos N minutos).
- [ ] Scheduler deshabilitado y confirmado en TODAS las instancias (config efectiva, no
      solo intención de deploy).
- [ ] Todas las instancias bajo la misma configuración (misma versión/flags).
- [ ] Ausencia confirmada de procesos externos que escriban directamente en la tabla.

**Checklist de reanudación:**

- [ ] Consolidación verificada (unicidad por consulta, conteos esperados).
- [ ] Versión nueva desplegada y sana (healthcheck, smoke de lectura).
- [ ] Scheduler re-habilitado / scope restituido / ruta desbloqueada — en ese orden.
- [ ] Primera corrida posterior supervisada: `inserted`/`skipped` coherentes con lo
      esperado (re-ingestión del período corriente → skipped > 0, inserted ≈ solo datos
      nuevos).
- [ ] Resultado registrado en el Database Changelog.

## 5. Estrategia de hash

**Recomendación principal: calcular el hash exclusivamente en Java** — la escritura nueva
(mapper) y el backfill (job de §6, código Java) comparten la misma clase de normalización
y `MessageDigest`. Con una sola implementación desaparece el riesgo de divergencia entre
normalizaciones y SQL nunca calcula hashes.

Si en algún momento se optara por backfill en SQL puro, se requeriría antes: el inventario
del Anexo (Parte I §8 — `to_regprocedure('sha256(bytea)')`, `pgcrypto` disponible y
autorizada por ambiente; su habilitación sería una migración Flyway explícita) **y** un
test Testcontainers de identidad byte a byte que compare, sobre filas fixture:
normalización, payload exacto, encoding UTF-8 (`convert_to(payload,'UTF8')`), delimitador
`0x1F`, representación del sentinel de null, representación temporal
(`(extract(epoch from ...)*1000)::bigint` vs `toEpochMilli()`) y el hex resultante en
minúsculas en ambos lados.

## 6. Criterio Flyway vs. job operacional

| Tipo de cambio | Vehículo | Condiciones |
|---|---|---|
| **Esquema** (columnas, tablas auxiliares, constraints, índices, nulabilidad, estructuras temporales) | **Siempre Flyway** | Sin excepción — ADR-009 regla 4 |
| **Datos pequeños y deterministas** | Flyway aceptable | Volumen acotado conocido; cabe en una transacción razonable; no compromete el arranque (regla práctica: < 1–2 min); fallo recuperable por fix-forward sin ambigüedad |
| **Backfill / consolidación masiva** | **Job operacional versionado** | Millones de filas; lotes; progreso reanudable; supervisión; pausable/reintentable sin bloquear despliegues |

**El job operacional no es una ejecución manual informal.** Es un artefacto versionado en
el repositorio (propuesta: comando Spring Boot activado por flag dedicado, p. ej.
`--sipsa.backfill.parcial=true`, que arranca, ejecuta y termina; nunca activo en el
runtime normal — reutiliza la normalización y el hash de Java, ver §5), con estos
requisitos de diseño:

- **Orden estable por `id`** y procesamiento por **rango/cursor** explícito.
- **Tamaño de lote** configurable, commit por lote (lotes parcialmente confirmados no se
  reprocesan con efectos dobles: la operación por fila es idempotente).
- **Bloqueo contra doble ejecución** (lock de aplicación o advisory lock de PostgreSQL).
- **Política de reintentos** por lote con backoff y **límite de errores** global que
  aborta el job.
- **Manejo de poison records:** una fila que falla repetidamente se marca y se salta, no
  bloquea el resto.
- **Reporte de conflictos** (dos filas → mismo hash) como salida estructurada para la
  decisión de negocio.
- **Reanudación desde el último lote confirmado.**
- **Criterio de finalización explícito**, verificado por consulta y usado como guarda de
  la migración E2.

**Sobre el checkpoint:** la condición `WHERE business_key_hash IS NULL` identifica filas
pendientes, pero **no es por sí sola un checkpoint suficiente** cuando hay conflictos,
filas fallidas, errores permanentes, reintentos, procesamiento paralelo o lotes
parcialmente confirmados — con `IS NULL` a secas, una poison record es indistinguible de
una fila pendiente. El job debe evaluar, según el volumen y la tasa de error que arroje
TECH-012, si necesita una **tabla de control** con estados
(`PENDING / PROCESSING / COMPLETED / CONFLICT / FAILED`). No es obligatoria de antemano;
sí lo es decidirlo explícitamente en el diseño del job, no sobre la marcha.

Secuencia resultante: **Flyway prepara el esquema (E1) → el job ejecuta los datos → Flyway
aplica el constraint final (E2), protegido por su guarda.**

## 7. Decisiones pendientes que condicionan todo lo anterior

1. Resultados completos de TECH-012 (Q1–Q14) y su clasificación según Parte I §6.
2. Las 8 preguntas de negocio de ADR-001.
3. Alternativa A vs B (regla: A si la consolidación cabe con holgura en una ventana
   nocturna; B si el volumen, el downtime o la multi-instancia lo impiden).
4. Dentro de B: B1 vs B2 vs B3.
5. ¿H-1 confirmado? → historia TECH-114 (parseo) antes de TECH-011.
6. ¿Tabla de control del job sí o no? (volumen y tasa de error).
7. ¿Archivo histórico y retención? (pregunta de negocio nº 4).
8. Verificación de historia Flyway por ambiente → historia separada para
   `baseline-on-migrate: false` (nunca dentro de TECH-011).
