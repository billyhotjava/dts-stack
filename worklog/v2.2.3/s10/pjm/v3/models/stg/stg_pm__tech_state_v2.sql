{{ config(materialized='view', tags=['project-management-v3', 'stg', 'tech-state']) }}

WITH cleaned AS (
  SELECT
    o.id AS source_row_id,
    'ods_tech_state_v2'::text AS source_table,
    COALESCE({{ nullif_placeholder("o.source_system") }}, 'excel') AS source_system,
    {{ nullif_placeholder("o.source_file") }} AS source_file,
    {{ nullif_placeholder("o.sheet_name") }} AS source_sheet_name,
    {{ nullif_placeholder("o.batch_id") }} AS source_batch_id,
    {{ parse_numeric_safe("o.row_num") }}::int AS source_row_num,
    o.import_time AS imported_at,

    {{ nullif_placeholder("o.project_no") }} AS project_no,
    {{ nullif_placeholder("o.tech_state_name") }} AS tech_state_name,
    {{ nullif_placeholder("o.change_item") }} AS change_item,
    {{ nullif_placeholder("o.owner") }} AS owner,
    {{ nullif_placeholder("o.dept") }} AS dept,
    {{ nullif_placeholder("o.dept_leader") }} AS dept_leader,
    {{ nullif_placeholder("o.completion_signature") }} AS completion_signature_raw,
    {{ nullif_placeholder("o.change_reason") }} AS change_reason,
    {{ nullif_placeholder("o.change_category") }} AS change_category_raw,
    {{ nullif_placeholder("o.plan_synced") }} AS plan_synced_raw,
    {{ nullif_placeholder("o.review_situation") }} AS review_situation_raw,
    {{ nullif_placeholder("o.affected_files") }} AS affected_files,
    {{ nullif_placeholder("o.affected_objects") }} AS affected_objects,
    {{ nullif_placeholder("o.file_signature_status") }} AS file_signature_status_raw,
    {{ nullif_placeholder("o.reform_status") }} AS reform_status_raw,
    {{ nullif_placeholder("o.project_manager") }} AS project_manager,
    {{ nullif_placeholder("o.filled_by") }} AS filled_by,
    {{ nullif_placeholder("o.remark") }} AS remark,

    {{ parse_numeric_safe("o.new_plan_count") }}::int AS new_plan_count,
    {{ parse_date_safe("o.change_submit_time") }} AS change_submit_time,
    {{ parse_date_safe("o.signature_closure_date") }} AS signature_closure_date,
    {{ parse_date_safe("o.plan_file_closure_date") }} AS plan_file_closure_date,
    {{ parse_date_safe("o.plan_reform_date") }} AS plan_reform_date,
    {{ parse_date_safe("o.file_signature_date") }} AS file_signature_date,
    {{ parse_date_safe("o.reform_date") }} AS reform_date,
    {{ parse_date_safe("o.last_update_time") }} AS last_update_time,
    {{ parse_numeric_safe("o.change_submit_week") }}::int AS change_submit_week,
    {{ parse_numeric_safe("o.signature_closure_week") }}::int AS signature_closure_week,
    {{ parse_numeric_safe("o.plan_file_closure_week") }}::int AS plan_file_closure_week,
    {{ parse_numeric_safe("o.plan_reform_week") }}::int AS plan_reform_week,
    {{ parse_numeric_safe("o.file_signature_week") }}::int AS file_signature_week,
    {{ parse_numeric_safe("o.reform_week") }}::int AS reform_week,
    {{ parse_numeric_safe("o.last_update_week") }}::int AS last_update_week
  FROM {{ source('pm_ods_v2', 'tech_state_v2') }} o
),
normalized AS (
  SELECT
    c.*,
    CASE
      WHEN upper(COALESCE(c.change_category_raw, '')) IN ('I', 'Ⅰ', 'I类', '1', '一') THEN 'I'
      WHEN upper(COALESCE(c.change_category_raw, '')) IN ('II', 'Ⅱ', 'II类', '2', '二') THEN 'II'
      WHEN upper(COALESCE(c.change_category_raw, '')) IN ('III', 'Ⅲ', 'III类', '3', '三') THEN 'III'
      ELSE c.change_category_raw
    END AS change_category,
    CASE
      WHEN c.review_situation_raw LIKE '%评估评审%' THEN true
      WHEN c.review_situation_raw LIKE '%已评审%' THEN true
      WHEN c.review_situation_raw LIKE '%评审通过%' THEN true
      ELSE false
    END AS has_review_signal
  FROM cleaned c
)

