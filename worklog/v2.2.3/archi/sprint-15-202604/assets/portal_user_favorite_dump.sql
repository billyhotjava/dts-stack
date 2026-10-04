-- =============================================================================
-- portal_user_favorite_dump.sql  (data-only placeholder)
--
-- Purpose: restore the row contents of `portal_user_favorite` if the F2 drop
-- changeset is ever rolled back.
-- =============================================================================
-- WARNING -- PLACEHOLDER ONLY
--
-- The local Claude Code environment does NOT have a live database connection,
-- so this file does NOT contain real rows.
--
-- Before production rollout, operations MUST replace this file with:
--
--   pg_dump -h <prod-host> -U <prod-user> -d <prod-db> \
--       -t portal_user_favorite \
--       --data-only --column-inserts \
--       -f worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_dump.sql
--
-- and refresh `assets/dump.checksum` accordingly (see assets/README.md).
-- Retention: keep this file (and its companion `portal_user_favorite_full.sql`)
-- for a minimum of 90 days post-release.
-- =============================================================================

SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;

-- Intentionally no INSERT statements in this placeholder.
