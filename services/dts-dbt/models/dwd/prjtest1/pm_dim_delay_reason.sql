{{ config(materialized='table', tags=['project-management', 'dim', 'project-cockpit', 'dwd']) }}

SELECT
  NULLIF(btrim(delay_reason_category), '') AS delay_reason_category,
  NULLIF(btrim(delay_reason_label), '') AS delay_reason_label,
  NULLIF(btrim(description), '') AS description
FROM {{ ref('pm_dim_delay_reason_seed') }}
