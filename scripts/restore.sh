#!/usr/bin/env bash
# StudyDeck — PostgreSQL restore from a logical backup produced by backup.sh.
#
# DESTRUCTIVE: drops and recreates the target schema before restoring. Requires
# the backend to be stopped (or no active connections) to avoid lock contention.
#
# Usage:
#   ./scripts/restore.sh path/to/studydeck-<stamp>.dump
#
# Env (defaults match compose.yaml):
#   PG_CONTAINER=studydeck-postgres
#   DB_NAME=studydeck
#   DB_USER=studydeck
set -euo pipefail

DUMP_FILE="${1:?Usage: restore.sh <dump-file>}"
PG_CONTAINER="${PG_CONTAINER:-studydeck-postgres}"
DB_NAME="${DB_NAME:-studydeck}"
DB_USER="${DB_USER:-studydeck}"

if [ ! -f "$DUMP_FILE" ]; then
  echo "Dump file not found: $DUMP_FILE" >&2
  exit 1
fi

echo "WARNING: this will overwrite the 'public' schema in database '${DB_NAME}'."
read -r -p "Type 'yes' to continue: " confirm
[ "$confirm" = "yes" ] || { echo "Aborted."; exit 1; }

echo "Recreating schema ..."
docker exec -i "$PG_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" \
  -c "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"

echo "Restoring from ${DUMP_FILE} ..."
docker exec -i "$PG_CONTAINER" pg_restore -U "$DB_USER" -d "$DB_NAME" --no-owner --no-privileges \
  < "$DUMP_FILE"

echo "Restore complete. Restart the backend so Flyway can validate the schema:"
echo "  docker compose up -d backend"
