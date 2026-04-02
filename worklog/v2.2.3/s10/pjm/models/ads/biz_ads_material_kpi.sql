{{ config(materialized='table', tags=['project-management', 'biz', 'ads', 'kpi', 'material']) }}

WITH dws_agg AS (
  SELECT
    project_no,
    SUM(total_cnt)           AS total_cnt,
    SUM(long_cycle_cnt)      AS long_cycle_cnt,
    SUM(self_developed_cnt)  AS self_developed_cnt,
    SUM(outsource_cnt)       AS outsource_cnt,
    SUM(has_risk_cnt)        AS has_risk_cnt
  FROM {{ ref('biz_dws_material_period_summary') }}
  GROUP BY project_no
),

-- 供应商去重：直接从 DWD 层按项目 COUNT(DISTINCT)，避免跨子系统重复
supplier_agg AS (
  SELECT
    project_no,
    COUNT(DISTINCT supplier_name) FILTER (WHERE supplier_name IS NOT NULL) AS supplier_count
  FROM {{ ref('biz_dwd_material_info') }}
  GROUP BY project_no
)

SELECT
  d.project_no,
  d.total_cnt,
  d.long_cycle_cnt,
  CASE WHEN d.total_cnt = 0 THEN 0
       ELSE ROUND(d.long_cycle_cnt::numeric / d.total_cnt::numeric, 4)
  END AS long_cycle_rate,
  d.self_developed_cnt,
  d.outsource_cnt,
  CASE WHEN d.total_cnt = 0 THEN 0
       ELSE ROUND(d.outsource_cnt::numeric / d.total_cnt::numeric, 4)
  END AS outsource_rate,
  d.has_risk_cnt,
  COALESCE(s.supplier_count, 0) AS supplier_count

FROM dws_agg d
LEFT JOIN supplier_agg s ON s.project_no = d.project_no
ORDER BY d.project_no
