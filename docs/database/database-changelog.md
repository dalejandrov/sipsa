# Database Changelog — SIPSA Integration Service

**Creado:** 2026-07-16
**Reglas:** [ADR-009 — Database Migration Strategy](../adr/ADR-009-database-migration-strategy.md)
**Guía de trabajo:** [docs/development/database-migrations.md](../development/database-migrations.md)

Este documento es el registro **operativo y auditable** de las migraciones de base de
datos: intención, impacto, riesgos y evidencia de cada una. No sustituye a ninguna otra
fuente — cada una tiene un rol fijo:

| Fuente | Rol |
|---|---|
| `flyway_schema_history` (tabla, por ambiente) | **Evidencia técnica** de qué migración corrió realmente en cada base, con checksum y resultado |
| `docs/database/database-changelog.md` (este archivo) | **Intención, impacto y operación** de cada migración: por qué existe, qué riesgo tenía, cómo se validó, dónde se aplicó |
| `CHANGELOG.md` (raíz) | Resumen de cambios **entregados de la aplicación**; menciona la migración, no la detalla |
| [ADR-009](../adr/ADR-009-database-migration-strategy.md) | **Reglas** vinculantes (inmutabilidad, orden, fix-forward, expand–migrate–contract) |
| [`technical-backlog.md`](../backlog/technical-backlog.md) | **Estado de las historias** que motivan cada migración |

Regla práctica: el detalle vive solo aquí; los demás documentos enlazan.

---

## Formato de entrada

Cada migración añade una entrada con estos campos:

```markdown
### V{N} — {descripción}
| Campo | Valor |
|---|---|
| Archivo | V{N}__{descripcion}.sql |
| Fecha (merge a main) | |
| Historia relacionada | TECH-… |
| Propósito | |
| Cambios de esquema | |
| Cambios de datos | |
| Impacto en índices | |
| Impacto en constraints | |
| Compatibilidad hacia atrás | |
| Requiere downtime | |
| Estrategia ante fallo | fix-forward (detallar) |
| Riesgos | |
| Evidencia de validación | FlywayMigrationsTest / staging / otros |
| Ambientes aplicados | dev / docker / CI / staging / prod (con fecha) |
```

---

## Registro

### V1 — initial_schema  *(entrada retroactiva)*

> **Nota:** esta entrada es **documentación histórica** de una migración ya aplicada en
> todos los ambientes. No es una migración nueva ni una modificación del archivo aplicado
> (`V1__initial_schema.sql` es inmutable — ADR-009 regla 2).

| Campo | Valor |
|---|---|
| Archivo | `V1__initial_schema.sql` (10.7 KB) |
| Fecha | Presente desde el inicio del historial del repositorio (esquema fundacional) |
| Historia relacionada | — (anterior al backlog); endurecimiento posterior por ADR-009 (2026-07-14) |
| Propósito | Esquema completo inicial del servicio |
| Cambios de esquema | 8 tablas: `ingestion_runs`, `ingestion_audit`, `ingestion_rejects`, `sipsa_ciudad`, `sipsa_parcial`, `sipsa_mayoristas_semanal`, `sipsa_mayoristas_mensual`, `sipsa_abastecimientos_mensual` |
| Cambios de datos | Ninguno |
| Impacto en índices | Índices de consulta por tabla (fechas, claves de negocio, `ingestion_run_id`); en `sipsa_parcial`: `idx_sipsa_parcial_fecha`, `idx_sipsa_parcial_muni`, `idx_sipsa_parcial_ingestion_run`, `idx_sipsa_parcial_key_hash` |
| Impacto en constraints | `uq_ingestion_runs_window (method_name, window_key)`; `ux_ciudad`; `ux_semana_tmp`/`ux_semana_fallback`; `ux_mes_*`; `ux_abas_*`; `sipsa_parcial.key_hash UNIQUE` (inefectivo hoy por el UUID aleatorio — PS-01, ver SPIKE) |
| Compatibilidad hacia atrás | n/a (fundacional) |
| Requiere downtime | n/a |
| Estrategia ante fallo | n/a (aplicada) |
| Riesgos | El `key_hash UNIQUE` de `sipsa_parcial` transmite una garantía de deduplicación que no existe (documentado en el [SPIKE](../architecture/sipsa-parcial-data-integrity-spike.md)) |
| Evidencia de validación | `FlywayMigrationsTest` (Testcontainers, PostgreSQL 18) en cada `./mvnw clean verify` y en CI (TECH-120) |
| Ambientes aplicados | dev / docker / CI (por diseño en cada arranque); staging / prod: **pendiente de inventario** (ver §Baseline) |

### V2 — add_parcial_natural_key_index

