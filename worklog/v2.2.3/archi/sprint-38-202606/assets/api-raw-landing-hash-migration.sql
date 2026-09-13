-- API raw landing hash migration template.
-- Replace {{target_table}} with one concrete raw landing table, for example public.ods_api_crm_orders.
-- Run one table at a time after validating duplicate rows and backup policy.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE {{target_table}}
    ADD COLUMN IF NOT EXISTS _dts_record_hash TEXT;

UPDATE {{target_table}}
SET _dts_record_hash = encode(digest(_dts_raw_record::text, 'sha256'), 'hex')
WHERE _dts_record_hash IS NULL;

WITH ranked AS (
    SELECT
        id,
        row_number() OVER (
            PARTITION BY _dts_source_resource, _dts_record_hash
            ORDER BY _dts_import_time DESC, id DESC
        ) AS rn
    FROM {{target_table}}
    WHERE _dts_record_hash IS NOT NULL
)
DELETE FROM {{target_table}} t
USING ranked r
WHERE t.id = r.id
  AND r.rn > 1;

ALTER TABLE {{target_table}}
    ALTER COLUMN _dts_record_hash SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS {{target_table_hash_index}}
    ON {{target_table}} (_dts_source_resource, _dts_record_hash);