SELECT
  n.source_row_id,
  n.source_table,
  n.source_system,
  n.source_file,
  n.source_sheet_name,
  n.source_batch_id,
  n.source_row_num,
  n.imported_at,

  n.project_no,
  n.tech_state_name,
  n.change_item,
  n.owner,
  n.dept,
  n.dept_leader,

  n.completion_signature_raw,
  CASE
    WHEN upper(COALESCE(n.completion_signature_raw, '')) IN ('Y', 'YES', 'TRUE', '是', '已完成', '已签署') THEN '是'
    WHEN upper(COALESCE(n.completion_signature_raw, '')) IN ('N', 'NO', 'FALSE', '否', '未完成', '未签署') THEN '否'
    ELSE n.completion_signature_raw
  END AS completion_signature,

  n.change_reason,
  n.change_category_raw,
  n.change_category,

  n.plan_synced_raw,
  CASE
    WHEN upper(COALESCE(n.plan_synced_raw, '')) IN ('Y', 'YES', 'TRUE', '是', '已同步') THEN '是'
    WHEN upper(COALESCE(n.plan_synced_raw, '')) IN ('N', 'NO', 'FALSE', '否', '未同步') THEN '否'
    ELSE n.plan_synced_raw
  END AS plan_synced,

  n.review_situation_raw AS review_situation,
  n.affected_files,
  n.affected_objects,

  n.file_signature_status_raw,
  CASE
    WHEN n.change_category IN ('I', 'II') THEN
      CASE
        WHEN n.file_signature_status_raw = '已评估评审，已签署' THEN '已评估评审，已签署'
        WHEN n.file_signature_status_raw IN ('已评估评审，未签署', '已评估评审，待签署', '已评估评审，已通过', '评审通过') THEN '已评估评审，未签署'
        WHEN n.file_signature_status_raw IN ('已签署', '已提出需求并签署') THEN '已评估评审，已签署'
        WHEN n.file_signature_status_raw IN ('未签署', '已提出需求，待签署') AND n.has_review_signal THEN '已评估评审，未签署'
        WHEN n.file_signature_status_raw IN ('未签署', '已提出需求，待签署', '待评审', '待提出', '已提出需求，未评估评审') THEN '已提出需求，未评估评审'
        ELSE n.file_signature_status_raw
      END
    WHEN n.change_category = 'III' THEN
      CASE
        WHEN n.file_signature_status_raw IN ('已提出需求，已签署', '已提出需求并签署', '已签署') THEN '已提出需求，已签署'
        WHEN n.file_signature_status_raw IN ('已提出需求，未签署', '已提出需求，待签署', '未签署') THEN '已提出需求，未签署'
        ELSE n.file_signature_status_raw
      END
    ELSE n.file_signature_status_raw
  END AS file_signature_status,

  n.reform_status_raw,
  CASE
    WHEN n.reform_status_raw IN ('已落实整改', '已完成', '完成') THEN '已落实整改'
    WHEN n.reform_status_raw IN ('不涉及', '无') THEN '不涉及'
    WHEN n.reform_status_raw IS NULL THEN NULL
    ELSE '整改中'
  END AS reform_status,

  n.project_manager,
  n.filled_by,
  n.remark,
  n.new_plan_count,

  n.change_submit_time,
  n.signature_closure_date,
  n.plan_file_closure_date,
  n.plan_reform_date,
  n.file_signature_date,
  n.reform_date,
  n.last_update_time,

  n.change_submit_week,
  n.signature_closure_week,
  n.plan_file_closure_week,
  n.plan_reform_week,
  n.file_signature_week,
  n.reform_week,
  n.last_update_week,

  to_char(n.change_submit_time, 'YYYY-MM') AS submit_month,
  to_char(n.file_signature_date, 'YYYY-MM') AS file_signature_month,
  to_char(n.reform_date, 'YYYY-MM') AS reform_month,
  to_char(n.last_update_time, 'YYYY-MM') AS last_update_month
FROM normalized n