| Campo | Valor |
|---|---|
| Archivo | `V2__add_parcial_natural_key_index.sql` |
| Fecha (merge a main) | 2026-07-16 (rama `fix/sipsa-parcial-data-integrity`) |
| Historia relacionada | TECH-011 (fase expand — corresponde a la "E1" conceptual del runbook) |
| Propósito | Índice compuesto de soporte sobre la clave natural de `sipsa_parcial`, confirmada por TECH-012 contra datos reales de DANE |
| Cambios de esquema | `CREATE INDEX idx_sipsa_parcial_natural_key ON sipsa_parcial (muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha)` |
| Cambios de datos | **Ninguno** (expand-only; sin limpieza, sin backfill, sin NOT NULL, sin constraint único natural) |
| Impacto en índices | +1 índice no-único; sin `CONCURRENTLY` (justificación en el propio script: no hay tráfico productivo que proteger) |
| Impacto en constraints | Ninguno (el `key_hash UNIQUE` existente pasa a ser efectivo por el hash determinista, sin cambio de DDL) |
| Compatibilidad hacia atrás | Total — la versión anterior de la aplicación opera igual con el índice presente |
| Requiere downtime | No |
| Estrategia ante fallo | Fix-forward (índice sin datos; re-creación en `V3` si fuera necesario) |
| Riesgos | Bajo — índice adicional sobre tabla que se reconstruyó localmente desde cero |
| Evidencia de validación | `FlywayMigrationsTest` (cadena completa desde base vacía + existencia del índice, PostgreSQL 18 real); `ParcialMigrationUpgradeTest` (upgrade V1→V2 sobre datos duplicados legados, verifica que no toca datos); ciclo Docker Compose completo con 3 ingestas reales de DANE |
| Ambientes aplicados | dev/docker/CI (por diseño en cada arranque/build); staging/producción: **no existen aún** (TECH-130..132 pendientes) |

### V3 — drop_redundant_parcial_key_hash_index

| Campo | Valor |
|---|---|
| Archivo | `V3__drop_redundant_parcial_key_hash_index.sql` |
| Fecha (merge a main) | 2026-07-16 (rama `fix/remove-redundant-parcial-key-hash-index`) |
| Historia relacionada | TECH-119 |
| Propósito | Eliminar `idx_sipsa_parcial_key_hash` (V1 creó dos índices B-tree idénticos sobre `key_hash`: el explícito no-único y el implícito del constraint `UNIQUE`) |
| Cambios de esquema | `DROP INDEX idx_sipsa_parcial_key_hash`. **Se conserva** `sipsa_parcial_key_hash_key` (índice de respaldo del constraint `UNIQUE (key_hash)`, que queda intacto y activo) |
| Cambios de datos | **Ninguno** — 676.210 filas verificadas idénticas antes/después en la base local |
| Impacto en índices | −80 MB en la base local de 676K filas (254 MB → 174 MB de índices totales); menor amplificación de escritura en cada inserción |
| Impacto en constraints | Ninguno — la unicidad de `key_hash` sigue vigente (verificado con inserción duplicada → unique violation post-V3) |
| Compatibilidad hacia atrás | Total — el planner usa `sipsa_parcial_key_hash_key` para los lookups por hash con costo idéntico (EXPLAIN antes/después) |
| Requiere downtime | No en los ambientes actuales (sin tráfico productivo; TECH-130..132 pendientes). `DROP INDEX` transaccional toma un lock ACCESS EXCLUSIVE breve — ejecutado en **8 ms** sobre la base local de 676K filas. Si un ambiente futuro con tráfico concurrente y tabla grande necesitara esta migración, aplicarla en una ventana breve (o re-evaluar `CONCURRENTLY` con `executeInTransaction=false`, no necesario hoy) |
| Estrategia ante fallo | Migración **transaccional**: PostgreSQL revierte el DROP si falla → reintento tras diagnóstico o fix-forward `V4`. Sin estados parciales posibles |
| Riesgos | Mínimos — el índice eliminado no respaldaba ningún constraint y ninguna consulta/test/script referencia su nombre (verificado por grep en todo el repo) |
| Evidencia de validación | `FlywayMigrationsTest` (cadena V1→V2→V3 desde base vacía + ausencia del índice + constraint presente); `ParcialKeyHashIndexMigrationTest` (upgrade V2→V3 con datos: filas y hashes preservados, plan usa el índice único, duplicado rechazado); upgrade en vivo sobre la base local real de 676.210 filas; re-ingestión completa idempotente posterior (dedupe TECH-011 operando solo con el índice único) |
| Ambientes aplicados | dev/docker/CI (por diseño en cada arranque/build); staging/producción: **no existen aún** |

