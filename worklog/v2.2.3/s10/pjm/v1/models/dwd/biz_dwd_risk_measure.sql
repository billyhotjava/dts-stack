{{ config(materialized='table', tags=['project-management', 'biz', 'dwd', 'risk']) }}

SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.risk_name), '') || '|' ||
    COALESCE(btrim(o.measure_title), '') || '|' ||
    COALESCE(btrim(o.follow_up_date), '')
  ) AS risk_measure_id,

  -- === 风险基本信息字段 ===
  {{ nullif_placeholder("o.project_no") }}              AS project_no,
  {{ nullif_placeholder("o.risk_name") }}               AS risk_name,
  {{ nullif_placeholder("o.subsystem") }}               AS subsystem,
  {{ nullif_placeholder("o.belonging_unit") }}          AS belonging_unit,
  {{ nullif_placeholder("o.risk_description") }}        AS risk_description,
  {{ nullif_placeholder("o.risk_phase") }}              AS risk_phase,
  {{ nullif_placeholder("o.risk_category") }}           AS risk_category,
  {{ nullif_placeholder("o.risk_level") }}              AS risk_level,
  {{ nullif_placeholder("o.impact_scope") }}            AS impact_scope,
  {{ nullif_placeholder("o.response_measure") }}        AS response_measure,
  {{ nullif_placeholder("o.monthly_control_plan") }}    AS monthly_control_plan,
  {{ nullif_placeholder("o.weekly_release_plan") }}     AS weekly_release_plan,
  {{ nullif_placeholder("o.release_plan_synced") }}     AS release_plan_synced,
  {{ nullif_placeholder("o.new_plan_count") }}          AS new_plan_count,
  {{ nullif_placeholder("o.progress_situation") }}      AS progress_situation,
  {{ nullif_placeholder("o.response_owner") }}          AS response_owner,
  {{ nullif_placeholder("o.control_owner") }}           AS control_owner,
  {{ nullif_placeholder("o.dept") }}                    AS dept,
  {{ nullif_placeholder("o.risk_status") }}             AS risk_status,
  {{ nullif_placeholder("o.project_manager") }}         AS project_manager,
  {{ nullif_placeholder("o.remark") }}                  AS remark,
  {{ nullif_placeholder("o.filled_by") }}               AS filled_by,

  -- === 跟进措施字段 ===
  {{ nullif_placeholder("o.measure_category") }}        AS measure_category,
  {{ nullif_placeholder("o.measure_title") }}           AS measure_title,
  {{ nullif_placeholder("o.follow_up_person") }}        AS follow_up_person,
  {{ nullif_placeholder("o.main_recipient") }}          AS main_recipient,
  {{ nullif_placeholder("o.cc_recipient") }}            AS cc_recipient,
  {{ nullif_placeholder("o.closure_status") }}          AS closure_status,
  {{ nullif_placeholder("o.closure_deliverable_type") }} AS closure_deliverable_type,
  {{ nullif_placeholder("o.closure_deliverable") }}     AS closure_deliverable,
  {{ nullif_placeholder("o.risk_content") }}            AS risk_content,

  -- === 周数字段 ===
  {{ nullif_placeholder("o.risk_submit_week") }}        AS risk_submit_week,
  {{ nullif_placeholder("o.progress_stat_week") }}      AS progress_stat_week,
  {{ nullif_placeholder("o.follow_up_week") }}          AS follow_up_week,
  {{ nullif_placeholder("o.final_closure_week") }}      AS final_closure_week,
  {{ nullif_placeholder("o.last_update_week") }}        AS last_update_week,

  -- === 风险等级标准化 ===
  CASE COALESCE({{ nullif_placeholder("o.risk_level") }}, '')
    WHEN '高' THEN 3
    WHEN '中' THEN 2
    WHEN '低' THEN 1
    ELSE 0
  END AS risk_rank,

  -- === 闭环标志 ===
  CASE
    WHEN btrim(COALESCE({{ nullif_placeholder("o.closure_status") }}, '')) = '已闭环' THEN true
    ELSE false
  END AS is_closed,

  -- === 日期解析 ===
  {{ parse_date_safe("o.risk_submit_time") }}           AS risk_submit_date,
  {{ parse_date_safe("o.final_release_time") }}         AS final_release_date,
  {{ parse_date_safe("o.progress_stat_time") }}         AS progress_stat_date,
  {{ parse_date_safe("o.follow_up_date") }}             AS follow_up_date,
  {{ parse_date_safe("o.final_closure_date") }}         AS final_closure_date,
  {{ parse_date_safe("o.last_update_time") }}           AS last_update_time,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM {{ parse_date_safe("o.risk_submit_time") }})::int     AS submit_year,
  EXTRACT(QUARTER FROM {{ parse_date_safe("o.risk_submit_time") }})::int  AS submit_quarter,
  to_char({{ parse_date_safe("o.risk_submit_time") }}, 'YYYY-MM')         AS submit_month,

  -- === 滞留天数 ===
  CASE
    WHEN {{ parse_date_safe("o.follow_up_date") }} IS NOT NULL
     AND btrim(COALESCE({{ nullif_placeholder("o.closure_status") }}, '')) != '已闭环'
    THEN (current_date - {{ parse_date_safe("o.follow_up_date") }})::int
    ELSE 0
  END AS pending_days,

  'ods_risk_measure'::text AS source_table,
  now() AS etl_time

FROM {{ source('pm_ods', 'risk_measure') }} o
WHERE o.project_no IS NOT NULL
