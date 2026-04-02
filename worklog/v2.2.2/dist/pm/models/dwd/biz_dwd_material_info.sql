{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'material']) }}

-- 重要物料信息 DWD 事实表
-- 清洗 ODS 层原始数据，标准化枚举值，解析日期

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.pbs_no), '') || '|' ||
    COALESCE(btrim(o.risk_name), '')
  ) AS material_id,

  -- === 业务字段 ===
  {{ nullif_placeholder("o.project_no") }}        AS project_no,
  {{ nullif_placeholder("o.subsystem") }}          AS subsystem,
  {{ nullif_placeholder("o.pbs_no") }}             AS pbs_no,
  {{ nullif_placeholder("o.pbs_name") }}           AS pbs_name,
  {{ nullif_placeholder("o.risk_name") }}          AS risk_name,
  {{ nullif_placeholder("o.risk_description") }}   AS risk_description,

  -- 自研/外协 标准化
  CASE
    WHEN btrim(COALESCE(o.self_or_outsource, '')) IN ('自研', '自制') THEN '自研'
    WHEN btrim(COALESCE(o.self_or_outsource, '')) IN ('外协', '外购', '外协外购') THEN '外协'
    ELSE {{ nullif_placeholder("o.self_or_outsource") }}
  END AS self_or_outsource,

  -- 是否自研
  CASE
    WHEN btrim(COALESCE(o.self_or_outsource, '')) IN ('自研', '自制') THEN TRUE
    ELSE FALSE
  END AS is_self_developed,

  {{ nullif_placeholder("o.supplier_name") }}      AS supplier_name,

  -- 长周期物料标志
  CASE
    WHEN btrim(COALESCE(o.is_long_cycle, '')) IN ('是', 'Y', 'yes', '1') THEN TRUE
    ELSE FALSE
  END AS is_long_cycle,

  {{ nullif_placeholder("o.belonging_unit") }}     AS belonging_unit,

  -- 日期解析
  {{ parse_date_safe("o.delivery_date") }}         AS delivery_date,
  {{ parse_date_safe("o.last_update_time") }}      AS last_update_time,
  {{ parse_numeric_safe("o.last_update_week") }}::int AS last_update_week,

  {{ nullif_placeholder("o.filled_by") }}          AS filled_by,
  {{ nullif_placeholder("o.remark") }}             AS remark,

  -- === 派生字段 ===
  CASE
    WHEN {{ parse_date_safe("o.delivery_date") }} IS NOT NULL
    THEN EXTRACT(YEAR FROM {{ parse_date_safe("o.delivery_date") }})::int
  END AS delivery_year,

  CASE
    WHEN {{ parse_date_safe("o.delivery_date") }} IS NOT NULL
    THEN to_char({{ parse_date_safe("o.delivery_date") }}, 'YYYY-MM')
  END AS delivery_month

FROM {{ source('pm_ods', 'material_info') }} o
WHERE {{ nullif_placeholder("o.project_no") }} IS NOT NULL