*(Las próximas migraciones de la transición — constraint natural definitivo y fase
contract, "E2/E3" del runbook — añadirán aquí su entrada con numeración asignada desde
`main` justo antes de implementar.)*

---

## Anexo operativo

### Numeración de migraciones futuras

Los documentos de planificación usan nombres conceptuales (**E1 expand, E2 constraint,
E3 contract**), nunca `V2/V3/V4` fijos: entre un diagnóstico y su implementación pueden
fusionarse otras migraciones. La numeración `V{N}` definitiva se asigna desde `main`
actualizado inmediatamente antes de implementar (ADR-009 regla 3: orden estricto,
renumerar en rebase).

### `IF NOT EXISTS` — política

Prohibido por defecto en migraciones versionadas: se ejecutan una vez y, si el estado
real no coincide con el esperado, la migración **debe fallar** y revelar el drift — no
ocultarlo ni dejar pasar una estructura creada con otra definición. Uso admisible solo
con justificación operacional explícita escrita en la propia migración y en su entrada de
este changelog.

### Migraciones no transaccionales (`CREATE INDEX CONCURRENTLY` y similares)

- `CREATE INDEX CONCURRENTLY` no puede ejecutarse dentro de una transacción. Se configura
  con un script config file `V{N}__desc.sql.conf` conteniendo `executeInTransaction=false`
  (verificar además, al implementar, si la versión de Flyway del BOM soporta la directiva
  inline `-- flyway:executeInTransaction=false`; no asumirlo).
- **Interrupción:** puede dejar un índice `INVALID` (no se usa en planes pero cobra
  mantenimiento) además del fallo registrado. Detección:
  `SELECT indexrelid::regclass FROM pg_index WHERE NOT indisvalid;`
- **Reparación:** procedimiento controlado y documentado — `DROP INDEX CONCURRENTLY` del
  índice inválido, reconciliación del historial (ver §Fallos) y recreación vía nueva
  migración o re-ejecución autorizada. Nunca se deja "para después" sin registrarlo aquí.

### Procedimiento ante fallos de Flyway

El comportamiento visible en `flyway_schema_history` tras un fallo **puede variar** según
versión, edición, configuración y tipo de ejecución de Flyway — no se asume ninguna forma
concreta de fila fallida. Procedimiento en orden, para cualquier fallo:

1. Inspeccionar `flyway_schema_history` (¿qué quedó registrado, con qué `success`?).
2. Inspeccionar el **estado real del esquema** contra lo que la migración debía crear.
3. Determinar si hubo rollback completo (migración transaccional revertida por
   PostgreSQL) o **estado parcial** (migración no transaccional: objetos a medias,
   índices `INVALID`).
4. Revisar los logs de Flyway del arranque fallido.
5. **No ejecutar `flyway repair` automáticamente.** `repair` solo con revisión y
   autorización, cuando el estado de la base ya fue reconciliado, y registrándolo aquí.
6. Reconciliar el estado (limpiar restos parciales con procedimiento documentado).
7. Aplicar **fix-forward** cuando se necesite corrección: nueva migración `V{N+1}`, nunca
   editar la aplicada.

**Checksum mismatch:** jamás usar `repair` para legitimar la edición de una migración
aplicada — la edición se revierte en git (ADR-009 regla 2). `clean` está deshabilitado y
no es un mecanismo de recuperación en ningún caso.

### Baseline (`baseline-on-migrate`)

La configuración actual (`baseline-on-migrate: true`, `baseline-version: 1`) existe para
adoptar Flyway sobre bases que preceden al historial. Antes de desactivarla se requiere
un **inventario por ambiente** (dev local, Docker, staging, producción) con las consultas
del [runbook, Parte I §8](../diagnostics/tech-012-runbook.md): existencia de
`flyway_schema_history`, migraciones aplicadas con checksum y resultado, presencia de
fila `type='BASELINE'`, y detección de esquemas no vacíos sin historial. Solo con los
ambientes verificados se crea una **historia separada** para `baseline-on-migrate: false`
— nunca mezclada con TECH-011.

### Validación exigida a toda migración nueva

- `./mvnw clean verify` — incluye `FlywayMigrationsTest` (cadena completa desde base
  vacía contra PostgreSQL real; en CI el gate es no-omitible).
- `./mvnw test -Dtest=FlywayMigrationsTest` — ciclo corto durante el desarrollo.
- `docker compose up --build` — arranque completo con la cadena aplicada, healthcheck `UP`.
- Para migraciones sobre datos existentes: tests Testcontainers adicionales con fixtures
  representativos (con y sin duplicados) y, antes de producción, ensayo en staging sobre
  copia real con duración medida.
- La evidencia de cada validación se registra en la entrada correspondiente de este
  changelog.
