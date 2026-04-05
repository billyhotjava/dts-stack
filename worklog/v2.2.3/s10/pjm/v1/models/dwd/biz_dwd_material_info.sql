{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'material']) }}

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.pbs_no), '') || '|' ||
    COALESCE(btrim(o.supplier_name), '')
  ) AS material_id,

  -- === 原始业务字段 ===
  {{ nullif_placeholder("o.project_no") }}              AS project_no,
  {{ nullif_placeholder("o.subsystem") }}               AS subsystem,
  {{ nullif_placeholder("o.pbs_no") }}                  AS pbs_no,
  {{ nullif_placeholder("o.pbs_name") }}                AS pbs_name,
  {{ nullif_placeholder("o.self_or_outsource") }}       AS self_or_outsource,
  {{ nullif_placeholder("o.supplier_name") }}           AS supplier_name,
  {{ nullif_placeholder("o.is_long_cycle") }}           AS is_long_cycle_raw,
  {{ nullif_placeholder("o.dept_owner") }}              AS dept_owner,
  {{ nullif_placeholder("o.control_dept_owner") }}      AS control_dept_owner,
  {{ nullif_placeholder("o.weekly_progress") }}         AS weekly_progress,
  {{ nullif_placeholder("o.affects_major_node") }}      AS affects_major_node,
  {{ nullif_placeholder("o.risk_level") }}              AS risk_level,
  {{ nullif_placeholder("o.risk_content") }}            AS risk_content,
  {{ nullif_placeholder("o.delay_impact") }}            AS delay_impact,
  {{ nullif_placeholder("o.remark") }}                  AS remark,

  -- === 周数字段 ===
  {{ nullif_placeholder("o.contract_negotiation_week") }} AS contract_negotiation_week,
  {{ nullif_placeholder("o.contract_delivery_week") }}    AS contract_delivery_week,
  {{ nullif_placeholder("o.actual_delivery_week") }}      AS actual_delivery_week,
  {{ nullif_placeholder("o.plan_inspect_week") }}         AS plan_inspect_week,
  {{ nullif_placeholder("o.complete_inspect_week") }}     AS complete_inspect_week,
  {{ nullif_placeholder("o.install_week") }}              AS install_week,
  {{ nullif_placeholder("o.last_update_week") }}          AS last_update_week,

  -- === 布尔派生字段 ===
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.is_long_cycle") }}, '')) = '是' THEN true
    ELSE false
  END AS is_long_cycle,

  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.self_or_outsource") }}, '')) = '自研' THEN true
    ELSE false
  END AS is_self_developed,

  -- === 风险等级标准化 ===
  CASE COALESCE({{ nullif_placeholder("o.risk_level") }}, '')
    WHEN '高' THEN 3
    WHEN '中' THEN 2
    WHEN '低' THEN 1
    ELSE 0
  END AS risk_rank,

  -- === 日期解析 ===
  {{ parse_date_safe("o.contract_negotiation_date") }}  AS contract_negotiation_date,
  {{ parse_date_safe("o.contract_delivery_date") }}     AS contract_delivery_date,
  {{ parse_date_safe("o.actual_delivery_date") }}       AS actual_delivery_date,
  {{ parse_date_safe("o.plan_inspect_date") }}          AS plan_inspect_date,
  {{ parse_date_safe("o.complete_inspect_date") }}      AS complete_inspect_date,
  {{ parse_date_safe("o.install_date") }}               AS install_date,
  {{ parse_date_safe("o.last_update_time") }}           AS last_update_time,

  -- === 交付延迟天数 ===
  CASE
    WHEN {{ parse_date_safe("o.contract_delivery_date") }} IS NOT NULL
     AND {{ parse_date_safe("o.actual_delivery_date") }} IS NOT NULL
    THEN ({{ parse_date_safe("o.actual_delivery_date") }} - {{ parse_date_safe("o.contract_delivery_date") }})::int
    ELSE NULL
  END AS delivery_delay_days,

  'ods_material_info'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods', 'material_info') }} o
WHERE o.project_no IS NOT NULL
