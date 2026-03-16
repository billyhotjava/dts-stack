{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'admin-data-lake']) }}

{{
  config(
    materialized='table',
    tags=['project-management', 'dim']
  )
}}

SELECT code, label, severity_rank
FROM (VALUES
  ('高', '高风险', 3),
  ('中', '中风险', 2),
  ('低', '低风险', 1)
) AS t(code, label, severity_rank)
