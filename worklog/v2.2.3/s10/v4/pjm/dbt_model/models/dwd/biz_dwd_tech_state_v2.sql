{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dwd', 'tech-state']) }}

WITH stg AS (
  SELECT * FROM {{ ref('stg_pm__tech_state_v2') }}
  WHERE project_no IS NOT NULL
    AND change_submit_time IS NOT NULL
),
normalized_lv1 AS (
  SELECT
    s.*,
    cca.canonical_code AS change_category,
    bac.canonical_code AS completion_signature,
    bap.canonical_code AS plan_synced,
    CASE
      WHEN s.reform_status_raw IS NULL THEN NULL
      ELSE COALESCE(rsa.canonical_code, '整改中')
    END AS reform_status,
    s.review_situation_raw AS review_situation,
    CASE
      WHEN s.review_situation_raw LIKE '%评估评审%' THEN true
      WHEN s.review_situation_raw LIKE '%已评审%'   THEN true
      WHEN s.review_situation_raw LIKE '%评审通过%' THEN true
      ELSE false
    END AS has_review_signal
  FROM stg s
  LEFT JOIN {{ ref('dim_change_category_alias') }} cca
    ON cca.alias_raw = upper(s.change_category_raw)
  LEFT JOIN {{ ref('dim_boolean_alias') }} bac
    ON bac.alias_raw = upper(s.completion_signature_raw)
  LEFT JOIN {{ ref('dim_boolean_alias') }} bap
    ON bap.alias_raw = upper(s.plan_synced_raw)
  LEFT JOIN {{ ref('dim_reform_status_alias') }} rsa
    ON rsa.alias_raw = s.reform_status_raw
),
normalized_lv2 AS (
  SELECT
    n.*,
    CASE
      WHEN n.change_category IN ('I','II') AND n.has_review_signal THEN 'I_II_with_review'
      WHEN n.change_category IN ('I','II')                         THEN 'I_II_no_review'
      WHEN n.change_category = 'III'                               THEN 'III'
      ELSE 'OTHER'
    END AS signature_context
  FROM normalized_lv1 n
),
normalized_lv3 AS (
  SELECT
    n.*,
    ssa.canonical_code AS file_signature_status
  FROM normalized_lv2 n
  LEFT JOIN {{ ref('dim_signature_status_alias') }} ssa
    ON ssa.context = n.signature_context
   AND ssa.alias_raw = n.file_signature_status_raw
),
derived AS (
  SELECT
    n.*,
    to_char(n.change_submit_time, 'YYYY-MM') AS submit_month,
    to_char(n.file_signature_date, 'YYYY-MM') AS file_signature_month,
    to_char(n.reform_date, 'YYYY-MM') AS reform_month,
    to_char(n.last_update_time, 'YYYY-MM') AS last_update_month,
    EXTRACT(YEAR FROM n.change_submit_time)::int AS submit_year,
    COALESCE(n.last_update_time, current_date) AS state_as_of_date
  FROM normalized_lv3 n
)

SELECT
  concat('tech_state:', d.source_row_id) AS tech_state_id,

  d.source_row_id,
  d.source_table,
  d.source_system,
  d.source_file,
  d.source_sheet_name,
  d.source_batch_id,
  d.source_row_num,
  d.imported_at AS source_imported_at,

  d.project_no,
  d.tech_state_name,
  d.change_item,
  d.owner,
  d.dept,
  d.dept_leader,

  d.completion_signature_raw,
  COALESCE(d.completion_signature, d.completion_signature_raw) AS completion_signature,
  d.change_reason,

  d.change_category_raw,
  d.change_category,
  cc.change_category_id,
  cc.label AS change_category_label,
  COALESCE(cc.is_cat_i, false) AS is_cat_i,
  COALESCE(cc.is_cat_ii, false) AS is_cat_ii,
  COALESCE(cc.is_cat_iii, false) AS is_cat_iii,

  d.plan_synced_raw,
  COALESCE(d.plan_synced, d.plan_synced_raw) AS plan_synced,
  d.review_situation_raw,
  d.review_situation,
  d.has_review_signal,
  d.affected_files,
  d.affected_objects,

  d.file_signature_status_raw,
  d.signature_context,
  COALESCE(d.file_signature_status, d.file_signature_status_raw) AS file_signature_status,
  ss.signature_status_id,
  ss.label AS file_signature_status_label,
  COALESCE(ss.is_reviewed, false) AS is_file_reviewed,
  COALESCE(ss.is_signed, false)   AS is_file_signed,

  d.reform_status_raw,
  d.reform_status,
  CASE WHEN d.reform_status = '已落实整改' THEN true ELSE false END AS is_reform_done,
  CASE WHEN d.reform_status = '不涉及'     THEN true ELSE false END AS is_reform_na,
  CASE
    WHEN d.reform_status IS NOT NULL
     AND d.reform_status NOT IN ('已落实整改', '不涉及') THEN true
    ELSE false
  END AS is_reform_pending,

  d.project_manager,
  d.filled_by,
  d.remark,
  d.new_plan_count,

  d.change_submit_time,
  d.signature_closure_date,
  d.plan_file_closure_date,
  d.plan_reform_date,
  d.file_signature_date,
  d.reform_date,
  d.last_update_time,

  d.change_submit_week,
  d.signature_closure_week,
  d.plan_file_closure_week,
  d.plan_reform_week,
  d.file_signature_week,
  d.reform_week,
  d.last_update_week,

  CASE WHEN d.completion_signature = '是' THEN true ELSE false END AS is_signature_completed,

  d.submit_year,
  d.submit_month,
  d.file_signature_month,
  d.reform_month,
  d.last_update_month,
  d.state_as_of_date,
  to_char(d.state_as_of_date, 'YYYY-MM') AS state_as_of_month,

  now() AS etl_time
FROM derived d
LEFT JOIN {{ ref('dim_change_category_v2') }} cc
  ON cc.code = d.change_category
LEFT JOIN {{ ref('dim_signature_status_v2') }} ss
  ON ss.code = d.file_signature_status
