# SPIKE — Integridad de datos de `SipsaParcial` (TECH-010 / TECH-012 / TECH-011 / TECH-081)

**Fecha:** 2026-07-16
**Rama:** `spike/sipsa-parcial-data-integrity`
**Estado:** Diagnóstico entregado — pendiente de revisión, respuestas de negocio y ejecución de TECH-012 contra la base real.
**Estados que este documento NO cambia:** ADR-001 permanece `Proposed`; TECH-010, TECH-011,
TECH-012 y TECH-081 permanecen `Pending`. Nada aquí autoriza implementación.
**Relacionados:** [ADR-001](../adr/ADR-001-data-deduplication.md) (`Proposed`), deuda PS-01 ([technical-debt.md](technical-debt.md)), [TECH-010](../backlog/technical-backlog.md#tech-010), [TECH-011](../backlog/technical-backlog.md#tech-011), [TECH-012](../backlog/technical-backlog.md#tech-012), [TECH-081](../backlog/technical-backlog.md#tech-081).

Ningún cambio funcional acompaña este documento. El único artefacto adicional es el
script de solo lectura [`docs/diagnostics/tech-012-sipsa-parcial-diagnostics.sql`](../diagnostics/tech-012-sipsa-parcial-diagnostics.sql).

---

## 1. Flujo actual de punta a punta

```
DANE SOAP (promediosSipsaParcial)
  └─ SoapGateway.getParcialData()                     [domain/gateway/SoapGateway.java]
      └─ SoapGatewayImpl → SoapStreamingClient         (streaming HTTP, retry manual max-retries=3)
          └─ InputStream crudo del envelope
              └─ ParcialStaxParser (StAX manual)       [infrastructure/soap/parser/ParcialStaxParser.java]
                  └─ SipsaParcialRecord (record inmutable, 15 campos String/Long/BigDecimal)
                      └─ ParcialIngestionHandler.execute()   [application/ingestion/handler/ParcialIngestionHandler.java]
                          ├─ valida 5 campos de clave no-null (rechaza y audita si falta alguno)
                          ├─ parseDate(fechaEncuestaText) → Instant (o null SILENCIOSO si falla)
                          ├─ SipsaIngestionMapper.toEntity(record, fechaEncuesta, runId)
                          │     └─ keyHash = computeKeyHash(...) = UUID.randomUUID()   ← defecto PS-01
                          └─ batches de `batch-size` → SipsaParcialRepository.batchUpsert()
                                └─ saveAll(items) + flush()  — inserta TODO, skipped siempre 0
```

Referencias exactas:

| Paso | Clase / artefacto | Detalle |
|---|---|---|
| Obtención | `SoapGateway.getParcialData()` → `SoapStreamingClient` | Streaming del XML; timeouts `connect 30s / read 60min` (`application.yaml:134-135`); reintentos manuales (`max-retries: 3`) |
| Parseo | `ParcialStaxParser` (extiende `AbstractStaxParser`) | Lee elementos `muniid, fuenid, futiid, idartisemana, enmafecha, promediokg, …` (constantes en `XmlFieldNames`). **No usa JAXB**: el texto crudo de `enmafecha` llega tal cual |
| Aliases del parser | `ParcialStaxParser.Builder.build():80-87` | `artiId := idArtiSemana` y `fechaEncuestaText := enmaFecha` — el XSD del servicio **no tiene** campo `artiid` para Parcial (`SrvSipsaUpraBeanService.xsd:112-127`) |
| Validación | `ParcialIngestionHandler.execute():77-90` | Rechaza (con auditoría vía `context.addRejectedRecord`) si `muniId, fuenId, futiId, idArtiSemana, enmaFecha` (texto) es null |
| Fecha | `ParcialIngestionHandler.parseDate():138-146` | `Instant.parse(...)`; **cualquier excepción → `return null` sin log** |
| Construcción entidad | `SipsaIngestionMapper.toEntity(record, fechaEncuesta, runId):85` | MapStruct; `enmaFecha ← fechaEncuesta` (el Instant parseado); `keyHash ← computeKeyHash(...)` |
| `key_hash` | `SipsaIngestionMapper.computeKeyHash():164-166` | `return UUID.randomUUID().toString()` — ignora ambos parámetros. El Javadoc lo admite: "auto-generated UUID" |
| Persistencia | `SipsaParcialRepository.batchUpsert():41-57` | `@Transactional`; setea `fechaSincronizacion=now`, `saveAll + flush`, `UpsertMetrics(items.size(), 0)` — **no hay lookup previo, ni dedupe intra-batch, ni updated** |
| Métricas | `IngestionContext` (`recordsInserted/recordsUpdated`) → `ingestion_runs.records_*` | El handler solo incrementa `inserted` (`flushBatch():132-134`). `skipped` de `UpsertMetrics` se descarta; no existe columna `records_skipped` |
| Esquema | `V1__initial_schema.sql:142-176` | `key_hash VARCHAR(100) UNIQUE`; índices sobre `enma_fecha`, `muni_id`, `ingestion_run_id`, `key_hash`. **Sin constraint sobre la clave natural** |
| API | `GET /api/sipsa/parcial` → `SipsaReadService.getParcial():132-154` → `SpecificationBuilder` | Devuelve TODAS las filas que coincidan con el filtro, duplicados incluidos, paginadas (default 20, orden `enmaFecha,desc`). `keyHash` **no** se expone en `SipsaParcialResponse` |

**Por qué el UUID:** no hay evidencia en el código ni en el historial de una razón de negocio;
la firma `computeKeyHash(record, fechaEncuesta)` recibe exactamente los ingredientes de la
clave natural y no los usa. Todo indica un placeholder que nunca se completó: el Javadoc del
handler (`ParcialIngestionHandler.java:28-44`) **describe la estrategia correcta que nunca se
implementó** ("Uses upsert based on business keys… Business key: (muniId, fuenId, futiId,
idArtiSemana, enmaFecha)… only one record will exist in the database").

**Por qué el constraint no protege:** `key_hash UNIQUE` compara valores; como cada fila
recibe un UUID nuevo, nunca hay dos valores iguales que comparar. El constraint es
técnicamente válido y semánticamente inútil (y su índice ocupa espacio por nada).

**Qué devuelve hoy la API con duplicados:** los N duplicados aparecen como N elementos
indistinguibles (el `id` interno y el `keyHash` no se exponen), infladas las páginas y las
agregaciones que el consumidor haga aguas abajo.

---

## 2. Hallazgos adicionales del spike (adyacentes, no bloqueantes del bloque)

| ID | Hallazgo | Evidencia | Severidad |
|---|---|---|---|
| **H-1** | `parseDate` traga errores: si DANE emite `xs:dateTime` **sin zona horaria** (legal según el XSD, `enmaFecha type="xs:dateTime" minOccurs="0"`), `Instant.parse` lanza y el método devuelve `null` en silencio → `enma_fecha NULL` en toda la tabla, sin rechazo ni log. Verificado en Java 25: `2012-01-10T00:00:00-05:00` parsea bien; `2012-01-10T00:00:00` (sin zona) **falla**. La consulta 10 del script TECH-012 mide si esto está ocurriendo en producción. Si `enma_fecha` está NULL, la clave natural pierde su componente temporal — **el fix de parseo es prerrequisito de TECH-011** | `ParcialIngestionHandler.java:138-146`; `SrvSipsaUpraBeanService.xsd:116` | **Riesgo prioritario — NO confirmado como bug productivo.** Lo confirmado por código es el mecanismo (excepción → `null` silencioso) y que el formato sin zona es legal por contrato; que ocurra con datos reales es hipótesis hasta ver la consulta 10 de TECH-012 o una respuesta SOAP representativa de DANE |
| **H-2** | El filtro `artiId` de `GET /api/sipsa/parcial` está roto: `withAttribute("artiId", …)` sobre `SipsaParcial`, que **no tiene** atributo `artiId` → `root.get("artiId")` lanza `IllegalArgumentException` en tiempo de consulta (500) cuando un cliente usa ese parámetro | `SipsaReadService.java:145`; entidad `SipsaParcial` (sin campo `artiId`) | Media (bug confirmado, historia nueva sugerida) |
| **H-3** | El filtro `muniId` compara tipos incompatibles: `ParcialQueryRequest.muniId` es `Long @Positive`, pero `SipsaParcial.muniId` es `String` (código DIVIPOLA, puede llevar ceros a la izquierda) → `cb.equal(path<String>, Long)` falla o nunca matchea; además `@Positive` impide buscar códigos con cero inicial | `ParcialQueryRequest.java:27`; `SipsaParcial.java:50-51`; `paginationConfig.validateIds(...)` en `SipsaReadService.java:140` | Media (mismo grupo que H-2) |
| **H-4** | Documentación engañosa en código: el Javadoc del handler y de la entidad afirman deduplicación por clave de negocio que no existe | `ParcialIngestionHandler.java:28-44`; `SipsaParcial.java:24-25` | Baja (corregir dentro de TECH-011) |
| **H-5** | `UpsertMetrics.skipped` se descarta en todos los handlers y no se persiste (no hay `records_skipped` en `ingestion_runs`); el criterio de aceptación de TECH-011 ("skipped > 0 en la segunda corrida") necesita como mínimo exponerlo en logs/contexto | `ParcialIngestionHandler.flushBatch():128-136`; `V1__initial_schema.sql:33-35` | Baja (resolver dentro de TECH-011) |

**H-2 y H-3 están formalmente fuera del alcance del bloque de deduplicación.** Se
documentan aquí porque el spike los descubrió, pero **no se implementan en esta rama**;
se propone formalizarlos como historia posterior en el backlog (sugerida: TECH-113
"Corregir filtros `artiId`/`muniId` de `GET /api/sipsa/parcial`", tipo Bug, prioridad
Media, sin dependencia con TECH-010/011/012).

---

## 2.bis Clasificación de los hallazgos y del estado del bloque

**Confirmado por código (verificable en `main` hoy, sin datos externos):**
- `computeKeyHash()` devuelve `UUID.randomUUID()` e ignora sus parámetros (PS-01).
- `batchUpsert()` inserta sin lookup; `skipped` siempre 0; el constraint `key_hash UNIQUE`
  no puede disparar jamás con UUIDs frescos.
- El Javadoc del handler y de la entidad describen una deduplicación inexistente (H-4).
- `UpsertMetrics.skipped` se descarta y no se persiste (H-5).
- El mecanismo de H-1: `parseDate` convierte cualquier error de parseo en `null` silencioso,
  y el XSD permite formatos que `Instant.parse` no acepta (verificado en Java 25).
- H-2 (filtro `artiId` sobre atributo inexistente) y H-3 (filtro `muniId` `Long` vs columna
  `String`) — confirmados por inspección de `SipsaReadService`, la entidad y el DTO.
- Parcial no dispone de ID técnico de DANE (a diferencia de Semanal/Mensual/Abas) — XSD.

**Hipótesis que requieren datos reales (TECH-012) o una respuesta SOAP representativa:**
- Que H-1 esté ocurriendo de verdad (`% enma_fecha NULL` — consulta 10).
- La magnitud real de duplicación acumulada (consultas 1-4, 12).
- Si DANE re-publica la misma clave con valores corregidos (consulta 5).
- La estabilidad de `idArtiSemana` entre corridas (consultas 3-5 + confirmación DANE).
- El formato/hora real de `enmaFecha` (consulta 11) — instante vs fecha.
- Si `futiId` es funcionalmente dependiente de `fuenId`.

**Decisiones de negocio pendientes (ninguna la resuelve este spike):** las 8 preguntas de
§7 — unicidad de la clave, skip vs update, fotografías por corrida, destino de los
duplicados históricos, versión que prevalece, alcance de la API, identidad temporal y
correcciones retroactivas.

---

## 3. Evaluación de la clave natural candidata

Candidata (documentada en ADR-001 y en el Javadoc del handler):
`(muniId, fuenId, futiId, idArtiSemana, enmaFecha)`

| Campo | Significado | Tipo XSD → Java → BD | Nulabilidad real | Estabilidad entre corridas | Normalización necesaria | Riesgo |
|---|---|---|---|---|---|---|
| `muniId` | Código DIVIPOLA del municipio | `xs:string` → `String` → `VARCHAR(50)` | Opcional en XSD; **obligatorio por validación del handler** | Alta (código oficial) | `trim`; **preservar como texto** (ceros a la izquierda); rechazar vacío además de null | Bajo |
| `fuenId` | ID de la fuente/mercado | `xs:decimal` → `Long` → `BIGINT` | Ídem | Alta | Canónico decimal (Long) | Bajo |
| `futiId` | ID del tipo de fuente | `xs:decimal` → `Long` → `BIGINT` | Ídem | Alta | Ídem | Bajo — pero verificar en TECH-012 si es funcionalmente dependiente de `fuenId` (si lo es, sobra en la clave; no daña dejarlo) |
| `idArtiSemana` | ID del artículo (semanal) | `xs:decimal` → `Long` → `BIGINT` | Ídem | **A confirmar**: el nombre sugiere un ID *por semana*; si DANE re-emite el mismo artículo con otro `idArtiSemana` en otra corrida de la misma fecha, la clave se fragmenta. La consulta 5 del script lo revela indirectamente | Ídem | **Medio — la incógnita principal de la clave** |
| `enmaFecha` | Fecha de la encuesta/toma | `xs:dateTime` → texto crudo → `Instant` → `TIMESTAMPTZ` | Opcional en XSD; el handler valida el **texto**, no el parseo (H-1) | Alta si DANE emite siempre el mismo instante para el mismo período; frágil si varía la hora/offset | Parsear con `ISO_OFFSET_DATE_TIME` + fallback documentado; **fallar/rechazar si no parsea** (hoy queda null); normalizar a UTC. Decidir si la identidad es el instante o la fecha en `America/Bogota` (pregunta nº 7) | Medio (formato) |

**Contraste hecho (no solo la documentación):** la candidata coincide con (a) los 5 campos
que el handler ya valida como obligatorios, (b) los campos disponibles en el XSD del
servicio (no existe un ID técnico tipo `tmpMayoSemId` para Parcial — la vía "ID de DANE"
que usan Semanal/Mensual/Abas **no está disponible** aquí), y (c) los índices existentes
(`muni_id`, `enma_fecha`). No hay en el tipo `sipsaPromediosMayoristasParcial` ningún otro
campo con vocación de identidad (los restantes son nombres descriptivos y precios).

**Conclusión técnica:** es la única clave viable con los datos del contrato; su validez
depende de dos confirmaciones que el código no puede dar: unicidad real por observación
(pregunta nº 1) y estabilidad de `idArtiSemana` entre corridas. Ambas salen del diagnóstico
TECH-012 (consultas 3-5) + confirmación de negocio/DANE.

---

## 4. Comparación de estrategias de persistencia

| Criterio | 1. Insert-only + skip | 2. Upsert (update si existe) | 3. Snapshot histórico | 4. Híbrido (canónica + historial) |
|---|---|---|---|---|
| Integridad | ✅ Una fila por clave; primera versión gana | ✅ Una fila por clave; última versión gana | ❌ Duplicación por diseño (necesita `run_id` en la identidad) | ✅ Canónica limpia + historial completo |
| Trazabilidad de correcciones DANE | ❌ Se pierden re-publicaciones | ⚠️ Se pierde el valor anterior (solo queda el último) | ✅ Total | ✅ Total |
| Almacenamiento | Mínimo | Mínimo | Crece por corrida sin límite (hoy: ~619K/día ya observado como riesgo) | Medio (historial solo de cambios) |
| Consultas actuales (`GET /api/sipsa/parcial`) | Sin cambios | Sin cambios | Requiere filtro "última corrida" en TODAS las consultas (cambio de contrato o de servicio) | Sin cambios (la API lee la canónica) |
| Compatibilidad API | ✅ | ✅ | ❌ sin trabajo extra | ✅ |
| Auditoría | `ingestion_runs` + rechazos (ya existe) | Ídem + contar `updated` (columna ya existe) | La corrida ES la auditoría | Historial explícito = mejor auditoría |
| Rendimiento ingesta | 1 SELECT bulk + 1 INSERT por lote (patrón ya probado en `SipsaCiudadRepository.batchUpsert`) | Igual + UPDATEs (más writes, mismo orden de magnitud) | El más rápido (INSERT ciego, es lo actual) | El más caro (doble escritura) |
| Concurrencia | Constraint único como backstop; violación → skip | Ídem; violación → reintento de lookup | Sin conflicto (no hay unicidad) | Constraint en canónica |
| Limpieza de históricos | Consolidar: conservar 1 fila por clave | Ídem (conservar la última) | No aplica (pero exige política de retención) | Migrar duplicados existentes AL historial (no se pierde nada) |
| Complejidad de migración | Baja | Baja | Media (identidad nueva) | Alta (tabla nueva + backfill) |
| Consistencia con el resto del sistema | ✅ **Idéntica a Ciudad/Semanal/Mensual/Abas (skip-first)** | ❌ Sería el único tipo con update | ❌ Único tipo append-only | ❌ Único tipo con dos tablas |

### Recomendación (PROVISIONAL)

**Opción 1 — insert-only + skip por `key_hash` determinista** (la Opción A de ADR-001),
por consistencia con los otros cuatro tipos de datos y mínima superficie de cambio.

> **Esta recomendación es provisional y NO debe adoptarse en ADR-001 hasta que se
> resuelvan las cuatro dependencias siguientes:**
> 1. **Los resultados de TECH-012** (volumen real, duplicación acumulada, NULLs de la clave).
> 2. **La estabilidad de `idArtiSemana` entre corridas** — si no es estable, la clave
>    candidata no deduplica y toda la estrategia cambia.
> 3. **El formato real de `enmaFecha`** (H-1): si la columna está NULL o el instante varía
>    entre corridas del mismo período, el componente temporal de la clave debe redefinirse.
> 4. **La decisión sobre republicaciones/correcciones de DANE** (preguntas nº 2/5/8): si
>    DANE corrige valores y negocio quiere conservar el último (o el historial), la opción
>    correcta pasa a ser la 2 (upsert) o la 4 (híbrida), no la 1.

**Escalar a Opción 2 (upsert)** solo si el diagnóstico (consulta 5) demuestra que DANE
re-publica la misma clave con precios corregidos en proporción relevante **y** negocio
decide que debe prevalecer el último valor (preguntas nº 2/5/8). La Opción 4 solo se
justifica si negocio exige conservar las correcciones históricas (pregunta nº 8 = sí);
la Opción 3 se descarta salvo decisión explícita de negocio en contra de la deduplicación
(equivale a la Opción C de ADR-001, con sus consecuencias ya documentadas).

**Depende de negocio:** elegir entre skip/update/historial (preguntas 2, 3, 5, 8) y el
destino de los duplicados existentes (pregunta 4). **No depende de negocio (técnico):**
hash determinista, normalización, bulk-lookup, constraint, métricas, tests — todo lo de §6.

---

## 5. Diagnóstico de base real (TECH-012)

Script entregado: [`docs/diagnostics/tech-012-sipsa-parcial-diagnostics.sql`](../diagnostics/tech-012-sipsa-parcial-diagnostics.sql)
— 12 secciones, solo `SELECT`, con marcas `[COSTOSA]`, recomendaciones de ejecución
(timeouts de sesión, horario fuera de la ventana de ingestión de las 14:20) y sin exponer
nombres ni precios en los listados. Cubre: volumen, claves únicas, filas y grupos
duplicados, top de repetición, crecimiento por corrida y por día, NULLs de la clave
(incluido el detector del hallazgo H-1), variantes de precio por clave (insumo de las
preguntas 2/5), tamaños de tabla e índices, distribución temporal y estimación del impacto
de consolidar.

**No se ejecuta nada contra producción sin autorización explícita y acceso provisto.**

Salidas que gatillan decisiones:
- `enma_fecha_null > 0` (consulta 10) → H-1 confirmado: fix de parseo obligatorio antes del hash.
- `grupos_con_precios_distintos` alto (consulta 5) → refuerza upsert u opción híbrida (negocio decide).
- `filas_a_eliminar` (consulta 12) → dimensiona la migración de limpieza y si se requiere por lotes.

---

## 6. Estrategia técnica propuesta (para TECH-011, tras ADR-001 aceptado)

**Representación del hash (determinista, versionada, con delimitadores y NULL explícitos):**

```
payload = "v1" + US + norm(muniId) + US + norm(fuenId) + US + norm(futiId)
               + US + norm(idArtiSemana) + US + norm(enmaFecha)
key_hash = hex( SHA-256( payload en UTF-8 ) )        → 64 chars, cabe en VARCHAR(100)
```

- `US` = `` (unit separator): imposible en códigos DIVIPOLA e IDs numéricos; elimina
  ambigüedad sin necesidad de escapado (defensa adicional: rechazar el registro si algún
  campo contiene `US`, imposible en la práctica).
- `norm(String)`: `trim`; rechazar vacío. Sin lower-case (los códigos son numéricos; no
  inventar normalizaciones sin evidencia).
- `norm(Long)`: `Long.toString` (canónico decimal, sin ceros a la izquierda).
- `norm(enmaFecha)`: parsear como `OffsetDateTime` (ISO offset) con fallback a
  `LocalDateTime` sin zona interpretada en `America/Bogota` (decisión a confirmar con la
  evidencia de la consulta 11); convertir a UTC y serializar como epoch millis. Si negocio
  responde que la identidad es la *fecha* (pregunta nº 7), usar `LocalDate` en
  `America/Bogota` como componente — decidirlo en ADR-001, no en el código.
- **Registro con clave imparseable = rechazado** (vía `context.addRejectedRecord`, como los
  null de hoy) — nunca "null silencioso dentro del hash".
- El prefijo `v1` versiona el algoritmo; los UUID legados (36 chars con guiones) son
  estructuralmente distinguibles del nuevo formato (64 hex), útil durante la transición.
- SHA-256 vía `java.security.MessageDigest` (sin dependencias nuevas). Colisión: despreciable.

**Constraint:** conservar `key_hash UNIQUE` como constraint de unicidad efectivo (por fin
significativo). Opcional recomendado en la misma migración: índice compuesto
`(muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha)` para diagnósticos y para la
limpieza — decidir en el PR según el resultado de tamaños de TECH-012.

**Upsert/batch (calcado del patrón probado de `SipsaCiudadRepository.batchUpsert`):**
1. Dedupe intra-batch con `LinkedHashMap<keyHash, entidad>` (última ocurrencia gana).
2. `findByKeyHashIn(keys)` — un solo SELECT indexado por lote (más simple que el `CONCAT`
   de Ciudad: aquí la clave ya es una columna única).
3. Existentes → `skipped++` (o update, según ADR-001); nuevos → `saveAll + flush`.
4. `UpsertMetrics(inserted, skipped)` → el handler pasa a registrar también `skipped`
   en logs y en `IngestionContext` (H-5; sin cambio de esquema: `records_updated` ya
   existe si la decisión fuera update).

**Concurrencia:** el constraint único es el backstop; capturar
`DataIntegrityViolationException` por lote → re-lookup y reclasificar como skipped. Riesgo
real bajo: scheduler single-instance y ventana única diaria (análisis de concurrencia de
`scheduled-ingestion-validation.md` §8 aplica igual).

**Migración Flyway (fase expand–migrate–contract, ADR-009):**

> ⚠️ **Advertencia:** el diseño de la migración destructiva de limpieza descrito abajo es
> un **boceto direccional, no un diseño definitivo**. No debe cerrarse (ni escribirse el
> SQL de producción) hasta conocer el volumen real y los patrones de divergencia que
> arroje TECH-012: la mecánica por lotes, la necesidad de la tabla de archivo, el criterio
> de fila canónica e incluso la viabilidad de un recálculo único de hashes dependen de
> esos números.
- `V2` (expand, no destructiva): índice compuesto de apoyo si se adopta; nada más.
- Limpieza (destructiva) **solo** tras TECH-012 + decisión de negocio (pregunta nº 4), como
  migración propia y con este orden interno: (1) copiar duplicados excedentes a una tabla
  de archivo `sipsa_parcial_archivo` (o exportarlos, según decida negocio), (2) borrar
  conservando la fila canónica por clave (`max(fecha_sincronizacion)`, empate → `max(id)`),
  (3) recalcular `key_hash` determinista para las filas supervivientes (UPDATE una sola vez).
  Ejecutar por lotes si la consulta 12 arroja millones de filas.
- **Rollback:** las migraciones destructivas no se revierten con Flyway (fix-forward,
  ADR-009); prerequisito operativo: backup/snapshot verificado de `sipsa_parcial`
  inmediatamente antes, y la tabla de archivo permite restaurar lógicamente sin backup.
- **Compatibilidad:** filas legadas con UUID conviven con el formato nuevo sin colisión
  posible (36 chars vs 64 hex); mientras no se recalculen, los duplicados legados siguen
  visibles — por eso la limpieza y el recálculo van juntos.

**Pruebas:**
- Unitarias (sin Spring): determinismo del hash (mismos inputs → mismo hash; case/trim;
  independencia del orden de llegada), sensibilidad (cambia un campo → cambia el hash),
  delimitador (ausencia de colisiones tipo `a|bc` vs `ab|c` — cubierto por `US`),
  normalización de fecha (offset, sin zona, imparseable → rechazo), `null` → rechazo.
- Repositorio (H2 como el resto de la suite): dedupe intra-batch, skip de existentes,
  métricas inserted/skipped, violación de constraint reclasificada.
- Testcontainers (patrón `FlywayMigrationsTest`): migración de limpieza contra PostgreSQL
  real con dataset sintético duplicado (verifica archivo + borrado + recálculo + constraint).
- Handler (mock de `SoapGateway` con XML fijo): segunda corrida idéntica → `inserted=0,
  skipped=N` — el criterio de aceptación literal de TECH-011. Sinergia con la mitad
  WireMock de TECH-044 si se quiere elevar a integración.

---

## 7. Preguntas de negocio (bloquean la aceptación de ADR-001)

1. ¿`(muniId, fuenId, futiId, idArtiSemana, enmaFecha)` identifica de forma única una
   observación de precio? (Validar además si `idArtiSemana` es estable entre corridas o
   cambia por semana/corrida.)
2. Cuando DANE vuelve a publicar la misma clave, ¿actualizar o ignorar? (La consulta 5 del
   diagnóstico dirá si esto ocurre y cuánto.)
3. ¿Se necesita fotografía por corrida (historial completo), o basta el estado canónico?
4. Los duplicados históricos existentes: ¿eliminar, consolidar conservando la última
   versión, o conservar archivados en tabla aparte?
5. Si una misma clave tiene versiones con precios distintos, ¿cuál prevalece (primera,
   última por `fecha_sincronizacion`, la de la corrida más reciente)?
6. ¿La API debe exponer solo el estado canónico (contrato actual) o también historial
   (cambio de contrato — nueva historia)?
7. ¿La identidad temporal es el instante exacto (`xs:dateTime` completo) o la fecha en
   `America/Bogota`? (La consulta 11 muestra si llegan horas ≠ medianoche.)
8. ¿Existen correcciones retroactivas de DANE que deban conservarse como versiones? (Si
   sí → opción híbrida; si no → skip/update simple.)

Recomendación tentativa si negocio no tiene preferencia: **1=sí (validar con datos),
2=ignorar (skip), 3=no, 4=consolidar conservando la última + archivo, 5=última,
6=solo canónico, 7=instante tal cual llegue (sin inventar truncamiento), 8=no** —
consistente con los otros cuatro tipos de datos del sistema.

---

## 8. Riesgos del bloque

| Riesgo | Mitigación |
|---|---|
| Ejecutar TECH-011 sin diagnóstico → limpieza mal dimensionada o clave equivocada | Orden obligatorio: TECH-012 + respuestas → ADR-001 `Accepted` → TECH-011 (ya es el orden del roadmap Fase 5) |
| H-1: si `enma_fecha` está NULL en producción, la clave pierde el componente temporal y cualquier hash calculado sobre datos existentes es inválido | Consulta 10 primero; fix de parseo antes del hash; el recálculo de hashes legados usa la columna solo si está poblada |
| `idArtiSemana` inestable entre corridas → la deduplicación nunca matchea (falsos nuevos) | Consultas 3-5 lo evidenciarían (duplicados "invisibles" con 4 de 5 campos iguales); confirmar con DANE |
| Migración destructiva irreversible | Expand–migrate–contract + tabla archivo + backup previo (ADR-009 fix-forward) |
| Corrida en curso durante el deploy del fix | Igual que TECH-111: desplegar fuera de la ventana 14:20–23:59; el upsert nuevo es compatible con filas legadas sin recalcular |
| Doble instancia futura (sin ShedLock) | El constraint único ya actúa de backstop por clave; documentado, sin acción ahora |

---

## 9. Orden recomendado de PRs y commits

1. **PR 0 (este spike, solo documentación):** este informe + script SQL + actualización de
   ADR-001 (enriquecer alternativas con lo aquí analizado, sigue `Proposed`) + nota en el
   backlog (TECH-010 "diagnóstico entregado"). Sin código.
2. **Ejecución TECH-012** (requiere acceso a la base real, autorización explícita):
   correr el script, documentar resultados en el informe, responder con negocio las 8
   preguntas → **ADR-001 pasa a `Accepted` (cierra TECH-081 y TECH-010)** en un commit
   de docs propio.
3. **PR 1 — `fix/parcial-enma-fecha-parsing`** (solo si la consulta 10 confirma H-1, o
   preventivo por su bajo costo): parseo robusto con rechazo explícito + tests. Pequeño e
   independientemente revertible.
4. **PR 2 — `fix/parcial-data-integrity` (TECH-011):** hash determinista + upsert bulk +
   métricas skipped + Javadocs corregidos (H-4/H-5) + tests unitarios y de repositorio.
   Commits sugeridos: `feat(parcial): deterministic key hash`, `feat(parcial): bulk
   skip-first upsert`, `test(parcial): idempotent re-ingestion suite`, `docs(parcial):
   mark TECH-011 done`.
5. **PR 3 — migración de limpieza histórica** (separada, tras decisión de la pregunta 4):
   archivo + consolidación + recálculo de hashes legados + test Testcontainers de la
   migración.
6. **Aparte del bloque:** historia nueva para H-2/H-3 (filtros `artiId`/`muniId` de la API
   Parcial) — bug de API independiente de la deduplicación.
