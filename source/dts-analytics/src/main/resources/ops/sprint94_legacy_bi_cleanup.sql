-- Sprint-94 legacy BI contract cleanup.
--
-- Durable boundary: analytics_screen, its versions/access/templates/audits/assets,
-- and all menu/role bindings are never cleanup targets. Any historical screen
-- payload that still names a legacy card blocks the complete operation.
--
-- Before apply:
--   1. Stop legacy Card/Dashboard writers and capture the last legacy ids.
--   2. Back up the complete analytics database with pg_dump and verify the dump.
--   3. Run this script once with apply=false and archive the output.
--   4. Apply only in the separate contract-release window.
--
-- Dry run:
--   psql -v legacy_card_max_id=123 -v legacy_dashboard_max_id=45 \
--     -v apply=false -f sprint94_legacy_bi_cleanup.sql
-- Apply after a verified backup:
--   psql -v legacy_card_max_id=123 -v legacy_dashboard_max_id=45 \
--     -v backup_confirmed=true -v apply=true -f sprint94_legacy_bi_cleanup.sql

\set ON_ERROR_STOP on

\if :{?apply}
\else
\set apply false
\endif

\if :{?backup_confirmed}
\else
\set backup_confirmed false
\endif

\if :{?legacy_card_max_id}
\else
\echo 'legacy_card_max_id is required; capture it before governed BI writes begin'
\quit 2
\endif

\if :{?legacy_dashboard_max_id}
\else
\echo 'legacy_dashboard_max_id is required; capture it before governed BI writes begin'
\quit 2
\endif

SELECT :'legacy_card_max_id' ~ '^[0-9]+$'
   AND :'legacy_dashboard_max_id' ~ '^[0-9]+$' AS valid_cutoffs
\gset

\if :valid_cutoffs
\else
\echo 'legacy BI cleanup cutoffs must be non-negative integer ids'
\quit 2
\endif

BEGIN;

CREATE TEMP TABLE sprint94_legacy_card_target ON COMMIT DROP AS
SELECT id
  FROM analytics_card
 WHERE id <= :'legacy_card_max_id'::bigint;

CREATE TEMP TABLE sprint94_legacy_dashboard_target ON COMMIT DROP AS
SELECT id
  FROM analytics_dashboard
 WHERE id <= :'legacy_dashboard_max_id'::bigint;

WITH historical_screen_payload AS (
    SELECT 'analytics_screen' AS source_table,
           id AS source_id,
           concat_ws(' ', components_json, variables_json, pages_json, carousel_json, v2_spec_json) AS payload
      FROM analytics_screen
    UNION ALL
    SELECT 'analytics_screen_version',
           id,
           concat_ws(' ', components_json, variables_json, pages_json, carousel_json, v2_spec_json)
      FROM analytics_screen_version
    UNION ALL
    SELECT 'analytics_screen_template',
           id,
           concat_ws(' ', components_json, variables_json, theme_pack_json)
      FROM analytics_screen_template
    UNION ALL
    SELECT 'analytics_screen_template_version', id, snapshot_json
      FROM analytics_screen_template_version
), referenced_payload AS (
    SELECT source_table, source_id
      FROM historical_screen_payload
     WHERE payload ~* '"(cardId|card_id|sourceCardId|source_card_id)"[[:space:]]*:'
)
SELECT EXISTS (SELECT 1 FROM referenced_payload) AS historical_screen_has_card_reference,
       COUNT(*) AS historical_screen_reference_rows
  FROM referenced_payload
\gset

\if :historical_screen_has_card_reference
\echo 'legacy BI cleanup blocked: historical screen or template payload still references a legacy card'
ROLLBACK;
\quit 4
\endif

SELECT 'legacy_cards' AS target, COUNT(*) AS row_count
  FROM sprint94_legacy_card_target
UNION ALL
SELECT 'legacy_dashboards', COUNT(*)
  FROM sprint94_legacy_dashboard_target
UNION ALL
SELECT 'screen_rows_preserved', COUNT(*)
  FROM analytics_screen
UNION ALL
SELECT 'screen_version_rows_preserved', COUNT(*)
  FROM analytics_screen_version
ORDER BY target;

\if :apply
    \if :backup_confirmed
    \else
        \echo 'legacy BI cleanup blocked: set backup_confirmed=true only after a verified pg_dump'
        ROLLBACK;
        \quit 5
    \endif

    DELETE FROM analytics_alert_subscription
     WHERE alert_id IN (
         SELECT id
           FROM analytics_alert
          WHERE card_id IN (SELECT id FROM sprint94_legacy_card_target)
     );

    DELETE FROM analytics_alert
     WHERE card_id IN (SELECT id FROM sprint94_legacy_card_target);

    DELETE FROM analytics_dashboard_card
     WHERE card_id IN (SELECT id FROM sprint94_legacy_card_target)
        OR dashboard_id IN (SELECT id FROM sprint94_legacy_dashboard_target);

    DELETE FROM analytics_query_trace
     WHERE card_id IN (SELECT id FROM sprint94_legacy_card_target);

    DELETE FROM analytics_public_link
     WHERE (lower(model) = 'card' AND model_id IN (SELECT id FROM sprint94_legacy_card_target))
        OR (lower(model) = 'dashboard' AND model_id IN (SELECT id FROM sprint94_legacy_dashboard_target));

    DELETE FROM analytics_bookmark
     WHERE (lower(model) = 'card' AND model_id IN (SELECT id FROM sprint94_legacy_card_target))
        OR (lower(model) = 'dashboard' AND model_id IN (SELECT id FROM sprint94_legacy_dashboard_target));

    DELETE FROM analytics_activity
     WHERE (lower(model) = 'card' AND model_id IN (SELECT id FROM sprint94_legacy_card_target))
        OR (lower(model) = 'dashboard' AND model_id IN (SELECT id FROM sprint94_legacy_dashboard_target));

    DELETE FROM analytics_revision
     WHERE (lower(model) = 'card' AND model_id IN (SELECT id FROM sprint94_legacy_card_target))
        OR (lower(model) = 'dashboard' AND model_id IN (SELECT id FROM sprint94_legacy_dashboard_target));

    DELETE FROM analytics_report_registration_outbox
     WHERE (lower(aggregate_type) IN ('card', 'analysis') AND aggregate_id IN (SELECT id FROM sprint94_legacy_card_target))
        OR (lower(aggregate_type) = 'dashboard' AND aggregate_id IN (SELECT id FROM sprint94_legacy_dashboard_target));

    -- Pulse scheduling belongs to the retired Metabase-style BI contract.
    DELETE FROM analytics_pulse_subscription;
    DELETE FROM analytics_pulse;

    DELETE FROM analytics_card
     WHERE id IN (SELECT id FROM sprint94_legacy_card_target);

    DELETE FROM analytics_dashboard
     WHERE id IN (SELECT id FROM sprint94_legacy_dashboard_target);

    COMMIT;
    \echo 'legacy BI cleanup committed; retain the verified database backup through the rollback window'
\else
    \echo 'dry run only: no legacy BI rows were removed'
    ROLLBACK;
\endif
