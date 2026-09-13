-- =============================================================================
-- portal_user_favorite_full.sql
--
-- Source: reconstructed from the JPA entity
--   source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/portal/PortalUserFavorite.java
-- Purpose: rollback material for the Liquibase changeset
--   20260424-1000_drop-portal-user-favorite.xml
-- =============================================================================
-- WARNING -- PLACEHOLDER ONLY
--
-- This file is a SCHEMA TEMPLATE. It does NOT contain production data.
--
-- Before production rollout, operations MUST replace this file with the
-- output of:
--
--   pg_dump -h <prod-host> -U <prod-user> -d <prod-db> \
--       -t portal_user_favorite \
--       -f worklog/v2.2.3/sprint-15-202604/assets/portal_user_favorite_full.sql
--
-- and refresh `assets/dump.checksum` accordingly (see assets/README.md).
-- =============================================================================

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET client_min_messages = warning;

BEGIN;

CREATE TABLE IF NOT EXISTS portal_user_favorite (
    id              uuid         NOT NULL,
    user_login      varchar(50)  NOT NULL,
    title           varchar(160) NOT NULL,
    target_type     varchar(32),
    target_id       varchar(64),
    link            varchar(512),
    metadata_json   text,
    sort_order      integer,
    enabled         boolean      NOT NULL DEFAULT TRUE,
    -- AbstractAuditingEntity columns (mirrors the rest of the platform schema)
    created_by      varchar(50),
    created_date    timestamp,
    last_modified_by   varchar(50),
    last_modified_date timestamp,
    CONSTRAINT pk_portal_user_favorite PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS ix_portal_user_favorite_user_login
    ON portal_user_favorite (user_login);

-- =============================================================================
-- Data section
-- =============================================================================
-- Replace with real INSERT statements from `pg_dump --data-only --column-inserts`.
-- Leave intentionally empty in this placeholder; operators are responsible for
-- restoring production rows if a rollback is ever required.
-- =============================================================================

COMMIT;
