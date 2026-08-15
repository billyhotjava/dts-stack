{{ config(materialized='table', tags=['project-management-v3', 'biz', 'dwd', 'material']) }}

WITH stg AS (
  SELECT *
  FROM {{ ref('stg_pm__material_info_v2') }}
  WHERE project_no IS NOT NULL
    AND pbs_no IS NOT NULL
), normalized AS (
  SELECT
    s.*,
    long_cycle.canonical_code AS is_long_cycle_code,
    major_node.canonical_code AS affects_major_node_code,
    risk_alias.canonical_code AS risk_level,
    COALESCE(s.last_update_time, current_date) AS state_as_of_date
  FROM stg s
  LEFT JOIN {{ ref('dim_boolean_alias') }} long_cycle
    ON long_cycle.alias_raw = upper(s.is_long_cycle_raw)
  LEFT JOIN {{ ref('dim_boolean_alias') }} major_node
    ON major_node.alias_raw = upper(s.affects_major_node_raw)
  LEFT JOIN {{ ref('dim_risk_level_alias') }} risk_alias
    ON risk_alias.alias_raw = s.risk_level_raw
)

SELECT
  concat('material_delivery:', md5(concat_ws(chr(31), n.project_no, n.pbs_no))) AS material_delivery_id,
  n.source_row_id,
  n.source_table,
  n.source_system,
  n.imported_at AS source_imported_at,
  n.project_no,
  n.subsystem,
  n.pbs_no,
  n.pbs_name,
  n.self_or_outsource,
  n.supplier_name,
  n.is_long_cycle_raw,
  (n.is_long_cycle_code = '是') AS is_long_cycle,
  n.dept_owner,
  n.control_dept_owner,
  n.weekly_progress,
  n.affects_major_node_raw,
  (n.affects_major_node_code = '是') AS affects_major_node,
  n.risk_level_raw,
  n.risk_level,
  rl.risk_level_id,
  rl.label AS risk_level_label,
  COALESCE(rl.is_high, false) AS is_high_risk,
  COALESCE(rl.is_mid, false) AS is_mid_risk,
  COALESCE(rl.is_low, false) AS is_low_risk,
  n.risk_content,
  n.delay_impact,
  n.remark,
  n.contract_negotiation_date,
  n.contract_delivery_date,
  n.actual_delivery_date,
  n.plan_inspect_date,
  n.complete_inspect_date,
  n.install_date,
  n.last_update_time,
  n.contract_negotiation_week,
  n.contract_delivery_week,
  n.actual_delivery_week,
  n.plan_inspect_week,
  n.complete_inspect_week,
  n.install_week,
  n.last_update_week,
  EXTRACT(YEAR FROM n.contract_delivery_date)::int AS contract_delivery_year,
  to_char(n.contract_delivery_date, 'YYYY-MM') AS contract_delivery_month,
  n.state_as_of_date,
  (n.actual_delivery_date IS NOT NULL) AS is_delivered,
  (n.actual_delivery_date IS NOT NULL AND n.actual_delivery_date <= n.contract_delivery_date) AS is_on_time_delivery,
  (n.actual_delivery_date IS NULL AND n.contract_delivery_date < n.state_as_of_date) AS is_overdue_undelivered,
  CASE
    WHEN n.actual_delivery_date IS NOT NULL THEN n.actual_delivery_date - n.contract_delivery_date
    WHEN n.contract_delivery_date < n.state_as_of_date THEN n.state_as_of_date - n.contract_delivery_date
    ELSE 0
  END AS delivery_delay_days,
  now() AS etl_time
FROM normalized n
LEFT JOIN {{ ref('dim_risk_level_v2') }} rl
  ON rl.code = n.risk_level
