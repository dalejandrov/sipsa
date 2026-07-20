# ADR-005 — Scheduler Execution Model

**Status:** Accepted (2026-07-20, refined — see Resolution)  
**Date:** 2026-07-13  
**Backlog:** [TECH-053](../backlog/technical-backlog.md#tech-053)

---

## Context

The SIPSA ingestion scheduler (`SipsaIngestionScheduler`) is a Spring `@Scheduled` component
that triggers three cron-based ingestion windows:

- Daily at 14:20 COT: Ciudad, Parcial, Semana (sequential).
- Day 8 at 14:30 COT: MesMadr.
- Day 10 at 14:30 COT: AbasMes.

The scheduling thread pool is configured with 5 threads (`sipsa.scheduling.pool-size: 5`).

---

## Problem

**Current behavior (synchronous):**

```java
// SipsaIngestionScheduler.java:124-135
private void runSafely(String methodName) {
    ingestionJob.execute(request);  // blocks until complete
}
```

`runDailyWindow()` calls `runSafely()` three times sequentially. The three ingestion jobs
(Ciudad, Parcial, Semana) run one after another in the same scheduler thread.

**Consequence:** During the daily window, one of the 5 scheduler threads is occupied for the
entire duration of three sequential ingestion runs. For Parcial (619K+ records), this can
take tens of minutes to over an hour.

**Risk level:** Low-to-medium for a single-instance deployment. The other 4 scheduler threads
remain available for other cron tasks. The main risk is if the same `@Scheduled` method is
triggered again while the previous run is still in the same thread — Spring's default behavior
is to skip the next invocation if the previous one has not completed in the same thread.

---

## Alternatives

### Option A — Dispatch to async executor (Recommended)

```java
private void runSafely(String methodName) {
    asyncIngestionService.executeAsync(request);  // returns immediately
}
```

The scheduler thread returns immediately after dispatch. The ingestion runs in the
`ingestionTaskExecutor` pool.

**Pros:** Scheduler thread never blocks for long; simpler reasoning about scheduler capacity.  
**Cons:**
1. If `ingestionTaskExecutor` queue is full (`queue-capacity: 25`), the job submission is rejected by `CallerRunsPolicy` — the scheduler thread will block after all. (Can be mitigated by increasing queue capacity or using `AbortPolicy` with explicit error handling.)
2. The scheduler cannot directly detect if the ingestion job is still running from the previous cron invocation. This is already handled by the `isRunComplete()` check in `IngestionJob.execute()` — a duplicate window will be skipped.
3. If the JVM is shut down gracefully, async jobs in the executor queue may not complete.

### Option B — Keep synchronous execution (current)

Accept that one scheduler thread is blocked during ingestion. Document as a known limitation.

**Pros:** Simple; the scheduler is aware if the job is still running; no risk of double-dispatch.  
**Cons:** Scheduler thread blocked for long periods; potential scheduling delays for other tasks if thread pool is saturated.

### Option C — Separate thread pool per ingestion method

Create a dedicated single-thread executor for the daily window, separate from the general `ingestionTaskExecutor`.

**Pros:** Complete isolation between scheduling and ingestion concerns.  
**Cons:** Additional configuration complexity; more thread pools to manage.

---

## Decision

**Not yet decided.** This ADR is `Proposed`.

Recommendation: **Option A**, with the following safeguards:
1. Increase `sipsa.ingestion.async.queue-capacity` from 25 to a value that accommodates all expected concurrent ingestion jobs (at most 5: 3 daily + 2 monthly).
2. Document that if the executor is saturated, the `CallerRunsPolicy` will cause the scheduler thread to block — the same behavior as Option B but less predictable.
3. Validate that `isRunComplete()` correctly prevents duplicate window processing when the scheduler fires again before the previous run has finished.

---

## Consequences

**If Option A is accepted:**
- `SipsaIngestionScheduler` depends on `AsyncIngestionService` instead of `GenericIngestionJob`.
- Scheduler threads are never blocked by ingestion work.
- The `ingestionTaskExecutor` pool size must be sufficient for all concurrent ingestion methods.
- Graceful shutdown (`await-termination: true`) ensures in-progress ingestions complete before JVM exit.

**If Option B is accepted:**
- No code change.
- Document the 1-thread-per-window limitation in this ADR and in operational runbooks.

---

---

## Resolution (2026-07-20, TECH-053)

**Option A was accepted, refined.** The literal Option A sketch above —
`asyncIngestionService.executeAsync(request)` called once per method — was not used
as-is: dispatching each of the daily window's three methods as an *independent*
`@Async` call would let them race each other on `ingestionTaskExecutor` (core pool
size 2, so genuinely concurrent), silently breaking the sequential,
resource-contention-avoiding execution this same document's Problem section
describes as the current, intentional behavior ("the three ingestion jobs run one
after another"). This gap wasn't discussed in the Alternatives section above.

**Actual design:** a new `ScheduledIngestionDispatcher` (not `AsyncIngestionService`,
which stays dedicated to the manual-trigger, one-request-per-dispatch path — its
existing purpose and callers are unchanged) exposes one `@Async("ingestionTaskExecutor")`
method **per scheduled window**, not per method:
- `dispatchDailyWindow()` — runs Ciudad → Parcial → Semana sequentially, inside one
  async call, on one worker thread. One dispatch per cron trigger, not three.
- `dispatchMonthlyMes()` / `dispatchMonthlyAbas()` — one method each, so the
  per-method and per-window granularity coincide; no sequencing concern there.

This is still exactly Option A's core trade-off — the scheduler thread returns
immediately, ingestion runs on the managed pool — with the window-level grouping
added specifically to preserve the pre-existing sequential-execution property.
Verified in Docker (a real, cron-fired daily window): the scheduler's `@Scheduled`
method returns essentially instantly, and all three methods then run in order on a
single `ingestion-async-*` thread.

**Safeguards from the original recommendation:**
1. *Queue capacity increase* — **not applied.** At most one window-level task is ever
   queued per cron trigger (3 became 1 concurrent task, not 3, precisely because of
   the window-level grouping above) — the concern this safeguard was written for
   (3 daily + 2 monthly concurrent submissions) does not arise with this design.
   Changing `queue-capacity` was also out of this story's explicit scope.
2. *`CallerRunsPolicy` saturation documented* — done, in
   `ScheduledIngestionDispatcher`'s own Javadoc: under sustained saturation the
   scheduler thread can still end up blocked (accepted, existing backpressure
   behavior, not eliminated — this ADR's "non-blocking" claim was never
   unconditional and TECH-053 does not claim otherwise).
3. *`isRunComplete()` / duplicate-window prevention validated* — confirmed unchanged:
   the real `uq_ingestion_runs_window UNIQUE (method_name, window_key)` constraint,
   translated by `IngestionControlService.createRun` into a `SipsaBusinessException`
   that `IngestionJob.execute` already treats as a controlled skip, is exercised
   identically regardless of which thread calls `execute()`. Regression-tested in
   `ScheduledIngestionDispatcherTest`.

**Consequences realized:** `SipsaIngestionScheduler` now depends on
`ScheduledIngestionDispatcher`, not `GenericIngestionJob`, at all. Scheduler threads
are not blocked in the normal (non-saturated) case. Graceful shutdown
(`await-termination: true`) is unchanged — not touched by this story.
