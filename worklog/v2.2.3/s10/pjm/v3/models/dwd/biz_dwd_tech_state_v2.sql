{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dwd', 'tech-state']) }}

WITH normalized AS (
  SELECT
    o.*,
    -- 标准化更改类别（输出 I/II/III）
    CASE
      WHEN upper(btrim(COALESCE(o.change_category, ''))) IN ('I', 'Ⅰ', 'I类', '1', '一') THEN 'I'
      WHEN upper(btrim(COALESCE(o.change_category, ''))) IN ('II', 'Ⅱ', 'II类', '2', '二') THEN 'II'
      WHEN upper(btrim(COALESCE(o.change_category, ''))) IN ('III', 'Ⅲ', 'III类', '3', '三') THEN 'III'
      ELSE {{ nullif_placeholder("o.change_category") }}
    END AS change_category_code
  FROM {{ source('pm_ods_v2', 'tech_state_v2') }} o
  WHERE o.project_no IS NOT NULL
    AND btrim(COALESCE(o.project_no, '')) != ''
)

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(n.project_no), '') || '|' ||
    COALESCE(btrim(n.tech_state_name), '') || '|' ||
    COALESCE(btrim(n.change_item), '')
  ) AS tech_state_id,

  -- === 原始业务字段 ===
  {{ nullif_placeholder("n.project_no") }}               AS project_no,
  {{ nullif_placeholder("n.tech_state_name") }}          AS tech_state_name,
  {{ nullif_placeholder("n.change_item") }}              AS change_item,
  {{ nullif_placeholder("n.owner") }}                    AS owner,
  {{ nullif_placeholder("n.dept") }}                     AS dept,
  {{ nullif_placeholder("n.dept_leader") }}              AS dept_leader,
  {{ nullif_placeholder("n.completion_signature") }}     AS completion_signature,
  {{ nullif_placeholder("n.change_reason") }}            AS change_reason,

  -- 标准化更改类别 + 字典布尔
  n.change_category_code                                 AS change_category,
  COALESCE(cc.is_cat_i,   false)                         AS is_cat_i,
  COALESCE(cc.is_cat_ii,  false)                         AS is_cat_ii,
  COALESCE(cc.is_cat_iii, false)                         AS is_cat_iii,

  {{ nullif_placeholder("n.plan_synced") }}              AS plan_synced,
  {{ nullif_placeholder("n.review_situation") }}         AS review_situation,
  {{ nullif_placeholder("n.affected_files") }}           AS affected_files,
  {{ nullif_placeholder("n.affected_objects") }}         AS affected_objects,

  -- 签署状态 + 字典布尔
  {{ nullif_placeholder("n.file_signature_status") }}    AS file_signature_status,
  COALESCE(ss.is_reviewed, false)                        AS is_file_reviewed,
  COALESCE(ss.is_signed,   false)                        AS is_file_signed,

  -- 整改状态 + 派生布尔
  {{ nullif_placeholder("n.reform_status") }}            AS reform_status,
  CASE WHEN btrim(COALESCE(n.reform_status,''))='已落实整改' THEN true ELSE false END AS is_reform_done,
  CASE WHEN btrim(COALESCE(n.reform_status,''))='不涉及'     THEN true ELSE false END AS is_reform_na,
  CASE WHEN btrim(COALESCE(n.reform_status,'')) NOT IN ('已落实整改','不涉及','') AND n.reform_status IS NOT NULL
       THEN true ELSE false END                          AS is_reform_pending,

  {{ nullif_placeholder("n.project_manager") }}          AS project_manager,
  {{ nullif_placeholder("n.filled_by") }}                AS filled_by,
  {{ nullif_placeholder("n.remark") }}                   AS remark,

  -- === 数值字段 ===
  {{ parse_numeric_safe("n.new_plan_count") }}::int      AS new_plan_count,

  -- === 日期解析 ===
  {{ parse_date_safe("n.change_submit_time") }}          AS change_submit_time,
  {{ parse_date_safe("n.signature_closure_date") }}      AS signature_closure_date,
  {{ parse_date_safe("n.plan_file_closure_date") }}      AS plan_file_closure_date,
  {{ parse_date_safe("n.plan_reform_date") }}            AS plan_reform_date,
  {{ parse_date_safe("n.file_signature_date") }}         AS file_signature_date,
  {{ parse_date_safe("n.reform_date") }}                 AS reform_date,
  {{ parse_date_safe("n.last_update_time") }}            AS last_update_time,

  -- === 周数字段 ===
  {{ parse_numeric_safe("n.change_submit_week") }}::int          AS change_submit_week,
  {{ parse_numeric_safe("n.signature_closure_week") }}::int      AS signature_closure_week,
  {{ parse_numeric_safe("n.plan_file_closure_week") }}::int      AS plan_file_closure_week,
  {{ parse_numeric_safe("n.plan_reform_week") }}::int            AS plan_reform_week,
  {{ parse_numeric_safe("n.file_signature_week") }}::int         AS file_signature_week,
  {{ parse_numeric_safe("n.reform_week") }}::int                 AS reform_week,
  {{ parse_numeric_safe("n.last_update_week") }}::int            AS last_update_week,

  -- === 签署完成标志（来源于 completion_signature='是'，与 is_file_signed 来源不同） ===
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("n.completion_signature") }}, '')) = '是' THEN true
    ELSE false
  END AS is_signature_completed,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM {{ parse_date_safe("n.change_submit_time") }})::int    AS submit_year,
  to_char({{ parse_date_safe("n.change_submit_time") }}, 'YYYY-MM')        AS submit_month,

  'ods_tech_state_v2'::text AS source_table,
  now() AS etl_time

FROM normalized n
LEFT JOIN {{ ref('dim_change_category_v2') }} cc
  ON cc.code = n.change_category_code
LEFT JOIN {{ ref('dim_signature_status_v2') }} ss
  ON ss.code = {{ nullif_placeholder("n.file_signature_status") }}
