{{ config(materialized='table', tags=['project-management', 'biz', 'dws', 'material']) }}

-- 重要物料周期汇总表
-- 粒度: (project_no, subsystem)

WITH base AS (
  SELECT * FROM {{ ref('biz_dwd_material_info') }}
)

SELECT
  project_no,
  subsystem,

  -- === 总量 ===
  COUNT(*) AS total_material_cnt,

  -- === 长周期物料 ===
  COUNT(*) FILTER (WHERE is_long_cycle) AS long_cycle_cnt,
  ROUND(
    COUNT(*) FILTER (WHERE is_long_cycle)::numeric / NULLIF(COUNT(*), 0), 4
  ) AS long_cycle_rate,

  -- === 自研/外协 ===
  COUNT(*) FILTER (WHERE is_self_developed) AS self_developed_cnt,
  COUNT(*) FILTER (WHERE NOT is_self_developed) AS outsource_cnt,
  ROUND(
    COUNT(*) FILTER (WHERE NOT is_self_developed)::numeric / NULLIF(COUNT(*), 0), 4
  ) AS outsource_rate,

  -- === 有风险描述的物料 ===
  COUNT(*) FILTER (WHERE risk_name IS NOT NULL AND risk_name != '') AS has_risk_cnt,
  ROUND(
    COUNT(*) FILTER (WHERE risk_name IS NOT NULL AND risk_name != '')::numeric / NULLIF(COUNT(*), 0), 4
  ) AS risk_rate,

  -- === 供应商统计 ===
  COUNT(DISTINCT supplier_name) FILTER (WHERE supplier_name IS NOT NULL) AS supplier_count,

  -- === 交期统计 ===
  MIN(delivery_date) AS earliest_delivery,
  MAX(delivery_date) AS latest_delivery

FROM base
GROUP BY project_no, subsystem
