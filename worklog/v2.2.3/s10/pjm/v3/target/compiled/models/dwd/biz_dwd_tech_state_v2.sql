

WITH typed AS (
  SELECT
    s.*,
    COALESCE(s.last_update_time, current_date) AS state_as_of_date,
    EXTRACT(YEAR FROM s.change_submit_time)::int AS submit_year
  FROM "biadmin"."public"."stg_pm__tech_state_v2" s
  WHERE s.project_no IS NOT NULL
    AND s.change_submit_time IS NOT NULL
)

SELECT
  concat('tech_state:', t.source_row_id) AS tech_state_id,

  t.source_row_id,
  t.source_table,
  t.source_system,
  t.source_file,
  t.source_sheet_name,
  t.source_batch_id,
  t.source_row_num,
  t.imported_at AS source_imported_at,

  t.project_no,
  t.tech_state_name,
  t.change_item,
  t.owner,
  t.dept,
  t.dept_leader,

  t.completion_signature_raw,
  t.completion_signature,
  t.change_reason,
  t.change_category_raw,
  t.change_category,
  cc.change_category_id,
  COALESCE(cc.is_cat_i, false) AS is_cat_i,
  COALESCE(cc.is_cat_ii, false) AS is_cat_ii,
  COALESCE(cc.is_cat_iii, false) AS is_cat_iii,

  t.plan_synced_raw,
  t.plan_synced,
  t.review_situation,
  t.affected_files,
  t.affected_objects,

  t.file_signature_status_raw,
  t.file_signature_status,
  ss.signature_status_id,
  COALESCE(ss.is_reviewed, false) AS is_file_reviewed,
  COALESCE(ss.is_signed, false) AS is_file_signed,

  t.reform_status_raw,
  t.reform_status,
  CASE
    WHEN t.reform_status = '已落实整改' THEN true
    ELSE false
  END AS is_reform_done,
  CASE
    WHEN t.reform_status = '不涉及' THEN true
    ELSE false
  END AS is_reform_na,
  CASE
    WHEN t.reform_status IS NOT NULL
     AND t.reform_status NOT IN ('已落实整改', '不涉及')
    THEN true
    ELSE false
  END AS is_reform_pending,

  t.project_manager,
  t.filled_by,
  t.remark,
  t.new_plan_count,

  t.change_submit_time,
  t.signature_closure_date,
  t.plan_file_closure_date,
  t.plan_reform_date,
  t.file_signature_date,
  t.reform_date,
  t.last_update_time,

  t.change_submit_week,
  t.signature_closure_week,
  t.plan_file_closure_week,
  t.plan_reform_week,
  t.file_signature_week,
  t.reform_week,
  t.last_update_week,

  CASE
    WHEN t.completion_signature = '是' THEN true
    ELSE false
  END AS is_signature_completed,

  t.submit_year,
  t.submit_month,
  t.file_signature_month,
  t.reform_month,
  t.last_update_month,
  t.state_as_of_date,
  to_char(t.state_as_of_date, 'YYYY-MM') AS state_as_of_month,

  now() AS etl_time
FROM typed t
LEFT JOIN "biadmin"."public"."dim_change_category_v2" cc
  ON cc.code = t.change_category
LEFT JOIN "biadmin"."public"."dim_signature_status_v2" ss
  ON ss.code = t.file_signature_status