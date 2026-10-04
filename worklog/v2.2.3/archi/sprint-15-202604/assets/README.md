# Sprint-15 / F2 rollback assets

This directory contains the rollback materials required by the Liquibase
changeset `20260424-1000_drop-portal-user-favorite.xml`, which drops the legacy
`portal_user_favorite` table as part of Sprint-15 F2.

## Files

| File                              | Purpose                                                       |
| --------------------------------- | ------------------------------------------------------------- |
| `portal_user_favorite_full.sql`   | DDL + (placeholder for) data, used to recreate the full table |
| `portal_user_favorite_dump.sql`   | Data-only dump, used to restore rows after a DDL recreate     |
| `dump.checksum`                   | SHA-256 checksums of both `.sql` files                        |

Both `.sql` files are **placeholders** committed from the Sprint-15 development
environment (which has no live DB connection). Production operations must
replace them with real dumps before go-live.

## Pre-release runbook (operations)

1. Regenerate the dumps against the production database (or staging mirror, as
   the release plan requires):

   ```bash
   pg_dump -h "$PROD_HOST" -U "$PROD_USER" -d "$PROD_DB" \
       -t portal_user_favorite \
       -f worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_full.sql

   pg_dump -h "$PROD_HOST" -U "$PROD_USER" -d "$PROD_DB" \
       -t portal_user_favorite \
       --data-only --column-inserts \
       -f worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_dump.sql
   ```

2. Refresh the checksum file:

   ```bash
   cd worklog/v2.2.3/sprint-15-202604
   sha256sum assets/portal_user_favorite_*.sql > assets/dump.checksum
   ```

3. Verify checksums before the drop runs:

   ```bash
   cd worklog/v2.2.3/sprint-15-202604
   sha256sum -c assets/dump.checksum
   ```

4. Run `./mvnw -pl source/dts-platform liquibase:update -Pprod` (or the
   environment equivalent) to apply the drop changeset.

## Rollback runbook (operations)

The Liquibase rollback block is intentionally a placeholder (`SELECT 1`) to
avoid automated data-restoration surprises. If a rollback is required:

1. Mark the changeset rolled back in Liquibase:

   ```bash
   ./mvnw -pl source/dts-platform \
       liquibase:rollbackCount -Dliquibase.rollbackCount=1 -Pprod
   ```

2. Manually recreate the table + rows:

   ```bash
   psql -h "$PROD_HOST" -U "$PROD_USER" -d "$PROD_DB" \
       -f worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_full.sql
   psql -h "$PROD_HOST" -U "$PROD_USER" -d "$PROD_DB" \
       -f worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_dump.sql
   ```

3. If only the full dump is available (schema + data), step 2's second command
   can be skipped.

## Retention

Operations must retain the real (non-placeholder) dumps for **at least 90
days** after the drop changeset reaches production. After that window the
files may be archived to cold storage per the standard DB-backup policy.
