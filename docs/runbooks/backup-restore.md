# Runbook: Backup & Restore (PostgreSQL)

Operational procedure for backing up and restoring the StudyDeck database. The
database is the single source of truth (decks, notes, cards, scheduling state,
documents, embeddings, audit log). The application schema is owned by Flyway.

## Prerequisites

- The `studydeck-postgres` container is running (`docker compose up -d postgres`).
- `docker` access on the host.

## Backup

```bash
./scripts/backup.sh                # writes ./backups/studydeck-<UTC-stamp>.dump
./scripts/backup.sh /mnt/backups   # custom output directory
```

Produces a compressed custom-format dump (`pg_dump -Fc`). Verify integrity:

```bash
pg_restore --list ./backups/studydeck-<stamp>.dump | head
```

### Scheduling

Run via cron on the host (example: daily at 02:30, keep 14 days):

```cron
30 2 * * *  cd /opt/studydeck && ./scripts/backup.sh /var/backups/studydeck && \
            find /var/backups/studydeck -name 'studydeck-*.dump' -mtime +14 -delete
```

Ship dumps off-host (object storage) for disaster recovery — a backup on the
same host as the database is not a backup.

## Restore

> **DESTRUCTIVE.** Recreates the `public` schema before restoring. Stop the
> backend first so there are no active connections and Flyway does not race.

```bash
docker compose stop backend
./scripts/restore.sh ./backups/studydeck-<stamp>.dump   # prompts for confirmation
docker compose up -d backend
```

On startup Flyway runs `validate` against the restored schema; a mismatch fails
fast. If the dump predates a migration, restore then let Flyway migrate forward.

## Point-in-time recovery (PITR)

The logical dumps above cover most needs. For RPO ≈ 0, enable WAL archiving on a
managed Postgres or a `wal-g`/`pgBackRest` sidecar — out of scope for the local
compose stack.

## Verification drill (do this periodically)

1. Take a backup from production-like data.
2. Restore into a throwaway database/container.
3. Boot the backend against it and run the smoke checks.
4. Confirm row counts for `deck`, `note`, `card`, `source_document`.

An untested backup is a hypothesis, not a recovery plan.
