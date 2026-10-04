{{ config(materialized='table', tags=['project-management', 'biz', 'ads', 'kpi', 'material']) }}

-- 物料域 KPI 汇总
-- 粒度: 全局汇总（单行）+ 按项目汇总

WITH project_level AS (
  SELECT
    project_no,
    SUM(total_material_cnt)   AS total_material_cnt,
    SUM(long_cycle_cnt)       AS long_cycle_cnt,
    SUM(self_developed_cnt)   AS self_developed_cnt,
    SUM(outsource_cnt)        AS outsource_cnt,
    SUM(has_risk_cnt)         AS has_risk_cnt,
    SUM(supplier_count)       AS supplier_count,
    MIN(earliest_delivery)    AS earliest_delivery,
    MAX(latest_delivery)      AS latest_delivery
  FROM {{ ref('biz_dws_material_period_summary') }}
  GROUP BY project_no
)

SELECT
  project_no,
  total_material_cnt,
  long_cycle_cnt,
  CASE
    WHEN total_material_cnt = 0 THEN NULL
    ELSE ROUND(long_cycle_cnt::numeric / total_material_cnt, 4)
  END AS long_cycle_rate,

  self_developed_cnt,
  outsource_cnt,
  CASE
    WHEN total_material_cnt = 0 THEN NULL
    ELSE ROUND(outsource_cnt::numeric / total_material_cnt, 4)
  END AS outsource_rate,

  has_risk_cnt,
  CASE
    WHEN total_material_cnt = 0 THEN NULL
    ELSE ROUND(has_risk_cnt::numeric / total_material_cnt, 4)
  END AS risk_rate,

  supplier_count,
  earliest_delivery,
  latest_delivery

FROM project_level
ORDER BY project_no
