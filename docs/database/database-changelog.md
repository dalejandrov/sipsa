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

### V1 — initial_schema

> **Nota (2026-07-28):** el sistema aún no ha salido a producción y no hay ambientes
> compartidos ni desarrolladores externos con el proyecto clonado. Aprovechando esa
> ventana, las antiguas `V2`–`V5` (índice de clave natural, eliminación de índice
> redundante, índice cubriente de artículo, retipado de fechas a `DATE`) se
> **consolidaron dentro de `V1__initial_schema.sql`** en lugar de mantenerse como
> migraciones incrementales separadas — no hay datos ni historial de
> `flyway_schema_history` reales que proteger. Esta entrada documenta el estado final
> resultante; el detalle de *por qué* cada índice tiene la forma que tiene permanece en
> el propio archivo SQL (comentarios por tabla) y en los documentos de diagnóstico
> enlazados abajo. A partir de aquí, la regla de inmutabilidad de ADR-009 (regla 2)
> aplica de nuevo con normalidad: toda migración nueva es `V2` en adelante.

| Campo | Valor |
|---|---|
| Archivo | `V1__initial_schema.sql` |
| Fecha | Esquema fundacional; consolidación de V2–V5 el 2026-07-28 |
| Historia relacionada | TECH-011/TECH-012 (índice de clave natural), TECH-119 (eliminación de índice redundante), TECH-124 (índice cubriente de artículo), TECH-104 (retipado de fechas) |
| Propósito | Esquema completo del servicio en su estado actual |
| Cambios de esquema | 8 tablas: `ingestion_runs`, `ingestion_audit`, `ingestion_rejects`, `sipsa_ciudad`, `sipsa_parcial`, `sipsa_mayoristas_semanal`, `sipsa_mayoristas_mensual`, `sipsa_abastecimientos_mensual` |
| Cambios de datos | Ninguno |
| Impacto en índices | Índices de consulta por tabla (fechas, claves de negocio, `ingestion_run_id`); en `sipsa_parcial`: `idx_sipsa_parcial_fecha`, `idx_sipsa_parcial_muni`, `idx_sipsa_parcial_ingestion_run`, `idx_sipsa_parcial_natural_key` (TECH-011), `idx_sipsa_parcial_article_date` — cubriente `(id_arti_semana, enma_fecha DESC) INCLUDE (id)` (TECH-124). Sin índice explícito duplicado sobre `key_hash` (TECH-119) |
| Impacto en constraints | `uq_ingestion_runs_window (method_name, window_key)`; `ux_ciudad`; `ux_semana_tmp`/`ux_semana_fallback`; `ux_mes_*`; `ux_abas_*`; `sipsa_parcial.key_hash UNIQUE`, efectivo por el hash determinista (ADR-001) |
| Compatibilidad hacia atrás | n/a (aún no hay producción ni ambientes compartidos) |
| Requiere downtime | n/a |
| Estrategia ante fallo | n/a (aplicada en cada arranque desde cero) |
| Riesgos | Ninguno activo; el retipado de fechas a `DATE` (antes `V5`, TECH-104) documentó un riesgo de invalidación de `key_hash` que solo aplicaría si existieran datos reales pre-migración — no es el caso |
| Evidencia de validación | `FlywayMigrationsTest` (Testcontainers, PostgreSQL 18) en cada `./mvnw clean verify` y en CI; detalle de diseño de los índices en [tech-124-article-filter-analysis.md](../diagnostics/tech-124-article-filter-analysis.md) y [tech-012-runbook.md](../diagnostics/tech-012-runbook.md) |
| Ambientes aplicados | dev / docker / CI (por diseño en cada arranque); staging / prod: aún no existen (TECH-130..132 pendientes) |

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
