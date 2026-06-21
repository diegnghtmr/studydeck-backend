#!/usr/bin/env bash
# StudyDeck — PostgreSQL logical backup.
#
# Runs pg_dump inside the running postgres container and writes a compressed,
# custom-format dump to the host. Custom format (-Fc) supports selective restore
# and parallelism.
#
# Usage:
#   ./scripts/backup.sh [output_dir]
#
# Env (defaults match compose.yaml):
#   PG_CONTAINER=studydeck-postgres
#   DB_NAME=studydeck
#   DB_USER=studydeck
set -euo pipefail

PG_CONTAINER="${PG_CONTAINER:-studydeck-postgres}"
DB_NAME="${DB_NAME:-studydeck}"
DB_USER="${DB_USER:-studydeck}"
OUT_DIR="${1:-./backups}"

mkdir -p "$OUT_DIR"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_FILE="$OUT_DIR/studydeck-${STAMP}.dump"

echo "Backing up database '${DB_NAME}' from container '${PG_CONTAINER}' ..."
docker exec "$PG_CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc --no-owner --no-privileges \
  > "$OUT_FILE"

echo "Backup written to: $OUT_FILE ($(du -h "$OUT_FILE" | cut -f1))"
echo "Verify with: pg_restore --list \"$OUT_FILE\" | head"
