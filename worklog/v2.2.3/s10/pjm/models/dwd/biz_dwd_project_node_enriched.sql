{{ config(materialized='table', tags=['project-management', 'biz', 'project-cockpit', 'dwd']) }}

WITH base AS (
  SELECT *
  FROM {{ ref('biz_dwd_project_node') }}
),
classified AS (
  SELECT
    b.*,
    -- project_no = 项目名称，subsystem = 子项目名称
    -- 不再按 / 拆分，直接使用原始字段
    b.project_no AS _derived_major_project_name,
    b.subsystem  AS _derived_subproject_name,
    CASE
      WHEN {{ nullif_placeholder("b.incomplete_reason") }} IS NULL THEN 'normal'
      WHEN b.incomplete_reason ~ '(算法|技术|仿真|硬件|精度)' THEN 'technical'
      WHEN b.incomplete_reason ~ '(质量|可靠性|返工|故障)' THEN 'quality'
      WHEN b.incomplete_reason ~ '(变更|延误|重新定义)' THEN 'change'
      WHEN b.incomplete_reason ~ '(联调|接口|协同)' THEN 'coordination'
      WHEN b.incomplete_reason ~ '(供货|到货|加工|器件)' THEN 'supplier'
      WHEN b.incomplete_reason ~ '(测试|标定|复测|暗室)' THEN 'test'
      WHEN b.incomplete_reason ~ '(归档|周报|报告)' THEN 'archive'
      ELSE 'normal'
    END AS delay_reason_category_fallback
  FROM base b
)
SELECT
  c.*,
  COALESCE(mp.major_project_id, c.project_no) AS major_project_id,
  COALESCE(mp.major_project_code, c.project_no) AS major_project_code,
  COALESCE(mp.major_project_name, c._derived_major_project_name) AS major_project_name,
  mp.project_level AS major_project_level,
  COALESCE(mp.owner_dept, c.dept) AS major_project_owner_dept,
  mp.owner_leader AS major_project_owner_leader,
  mp.priority_level AS major_project_priority_level,
  COALESCE(sp.subproject_id, md5(c.project_no || '/' || COALESCE(c.subsystem, ''))) AS subproject_id,
  COALESCE(sp.subproject_code, c.project_no || '-' || left(c._derived_subproject_name, 8)) AS subproject_code,
  COALESCE(sp.subproject_name, c._derived_subproject_name) AS subproject_name,
  COALESCE(sp.owner_dept, c.dept) AS subproject_owner_dept,
  sp.owner_user AS subproject_owner_user,
  COALESCE(sp.project_manager, c.project_manager) AS subproject_owner_manager,
  sp.priority_level AS subproject_priority_level,
  map.map_id,
  COALESCE(map.node_category, 'routine') AS node_category,
  COALESCE(map.is_key_node, false) AS is_key_node,
  COALESCE(map.is_milestone, false) AS is_milestone,
  map.sort_order,
  COALESCE(map.delay_reason_category, c.delay_reason_category_fallback) AS delay_reason_category,
  dr.delay_reason_label,
  CASE
    WHEN c.is_completed AND c.is_on_time THEN 'closed-on-time'
    WHEN c.is_completed AND c.is_overdue_completed THEN 'closed-delayed'
    WHEN c.is_incomplete AND COALESCE(c.delay_days, 0) > 0 THEN 'overdue-open'
    WHEN c.is_incomplete THEN 'risk-open'
    WHEN c.is_due THEN 'in-flight'
    ELSE 'planned'
  END AS node_status_bucket,
  CASE COALESCE(c.risk_level, '')
    WHEN '高' THEN 3
    WHEN '中' THEN 2
    WHEN '低' THEN 1
    ELSE 0
  END AS risk_rank,
  GREATEST(COALESCE(c.delay_days, 0), 0) AS overdue_days,
  CASE
    WHEN c.plan_date IS NOT NULL THEN (c.plan_date - current_date)::int
    ELSE NULL
  END AS days_to_plan,
  LEAST(
    100,
    GREATEST(
      0,
      100
      - CASE COALESCE(c.risk_level, '')
          WHEN '高' THEN 35
          WHEN '中' THEN 18
          WHEN '低' THEN 5
          ELSE 0
        END
      - LEAST(GREATEST(COALESCE(c.delay_days, 0), 0), 30)
      - CASE
          WHEN c.is_incomplete THEN 12
          WHEN c.completion_status = '正常待完成' THEN 4
          ELSE 0
        END
      + CASE
          WHEN c.is_completed AND c.is_on_time THEN 8
          WHEN c.is_completed THEN 3
          ELSE 0
        END
    )
  ) AS health_score
FROM classified c
LEFT JOIN {{ ref('pm_map_node_subject') }} map
  ON map.project_no = c.project_no
 AND map.subsystem = c.subsystem
 AND map.node_task = c.node_task
LEFT JOIN {{ ref('pm_dim_subproject') }} sp
  ON sp.subproject_id = map.subproject_id
LEFT JOIN {{ ref('pm_dim_major_project') }} mp
  ON mp.major_project_id = COALESCE(map.major_project_id, sp.major_project_id)
LEFT JOIN {{ ref('pm_dim_delay_reason') }} dr
  ON dr.delay_reason_category = COALESCE(map.delay_reason_category, c.delay_reason_category_fallback)
