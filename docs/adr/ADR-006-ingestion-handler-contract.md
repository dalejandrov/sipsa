# ADR-006 — Ingestion Handler Contract

**Status:** Proposed  
**Date:** 2026-07-13  
**Backlog:** [TECH-055](../backlog/technical-backlog.md#tech-055)

---

## Context

`IngestionHandler` is the Strategy interface for all ingestion data sources. It currently declares:

```java
public interface IngestionHandler {
    String getMethodName();
    void execute(IngestionContext context) throws Exception;
}
```

`WindowPolicy` classifies handlers as daily or monthly based on the handler's method name string:

```java
// WindowPolicy.java:195-197
private boolean isMonthlyMethod(String methodName) {
    return methodName.toLowerCase().contains("mesmadr")
        || methodName.toLowerCase().contains("abas");
}
```

---

## Problem

The scheduling type (daily vs monthly) is encoded in the method name string, not in the handler
contract. Adding a new monthly handler with a method name that does not contain "mesmadr" or "abas"
(e.g., `promediosSipsaMensualCiudad`) would silently use daily scheduling, potentially running
at incorrect times without any compile-time or startup-time warning.

---

## Alternatives

### Option A — Add `isMonthly()` to `IngestionHandler` interface

```java
public interface IngestionHandler {
    String getMethodName();
    boolean isMonthly();         // ← new method
    void execute(IngestionContext context) throws Exception;
}
```

`WindowPolicy.isMonthlyMethod()` is replaced by `handler.isMonthly()` looked up from the registry.

**Pros:** Contract is explicit; compiler enforces all handlers to declare their type; zero guessing from method names.  
**Cons:** All 5 existing handlers must add the method; interface grows; if `IngestionHandler` is implemented in tests or other contexts, they also need the method.

### Option B — Document the naming convention (current approach)

Add Javadoc to `IngestionHandler.getMethodName()` stating that monthly methods must include "mesmadr" or "abas" in their name.

**Pros:** Zero code change.  
**Cons:** Convention is not enforced; easy to violate; discoverable only by reading Javadoc; relies on developer discipline.

### Option C — Explicit registry in configuration

Move the daily/monthly classification to `application.yaml`:

```yaml
sipsa:
  scheduling:
    monthly-methods:
      - promediosSipsaMesMadr
      - promedioAbasSipsaMesMadr
```

**Pros:** Configuration-driven; no interface change; operational team can modify without code changes.  
**Cons:** Application configuration and code must stay in sync; risk of misconfiguration if a handler is added without updating the config.

---

## Decision

**Not yet decided.** This ADR is `Proposed` pending TECH-055 (SPIKE).

Tentative recommendation: **Option A**. The `isMonthly()` method is a low-cost change that makes the contract explicit and eliminates a class of silent bugs. The 5 existing handlers must each implement `default boolean isMonthly() { return false; }` (for daily) or override for monthly handlers.

---

## Consequences

**If Option A is accepted:**
- `IngestionHandler` gains `isMonthly()`.
- `WindowPolicy.isMonthlyMethod(String)` is replaced by a lookup: `ingestionService.getHandler(methodName).isMonthly()`.
- All 5 handlers are updated (3 daily return `false`, 2 monthly return `true`).
- Future handlers declare their scheduling type explicitly.

**If Option B is accepted:**
- Javadoc is updated on `getMethodName()`.
- A comment is added to `WindowPolicy.isMonthlyMethod()` explaining the convention.
- No enforcement.

---

*Update this ADR to `Accepted` after TECH-055 is resolved.*
