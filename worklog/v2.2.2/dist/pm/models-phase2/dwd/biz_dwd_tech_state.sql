{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'tech-state']) }}

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.tech_state_name), '') || '|' ||
    COALESCE(btrim(o.change_item), '')
  ) AS tech_state_id,

  -- === 原始业务字段 ===
  {{ nullif_placeholder("o.project_no") }}              AS project_no,
  {{ nullif_placeholder("o.tech_state_name") }}         AS tech_state_name,
  {{ nullif_placeholder("o.change_item") }}             AS change_item,
  {{ nullif_placeholder("o.file_signature_status") }}   AS file_signature_status,
  {{ nullif_placeholder("o.completion_signature") }}    AS completion_signature,
  {{ nullif_placeholder("o.reform_status") }}           AS reform_status,
  {{ nullif_placeholder("o.closure_status") }}          AS closure_status,
  {{ nullif_placeholder("o.dept") }}                    AS dept,
  {{ nullif_placeholder("o.subsystem") }}               AS subsystem,
  {{ nullif_placeholder("o.filled_by") }}               AS filled_by,
  {{ nullif_placeholder("o.remark") }}                  AS remark,

  -- === 更改类别标准化 ===
  -- 原始值可能是 I/II/III 或 Ⅰ/Ⅱ/Ⅲ，统一为罗马数字
  CASE
    WHEN upper(btrim(COALESCE(o.change_category, ''))) IN ('I', 'Ⅰ', '1') THEN 'I'
    WHEN upper(btrim(COALESCE(o.change_category, ''))) IN ('II', 'Ⅱ', '2') THEN 'II'
    WHEN upper(btrim(COALESCE(o.change_category, ''))) IN ('III', 'Ⅲ', '3') THEN 'III'
    ELSE {{ nullif_placeholder("o.change_category") }}
  END AS change_category,

  COALESCE(cc.severity_rank, 0) AS change_severity_rank,

  -- === 签署状态标准化 ===
  COALESCE(ss.is_submitted, false) AS is_submitted,
  COALESCE(ss.is_reviewed, false)  AS is_reviewed,
  COALESCE(ss.is_signed, false)    AS is_signed,

  -- === 闭环标志 ===
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.closure_status") }}, '')) = '已闭环' THEN true
    ELSE false
  END AS is_closed,

  -- === 完成签署标志 ===
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.completion_signature") }}, '')) = '是' THEN true
    ELSE false
  END AS is_signature_completed,

  -- === 整改落实标志 ===
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.reform_status") }}, '')) = '已落实整改' THEN true
    WHEN btrim(COALESCE({{ nullif_placeholder("o.reform_status") }}, '')) = '不涉及整改' THEN true
    ELSE false
  END AS is_reform_done,
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.reform_status") }}, '')) = '不涉及整改' THEN true
    ELSE false
  END AS is_reform_not_applicable,

  -- === 日期解析 ===
  {{ parse_date_safe("o.change_submit_time") }}    AS change_submit_date,
  {{ parse_date_safe("o.last_update_time") }}      AS last_update_time,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM {{ parse_date_safe("o.change_submit_time") }})::int     AS submit_year,
  EXTRACT(QUARTER FROM {{ parse_date_safe("o.change_submit_time") }})::int  AS submit_quarter,
  to_char({{ parse_date_safe("o.change_submit_time") }}, 'YYYY-MM')         AS submit_month,

  'ods_tech_state'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods', 'tech_state') }} o
LEFT JOIN {{ ref('dim_change_category') }} cc
  ON cc.code = CASE
    WHEN upper(btrim(COALESCE(o.change_category, ''))) IN ('I', 'Ⅰ', '1') THEN 'I'
    WHEN upper(btrim(COALESCE(o.change_category, ''))) IN ('II', 'Ⅱ', '2') THEN 'II'
    WHEN upper(btrim(COALESCE(o.change_category, ''))) IN ('III', 'Ⅲ', '3') THEN 'III'
    ELSE {{ nullif_placeholder("o.change_category") }}
  END
LEFT JOIN {{ ref('dim_signature_status') }} ss
  ON ss.code = {{ nullif_placeholder("o.file_signature_status") }}
WHERE btrim(COALESCE(o.project_no, '')) != ''
