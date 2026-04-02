{{ config(materialized='table', tags=['project-management', 'biz', 'dws', 'progress']) }}

SELECT
  d.project_no,
  d.subsystem,

  COUNT(*)                                                                      AS total_measure_cnt,
  SUM(CASE WHEN d.closure_status = '已闭环' THEN 1 ELSE 0 END)                 AS closed_cnt,
  SUM(CASE WHEN d.closure_status IS NULL
            OR d.closure_status != '已闭环' THEN 1 ELSE 0 END)                 AS open_cnt,
  CASE WHEN COUNT(*) = 0 THEN 0
       ELSE ROUND(
         SUM(CASE WHEN d.closure_status = '已闭环' THEN 1 ELSE 0 END)::numeric
         / COUNT(*)::numeric, 4)
  END                                                                           AS closure_rate,

  -- 按 measure_category 分类计数
  SUM(CASE WHEN d.measure_category = '设计' THEN 1 ELSE 0 END)                 AS cat_design_cnt,
  SUM(CASE WHEN d.measure_category = '工艺' THEN 1 ELSE 0 END)                 AS cat_process_cnt,
  SUM(CASE WHEN d.measure_category = '管理' THEN 1 ELSE 0 END)                 AS cat_management_cnt,
  SUM(CASE WHEN d.measure_category = '元器件' THEN 1 ELSE 0 END)               AS cat_component_cnt,
  SUM(CASE WHEN d.measure_category = '操作' THEN 1 ELSE 0 END)                 AS cat_operation_cnt,
  SUM(CASE WHEN d.measure_category = '外协' THEN 1 ELSE 0 END)                 AS cat_outsource_cnt,
  SUM(CASE WHEN d.measure_category = '软件' THEN 1 ELSE 0 END)                 AS cat_software_cnt,
  SUM(CASE WHEN d.measure_category NOT IN ('设计','工艺','管理','元器件','操作','外协','软件')
            OR d.measure_category IS NULL THEN 1 ELSE 0 END)                    AS cat_other_cnt

FROM {{ ref('biz_dwd_progress_measure') }} d
WHERE d.project_no IS NOT NULL
GROUP BY d.project_no, d.subsystem
