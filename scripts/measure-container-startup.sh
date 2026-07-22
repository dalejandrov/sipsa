#!/usr/bin/env bash
# TECH-144: measures container startup time (container start -> /actuator/health
# 200) and peak/idle memory footprint under given CPU/memory limits, against
# the existing local docker-compose db/oidc services. Used to produce
# evidence-based ECS task cpu/memory and health_check_grace_period_seconds
# proposals instead of carrying forward unvalidated defaults. Never touches
# AWS, never uses any AWS credential — purely local Docker.
#
# Prerequisites: Docker with the `compose` plugin (docker compose, not the
# standalone docker-compose v1 binary), curl, and this repository's own
# Dockerfile/docker-compose.yml (run from the repository root). No other
# tooling required.
#
# Usage: ./scripts/measure-container-startup.sh [cpus] [memory] [samples]
#   e.g. ./scripts/measure-container-startup.sh 0.25 1024m 3
#
# Exit code: non-zero if ANY sample fails to reach /actuator/health 200
# within the per-sample timeout — this script's success/failure signal is
# the health probe, never a fixed sleep duration.

set -euo pipefail

CPUS="${1:-0.25}"
MEMORY="${2:-512m}"
SAMPLES="${3:-3}"
PER_SAMPLE_TIMEOUT_SECONDS="${PER_SAMPLE_TIMEOUT_SECONDS:-300}"
IMAGE="sipsa-app"
NETWORK="sipsa_sipsa-network"
COMPOSE_PROJECT="sipsa"

CONTAINER_NAME=""
FAILURES=0
declare -a RESULTS

cleanup() {
  [ -n "$CONTAINER_NAME" ] && docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  docker compose -p "$COMPOSE_PROJECT" down >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Building image..."
docker build -t "$IMAGE" -f Dockerfile . >/dev/null

echo "Starting db/oidc dependencies..."
docker compose -p "$COMPOSE_PROJECT" up -d db oidc >/dev/null

echo "Waiting for db healthy..."
for _ in $(seq 1 30); do
  db_status=$(docker inspect -f '{{.State.Health.Status}}' sipsa-db 2>/dev/null || echo "")
  [ "$db_status" = "healthy" ] && break
  sleep 2
done
if [ "$db_status" != "healthy" ]; then
  echo "db never became healthy — aborting." >&2
  exit 1
fi

for i in $(seq 1 "$SAMPLES"); do
  CONTAINER_NAME="sipsa-app-capacity-test-$i"
  PORT=$((18080 + i))
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true

  START=$(date +%s)
  docker run -d --name "$CONTAINER_NAME" \
    --network "$NETWORK" \
    --cpus="$CPUS" --memory="$MEMORY" --memory-swap="$MEMORY" \
    -p "${PORT}:8080" \
    -e SPRING_PROFILES_ACTIVE=docker \
    -e DB_HOST=db -e DB_PORT=5432 -e DB_NAME=sipsa_db \
    -e DB_USERNAME=sipsa_user -e DB_PASSWORD=sipsa_pass \
    -e SIPSA_JWT_ISSUER_URI=http://oidc:9000/default \
    "$IMAGE" >/dev/null

  DEADLINE=$((START + PER_SAMPLE_TIMEOUT_SECONDS))
  code="000"
  peak_mem_pct="0.00"
  peak_mem_raw=""
  while [ "$(date +%s)" -lt "$DEADLINE" ]; do
    # Sampled every ~3s (the same interval as the health poll below), not a
    # continuous monitor — a true instantaneous peak could be marginally
    # higher; OOMKilled (checked below) is the authoritative signal for
    # whether the limit was ever actually exceeded.
    stat_line=$(docker stats --no-stream --format '{{.MemPerc}}|{{.MemUsage}}' "$CONTAINER_NAME" 2>/dev/null || echo "")
    if [ -n "$stat_line" ]; then
      cur_pct="${stat_line%%|*}"
      cur_pct="${cur_pct%\%}"
      cur_raw="${stat_line#*|}"
      if awk -v a="$cur_pct" -v b="$peak_mem_pct" 'BEGIN{exit !(a>b)}'; then
        peak_mem_pct="$cur_pct"
        peak_mem_raw="$cur_raw"
      fi
    fi

    code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${PORT}/actuator/health" 2>/dev/null || echo "000")
    if [ "$code" = "200" ]; then
      NOW=$(date +%s)
      ELAPSED=$((NOW - START))
      OOM=$(docker inspect "$CONTAINER_NAME" --format '{{.State.OOMKilled}}')
      EXIT_CODE=$(docker inspect "$CONTAINER_NAME" --format '{{.State.ExitCode}}')
      echo "Sample $i: healthy after ${ELAPSED}s, peak memory ${peak_mem_raw} (${peak_mem_pct}%), OOMKilled=${OOM}, ExitCode=${EXIT_CODE}"
      RESULTS+=("$ELAPSED")
      break
    fi
    sleep 3
  done

  if [ "$code" != "200" ]; then
    echo "Sample $i: FAILED to become healthy within ${PER_SAMPLE_TIMEOUT_SECONDS}s (final code: $code)" >&2
    docker logs "$CONTAINER_NAME" 2>&1 | tail -30
    FAILURES=$((FAILURES + 1))
  fi

  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1
  CONTAINER_NAME=""
done

echo ""
echo "Results (seconds to healthy): ${RESULTS[*]:-none}"
echo "Failures: $FAILURES / $SAMPLES"

if [ "$FAILURES" -gt 0 ]; then
  exit 1
fi
