{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dwd', 'tech-state']) }}

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.tech_state_name), '') || '|' ||
    COALESCE(btrim(o.change_item), '')
  ) AS tech_state_id,

  -- === 原始业务字段 ===
  {{ nullif_placeholder("o.project_no") }}               AS project_no,
  {{ nullif_placeholder("o.tech_state_name") }}          AS tech_state_name,
  {{ nullif_placeholder("o.change_item") }}              AS change_item,
  {{ nullif_placeholder("o.owner") }}                    AS owner,
  {{ nullif_placeholder("o.dept") }}                     AS dept,
  {{ nullif_placeholder("o.dept_leader") }}              AS dept_leader,
  {{ nullif_placeholder("o.completion_signature") }}     AS completion_signature,
  {{ nullif_placeholder("o.change_reason") }}            AS change_reason,

  -- 标准化更改类别
  CASE
    WHEN upper(btrim(COALESCE(o.change_category, ''))) IN ('I', 'Ⅰ', 'I类', '1', '一') THEN 'I'
    WHEN upper(btrim(COALESCE(o.change_category, ''))) IN ('II', 'Ⅱ', 'II类', '2', '二') THEN 'II'
    WHEN upper(btrim(COALESCE(o.change_category, ''))) IN ('III', 'Ⅲ', 'III类', '3', '三') THEN 'III'
    ELSE {{ nullif_placeholder("o.change_category") }}
  END AS change_category,

  {{ nullif_placeholder("o.plan_synced") }}              AS plan_synced,
  {{ nullif_placeholder("o.review_situation") }}         AS review_situation,
  {{ nullif_placeholder("o.affected_files") }}           AS affected_files,
  {{ nullif_placeholder("o.affected_objects") }}         AS affected_objects,
  {{ nullif_placeholder("o.file_signature_status") }}    AS file_signature_status,
  {{ nullif_placeholder("o.reform_status") }}            AS reform_status,
  {{ nullif_placeholder("o.project_manager") }}          AS project_manager,
  {{ nullif_placeholder("o.filled_by") }}                AS filled_by,
  {{ nullif_placeholder("o.remark") }}                   AS remark,

  -- === 数值字段 ===
  {{ parse_numeric_safe("o.new_plan_count") }}::int      AS new_plan_count,

  -- === 日期解析 ===
  {{ parse_date_safe("o.change_submit_time") }}          AS change_submit_time,
  {{ parse_date_safe("o.signature_closure_date") }}      AS signature_closure_date,
  {{ parse_date_safe("o.plan_file_closure_date") }}      AS plan_file_closure_date,
  {{ parse_date_safe("o.plan_reform_date") }}            AS plan_reform_date,
  {{ parse_date_safe("o.file_signature_date") }}         AS file_signature_date,
  {{ parse_date_safe("o.reform_date") }}                 AS reform_date,
  {{ parse_date_safe("o.last_update_time") }}            AS last_update_time,

  -- === 周数字段 ===
  {{ parse_numeric_safe("o.change_submit_week") }}::int          AS change_submit_week,
  {{ parse_numeric_safe("o.signature_closure_week") }}::int      AS signature_closure_week,
  {{ parse_numeric_safe("o.plan_file_closure_week") }}::int      AS plan_file_closure_week,
  {{ parse_numeric_safe("o.plan_reform_week") }}::int            AS plan_reform_week,
  {{ parse_numeric_safe("o.file_signature_week") }}::int         AS file_signature_week,
  {{ parse_numeric_safe("o.reform_week") }}::int                 AS reform_week,
  {{ parse_numeric_safe("o.last_update_week") }}::int            AS last_update_week,

  -- === 签署完成标志 ===
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.completion_signature") }}, '')) = '是' THEN true
    ELSE false
  END AS is_signature_completed,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM {{ parse_date_safe("o.change_submit_time") }})::int    AS submit_year,
  to_char({{ parse_date_safe("o.change_submit_time") }}, 'YYYY-MM')        AS submit_month,

  'ods_tech_state_v2'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods_v2', 'tech_state_v2') }} o
WHERE o.project_no IS NOT NULL
  AND btrim(COALESCE(o.project_no, '')) != ''
