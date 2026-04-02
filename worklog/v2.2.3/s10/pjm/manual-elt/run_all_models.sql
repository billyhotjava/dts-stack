-- ============================================================
-- 手工执行 ELT：按依赖顺序运行所有数仓模型
-- 前提：ODS 表已存在且有数据
-- 用法：psql -U biadmin -d biadmin -f run_all_models.sql
-- ============================================================

BEGIN;

-- ── dim_completion_status ──
DROP TABLE IF EXISTS dim_completion_status CASCADE;
CREATE TABLE dim_completion_status AS
SELECT code, label, is_completed, is_on_time, is_overdue_completed, is_incomplete, sort_order
FROM (VALUES
  ('正常待完成',         '正常待完成',       false, false, false, false, 1),
  ('按时完成',           '按时完成',         true,  true,  false, false, 2),
  ('超期已完成已变更',   '超期已完成已变更', true,  false, true,  false, 3),
  ('超期已完成未变更',   '超期已完成未变更', true,  false, true,  false, 4),
  ('不正常待变更',       '不正常待变更',     false, false, false, true,  5),
  ('超期未完成未变更',   '超期未完成未变更', false, false, false, true,  6),
  ('超期未完成已变更',   '超期未完成已变更', false, false, false, true,  7)
) AS t(code, label, is_completed, is_on_time, is_overdue_completed, is_incomplete, sort_order);

-- ── dim_node_type ──
DROP TABLE IF EXISTS dim_node_type CASCADE;
CREATE TABLE dim_node_type AS
SELECT code, label, is_general, severity_rank
FROM (VALUES
  ('一般节点',   '一般节点',   true,  1),
  ('重要节点',   '重要节点',   false, 2),
  ('重大节点',   '重大节点',   false, 3),
  ('里程碑节点', '里程碑节点', false, 4)
) AS t(code, label, is_general, severity_rank);

-- ── dim_risk_level ──
DROP TABLE IF EXISTS dim_risk_level CASCADE;
CREATE TABLE dim_risk_level AS
SELECT code, label, severity_rank
FROM (VALUES
  ('高', '高风险', 3),
  ('中', '中风险', 2),
  ('低', '低风险', 1)
) AS t(code, label, severity_rank);

-- ── dim_quality_status ──
DROP TABLE IF EXISTS dim_quality_status CASCADE;
CREATE TABLE dim_quality_status AS
SELECT code, label, is_zero_completed, is_tech_zero, is_mgmt_zero, is_both_zero, sort_order
FROM (VALUES
  ('未完成归零',             '未完成归零',             false, false, false, false, 1),
  ('已完成技术归零',         '已完成技术归零',         true,  true,  false, false, 2),
  ('已完成管理归零',         '已完成管理归零',         true,  false, true,  false, 3),
  ('已完成技术和管理归零',   '已完成技术和管理归零',   true,  true,  true,  true,  4)
) AS t(code, label, is_zero_completed, is_tech_zero, is_mgmt_zero, is_both_zero, sort_order);

-- ── dim_change_category ──
DROP TABLE IF EXISTS dim_change_category CASCADE;
CREATE TABLE dim_change_category AS
SELECT code, label, severity_rank
FROM (VALUES
  ('I',   'Ⅰ类更改', 3),
  ('Ⅰ',  'Ⅰ类更改', 3),
  ('II',  'Ⅱ类更改', 2),
  ('Ⅱ',  'Ⅱ类更改', 2),
  ('III', 'Ⅲ类更改', 1),
  ('Ⅲ',  'Ⅲ类更改', 1)
) AS t(code, label, severity_rank);

-- ── dim_signature_status ──
DROP TABLE IF EXISTS dim_signature_status CASCADE;
CREATE TABLE dim_signature_status AS
-- 文件签署状态维度表
-- 基于 PDF P4 技术状态统计中的签署状态枚举
SELECT code, label, is_submitted, is_reviewed, is_signed, sort_order
FROM (VALUES
  ('已提出需求，未评估评审',     '已提出需求，未评估评审',     true,  false, false, 1),
  ('已评估评审，未签署',         '已评估评审，未签署',         true,  true,  false, 2),
  ('已评估评审，已签署',         '已评估评审，已签署',         true,  true,  true,  3),
  ('已提出需求，未签署',         '已提出需求，未签署',         true,  false, false, 4),
  ('已提出需求，已签署',         '已提出需求，已签署',         true,  false, true,  5)
) AS t(code, label, is_submitted, is_reviewed, is_signed, sort_order);

-- ── pm_dim_delay_reason ──
DROP TABLE IF EXISTS pm_dim_delay_reason CASCADE;
CREATE TABLE pm_dim_delay_reason AS
-- 延期原因枚举，内联定义，无需 seed
SELECT * FROM (
  VALUES
    ('normal',       '正常推进', '计划内推进或已按时完成'),
    ('technical',    '技术攻关', '关键技术或算法攻关导致延期'),
    ('quality',      '质量整改', '质量问题或试验整改导致延期'),
    ('change',       '计划变更', '计划调整或技术状态变更导致延期'),
    ('coordination', '协同配合', '跨部门协同或接口联调导致延期'),
    ('supplier',     '供方配套', '供货、到货或外协加工导致延期'),
    ('test',         '试验验证', '测试、标定或暗室试验导致延期'),
    ('archive',      '归档报告', '周报、归档或技术报告导致延期')
) AS t(delay_reason_category, delay_reason_label, description);

-- ── biz_dwd_project_node ──
DROP TABLE IF EXISTS biz_dwd_project_node CASCADE;
CREATE TABLE biz_dwd_project_node AS
SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.subsystem), '') || '|' ||
    COALESCE(btrim(o.node_task), '') || '|' ||
    COALESCE(btrim(o.plan_date), '')
  ) AS node_id,

  -- === 原始业务字段 ===
  (
  CASE
    WHEN o.project_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_no AS text)), '')
  END
)          AS project_no,
  (
  CASE
    WHEN o.subsystem IS NULL THEN NULL
    WHEN upper(btrim(cast(o.subsystem AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.subsystem AS text)), '')
  END
)           AS subsystem,
  (
  CASE
    WHEN o.node_task IS NULL THEN NULL
    WHEN upper(btrim(cast(o.node_task AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.node_task AS text)), '')
  END
)           AS node_task,
  (
  CASE
    WHEN o.owner IS NULL THEN NULL
    WHEN upper(btrim(cast(o.owner AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.owner AS text)), '')
  END
)               AS owner,
  (
  CASE
    WHEN o.dept IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept AS text)), '')
  END
)                AS dept,
  (
  CASE
    WHEN o.dept_leader IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept_leader AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept_leader AS text)), '')
  END
)         AS dept_leader,
  (
  CASE
    WHEN o.collab_dept IS NULL THEN NULL
    WHEN upper(btrim(cast(o.collab_dept AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.collab_dept AS text)), '')
  END
)         AS collab_dept,
  (
  CASE
    WHEN o.supervisor_dept IS NULL THEN NULL
    WHEN upper(btrim(cast(o.supervisor_dept AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.supervisor_dept AS text)), '')
  END
)     AS supervisor_dept,
  (
  CASE
    WHEN o.incomplete_reason IS NULL THEN NULL
    WHEN upper(btrim(cast(o.incomplete_reason AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.incomplete_reason AS text)), '')
  END
)   AS incomplete_reason,
  (
  CASE
    WHEN o.risk_content IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_content AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_content AS text)), '')
  END
)        AS risk_content,
  (
  CASE
    WHEN o.delay_impact IS NULL THEN NULL
    WHEN upper(btrim(cast(o.delay_impact AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.delay_impact AS text)), '')
  END
)        AS delay_impact,
  (
  CASE
    WHEN o.institute_leader IS NULL THEN NULL
    WHEN upper(btrim(cast(o.institute_leader AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.institute_leader AS text)), '')
  END
)    AS institute_leader,
  (
  CASE
    WHEN o.project_manager IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_manager AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_manager AS text)), '')
  END
)     AS project_manager,
  (
  CASE
    WHEN o.filled_by IS NULL THEN NULL
    WHEN upper(btrim(cast(o.filled_by AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.filled_by AS text)), '')
  END
)           AS filled_by,
  (
  CASE
    WHEN o.highlight IS NULL THEN NULL
    WHEN upper(btrim(cast(o.highlight AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.highlight AS text)), '')
  END
)           AS highlight,
  (
  CASE
    WHEN o.deliverable IS NULL THEN NULL
    WHEN upper(btrim(cast(o.deliverable AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.deliverable AS text)), '')
  END
)          AS deliverable,
  (
  CASE
    WHEN (
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int AS last_update_week,

  -- === 枚举标准化 ===
  (
  CASE
    WHEN o.completion_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.completion_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.completion_status AS text)), '')
  END
)   AS completion_status,
  COALESCE(cs.is_completed, false)         AS is_completed,
  COALESCE(cs.is_on_time, false)           AS is_on_time,
  COALESCE(cs.is_overdue_completed, false) AS is_overdue_completed,
  COALESCE(cs.is_incomplete, false)        AS is_incomplete,

  (
  CASE
    WHEN o.node_type IS NULL THEN NULL
    WHEN upper(btrim(cast(o.node_type AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.node_type AS text)), '')
  END
)           AS node_type,
  COALESCE(nt.is_general, false)           AS is_general_node,

  (
  CASE
    WHEN o.risk_level IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_level AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_level AS text)), '')
  END
)          AS risk_level,

  (
  CASE
    WHEN o.source IS NULL THEN NULL
    WHEN upper(btrim(cast(o.source AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.source AS text)), '')
  END
)              AS data_source,
  (
  CASE
    WHEN o.delay_applied IS NULL THEN NULL
    WHEN upper(btrim(cast(o.delay_applied AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.delay_applied AS text)), '')
  END
)       AS delay_applied,

  -- === 日期解析 ===
  (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)             AS plan_date,
  (
  CASE
    WHEN o.actual_date IS NULL THEN NULL
    WHEN btrim(cast(o.actual_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.actual_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.actual_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.actual_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)           AS actual_date,
  (
  CASE
    WHEN o.delay_expected_date IS NULL THEN NULL
    WHEN btrim(cast(o.delay_expected_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.delay_expected_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.delay_expected_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.delay_expected_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.delay_expected_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.delay_expected_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.delay_expected_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)   AS delay_expected_date,
  (
  CASE
    WHEN o.original_plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.original_plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.original_plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.original_plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.original_plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.original_plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.original_plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.original_plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)    AS original_plan_date,
  (
  CASE
    WHEN o.last_update_time IS NULL THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.last_update_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.last_update_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.last_update_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)      AS last_update_time,

  -- === 周数 ===
  (
  CASE
    WHEN (
  CASE
    WHEN o.plan_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.plan_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.plan_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int     AS plan_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.actual_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.actual_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.actual_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.actual_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.actual_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.actual_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.actual_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.actual_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.actual_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int   AS actual_week,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int            AS plan_year,
  EXTRACT(QUARTER FROM (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int         AS plan_quarter,
  to_char((
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
), 'YYYY-MM')                AS plan_month,
  EXTRACT(WEEK FROM (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int            AS plan_week_of_year,
  to_char((
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
), 'IYYY-"W"IW')            AS plan_iso_week,

  EXTRACT(YEAR FROM (
  CASE
    WHEN o.actual_date IS NULL THEN NULL
    WHEN btrim(cast(o.actual_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.actual_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.actual_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.actual_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int          AS actual_year,
  to_char((
  CASE
    WHEN o.actual_date IS NULL THEN NULL
    WHEN btrim(cast(o.actual_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.actual_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.actual_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.actual_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
), 'YYYY-MM')              AS actual_month,

  -- === 衍生字段 ===
  CASE
    WHEN (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) IS NOT NULL
     AND (
  CASE
    WHEN o.actual_date IS NULL THEN NULL
    WHEN btrim(cast(o.actual_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.actual_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.actual_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.actual_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) IS NOT NULL
    THEN ((
  CASE
    WHEN o.actual_date IS NULL THEN NULL
    WHEN btrim(cast(o.actual_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.actual_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.actual_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.actual_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) - (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int
  END AS delay_days,

  CASE
    WHEN (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) IS NOT NULL
     AND (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) <= current_date
    THEN true
    ELSE false
  END AS is_due,

  'ods_project_subject_domain'::text AS source_table,
  now() AS etl_time

FROM ods_project_subject_domain o
LEFT JOIN dim_completion_status cs
  ON cs.code = (
  CASE
    WHEN o.completion_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.completion_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.completion_status AS text)), '')
  END
)
LEFT JOIN dim_node_type nt
  ON nt.code = (
  CASE
    WHEN o.node_type IS NULL THEN NULL
    WHEN upper(btrim(cast(o.node_type AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.node_type AS text)), '')
  END
)
WHERE btrim(COALESCE(o.project_no, '')) != ''
  AND btrim(COALESCE(o.plan_date, '')) != '';

-- ── pm_dim_major_project ──
DROP TABLE IF EXISTS pm_dim_major_project CASCADE;
CREATE TABLE pm_dim_major_project AS
-- 从 ODS 节点数据自动推导项目维度，无需 seed
-- project_no IS the major project grouping key (客户确认：项目编号代表项目名称)
WITH raw AS (
  SELECT
    (
  CASE
    WHEN o.project_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_no AS text)), '')
  END
) AS project_no,
    (
  CASE
    WHEN o.project_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_no AS text)), '')
  END
) AS major_project_name,
    (
  CASE
    WHEN o.dept IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept AS text)), '')
  END
) AS dept,
    (
  CASE
    WHEN o.dept_leader IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept_leader AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept_leader AS text)), '')
  END
) AS dept_leader,
    (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) AS plan_date
  FROM ods_project_subject_domain o
  WHERE btrim(COALESCE(o.project_no, '')) != ''
),
agg AS (
  SELECT
    project_no,
    min(major_project_name) AS major_project_name,
    min(plan_date) AS start_date,
    max(plan_date) AS plan_end_date
  FROM raw
  GROUP BY project_no
),
first_dept AS (
  SELECT DISTINCT ON (project_no)
    project_no,
    dept,
    dept_leader
  FROM raw
  WHERE dept IS NOT NULL
  ORDER BY project_no, dept
)
SELECT
  a.project_no AS major_project_id,
  a.project_no AS major_project_code,
  a.major_project_name,
  '项目' AS project_level,
  d.dept AS owner_dept,
  d.dept_leader AS owner_leader,
  'A' AS priority_level,
  a.start_date,
  a.plan_end_date,
  '执行中' AS status,
  NULL::text AS remark
FROM agg a
LEFT JOIN first_dept d ON d.project_no = a.project_no;

-- ── pm_dim_subproject ──
DROP TABLE IF EXISTS pm_dim_subproject CASCADE;
CREATE TABLE pm_dim_subproject AS
-- 从 ODS 节点数据自动推导子项目维度，无需 seed
-- subsystem 直接作为子项目名称，不再用 "/" 分割
WITH raw AS (
  SELECT
    (
  CASE
    WHEN o.project_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_no AS text)), '')
  END
) AS project_no,
    (
  CASE
    WHEN o.subsystem IS NULL THEN NULL
    WHEN upper(btrim(cast(o.subsystem AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.subsystem AS text)), '')
  END
) AS subsystem,
    (
  CASE
    WHEN o.subsystem IS NULL THEN NULL
    WHEN upper(btrim(cast(o.subsystem AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.subsystem AS text)), '')
  END
) AS subproject_name,
    (
  CASE
    WHEN o.dept IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept AS text)), '')
  END
) AS dept,
    (
  CASE
    WHEN o.project_manager IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_manager AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_manager AS text)), '')
  END
) AS project_manager,
    (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) AS plan_date
  FROM ods_project_subject_domain o
  WHERE btrim(COALESCE(o.project_no, '')) != ''
    AND btrim(COALESCE(o.subsystem, '')) != ''
),
agg AS (
  SELECT
    project_no,
    subsystem,
    min(subproject_name) AS subproject_name,
    min(plan_date) AS plan_start_date,
    max(plan_date) AS plan_end_date
  FROM raw
  GROUP BY project_no, subsystem
),
first_dept AS (
  SELECT DISTINCT ON (project_no, subsystem)
    project_no,
    subsystem,
    dept,
    project_manager
  FROM raw
  WHERE dept IS NOT NULL
  ORDER BY project_no, subsystem, dept
)
SELECT
  md5(COALESCE(a.project_no, '') || '/' || COALESCE(a.subsystem, '')) AS subproject_id,
  a.project_no || '-' || left(COALESCE(a.subproject_name, ''), 8) AS subproject_code,
  a.subproject_name,
  a.project_no AS major_project_id,
  a.project_no,
  a.subsystem AS subsystem_name,
  d.dept AS owner_dept,
  NULL::text AS owner_user,
  d.project_manager,
  a.plan_start_date,
  a.plan_end_date,
  NULL::date AS actual_end_date,
  '执行中' AS status,
  'A' AS priority_level,
  NULL::text AS remark
FROM agg a
LEFT JOIN first_dept d ON d.project_no = a.project_no AND d.subsystem = a.subsystem;

-- ── pm_map_node_subject ──
DROP TABLE IF EXISTS pm_map_node_subject CASCADE;
CREATE TABLE pm_map_node_subject AS
-- 从 ODS 节点数据自动推导节点映射，无需 seed
SELECT DISTINCT
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.subsystem), '') || '|' ||
    COALESCE(btrim(o.node_task), '')
  ) AS map_id,
  (
  CASE
    WHEN o.project_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_no AS text)), '')
  END
) AS project_no,
  (
  CASE
    WHEN o.subsystem IS NULL THEN NULL
    WHEN upper(btrim(cast(o.subsystem AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.subsystem AS text)), '')
  END
) AS subsystem,
  (
  CASE
    WHEN o.node_task IS NULL THEN NULL
    WHEN upper(btrim(cast(o.node_task AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.node_task AS text)), '')
  END
) AS node_task,
  md5(COALESCE(btrim(o.project_no), '') || '/' || COALESCE(btrim(o.subsystem), '')) AS subproject_id,
  (
  CASE
    WHEN o.project_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_no AS text)), '')
  END
) AS major_project_id,
  CASE btrim(o.node_type)
    WHEN '里程碑节点' THEN 'milestone'
    WHEN '重大节点' THEN 'critical'
    WHEN '重要节点' THEN 'critical'
    ELSE 'routine'
  END AS node_category,
  NULL::text AS delay_reason_category,
  btrim(o.node_type) IN ('重大节点', '重要节点', '里程碑节点') AS is_key_node,
  btrim(o.node_type) = '里程碑节点' AS is_milestone,
  NULL::int AS sort_order,
  'auto' AS source_flag,
  NULL::text AS remark
FROM ods_project_subject_domain o
WHERE btrim(COALESCE(o.project_no, '')) != ''
  AND btrim(COALESCE(o.node_task, '')) != '';

-- ── biz_dwd_progress_measure ──
DROP TABLE IF EXISTS biz_dwd_progress_measure CASCADE;
CREATE TABLE biz_dwd_progress_measure AS
SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.node_task), '') || '|' ||
    COALESCE(btrim(o.plan_date), '') || '|' ||
    COALESCE(btrim(o.measure_title), '')
  ) AS measure_id,

  -- === 原始业务字段 ===
  (
  CASE
    WHEN o.project_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_no AS text)), '')
  END
)               AS project_no,
  (
  CASE
    WHEN o.subsystem IS NULL THEN NULL
    WHEN upper(btrim(cast(o.subsystem AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.subsystem AS text)), '')
  END
)                AS subsystem,
  (
  CASE
    WHEN o.node_task IS NULL THEN NULL
    WHEN upper(btrim(cast(o.node_task AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.node_task AS text)), '')
  END
)                AS node_task,
  (
  CASE
    WHEN o.completion_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.completion_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.completion_status AS text)), '')
  END
)        AS completion_status,
  (
  CASE
    WHEN o.measure_category IS NULL THEN NULL
    WHEN upper(btrim(cast(o.measure_category AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.measure_category AS text)), '')
  END
)         AS measure_category,
  (
  CASE
    WHEN o.measure_title IS NULL THEN NULL
    WHEN upper(btrim(cast(o.measure_title AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.measure_title AS text)), '')
  END
)            AS measure_title,
  (
  CASE
    WHEN o.follow_up_person IS NULL THEN NULL
    WHEN upper(btrim(cast(o.follow_up_person AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.follow_up_person AS text)), '')
  END
)         AS follow_up_person,
  (
  CASE
    WHEN o.main_recipient IS NULL THEN NULL
    WHEN upper(btrim(cast(o.main_recipient AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.main_recipient AS text)), '')
  END
)           AS main_recipient,
  (
  CASE
    WHEN o.cc_recipient IS NULL THEN NULL
    WHEN upper(btrim(cast(o.cc_recipient AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.cc_recipient AS text)), '')
  END
)             AS cc_recipient,
  (
  CASE
    WHEN o.closure_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_status AS text)), '')
  END
)           AS closure_status,
  (
  CASE
    WHEN o.closure_deliverable_type IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_deliverable_type AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_deliverable_type AS text)), '')
  END
) AS closure_deliverable_type,
  (
  CASE
    WHEN o.closure_deliverable IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_deliverable AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_deliverable AS text)), '')
  END
)      AS closure_deliverable,
  (
  CASE
    WHEN o.risk_content IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_content AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_content AS text)), '')
  END
)             AS risk_content,
  (
  CASE
    WHEN o.remark IS NULL THEN NULL
    WHEN upper(btrim(cast(o.remark AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.remark AS text)), '')
  END
)                   AS remark,
  (
  CASE
    WHEN o.filled_by IS NULL THEN NULL
    WHEN upper(btrim(cast(o.filled_by AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.filled_by AS text)), '')
  END
)                AS filled_by,

  -- === 日期解析 ===
  (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)                   AS plan_date,
  (
  CASE
    WHEN o.follow_up_date IS NULL THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.follow_up_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.follow_up_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.follow_up_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)              AS follow_up_date,
  (
  CASE
    WHEN o.final_closure_date IS NULL THEN NULL
    WHEN btrim(cast(o.final_closure_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.final_closure_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.final_closure_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.final_closure_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)          AS final_closure_date,
  (
  CASE
    WHEN o.last_update_time IS NULL THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.last_update_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.last_update_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.last_update_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)            AS last_update_time,

  -- === 周数 ===
  (
  CASE
    WHEN (
  CASE
    WHEN o.plan_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.plan_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.plan_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int           AS plan_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.follow_up_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.follow_up_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.follow_up_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.follow_up_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.follow_up_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.follow_up_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.follow_up_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.follow_up_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.follow_up_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int      AS follow_up_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.final_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.final_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.final_closure_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.final_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.final_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.final_closure_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.final_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.final_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.final_closure_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int  AS final_closure_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int    AS last_update_week,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int            AS plan_year,
  EXTRACT(QUARTER FROM (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int         AS plan_quarter,
  to_char((
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
), 'YYYY-MM')                AS plan_month,

  EXTRACT(YEAR FROM (
  CASE
    WHEN o.follow_up_date IS NULL THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.follow_up_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.follow_up_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.follow_up_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int       AS follow_up_year,
  to_char((
  CASE
    WHEN o.follow_up_date IS NULL THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.follow_up_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.follow_up_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.follow_up_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
), 'YYYY-MM')           AS follow_up_month,

  -- === 衍生字段 ===
  CASE
    WHEN (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) IS NOT NULL
     AND (
  CASE
    WHEN o.follow_up_date IS NULL THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.follow_up_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.follow_up_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.follow_up_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) IS NOT NULL
    THEN ((
  CASE
    WHEN o.follow_up_date IS NULL THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.follow_up_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.follow_up_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.follow_up_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) - (
  CASE
    WHEN o.plan_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int
  END AS plan_to_followup_days,

  CASE
    WHEN (
  CASE
    WHEN o.follow_up_date IS NULL THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.follow_up_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.follow_up_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.follow_up_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) IS NOT NULL
     AND (
  CASE
    WHEN o.final_closure_date IS NULL THEN NULL
    WHEN btrim(cast(o.final_closure_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.final_closure_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.final_closure_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.final_closure_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) IS NOT NULL
    THEN ((
  CASE
    WHEN o.final_closure_date IS NULL THEN NULL
    WHEN btrim(cast(o.final_closure_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.final_closure_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.final_closure_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.final_closure_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) - (
  CASE
    WHEN o.follow_up_date IS NULL THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.follow_up_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.follow_up_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.follow_up_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int
  END AS followup_to_closure_days,

  'ods_progress_measure'::text AS source_table,
  now() AS etl_time

FROM ods_progress_measure o
WHERE btrim(COALESCE(o.project_no, '')) != '';

-- ── biz_dwd_quality_issue ──
DROP TABLE IF EXISTS biz_dwd_quality_issue CASCADE;
CREATE TABLE biz_dwd_quality_issue AS
SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.issue_name), '') || '|' ||
    COALESCE(btrim(o.issue_date), '')
  ) AS issue_id,

  -- === 原始业务字段 ===
  (
  CASE
    WHEN o.project_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_no AS text)), '')
  END
)           AS project_no,
  (
  CASE
    WHEN o.subsystem IS NULL THEN NULL
    WHEN upper(btrim(cast(o.subsystem AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.subsystem AS text)), '')
  END
)            AS subsystem,
  (
  CASE
    WHEN o.issue_name IS NULL THEN NULL
    WHEN upper(btrim(cast(o.issue_name AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.issue_name AS text)), '')
  END
)           AS issue_name,
  (
  CASE
    WHEN o.dept IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept AS text)), '')
  END
)                 AS dept,
  (
  CASE
    WHEN o.team_leader IS NULL THEN NULL
    WHEN upper(btrim(cast(o.team_leader AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.team_leader AS text)), '')
  END
)          AS team_leader,
  (
  CASE
    WHEN o.dept_leader IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept_leader AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept_leader AS text)), '')
  END
)          AS dept_leader,
  (
  CASE
    WHEN o.issue_summary IS NULL THEN NULL
    WHEN upper(btrim(cast(o.issue_summary AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.issue_summary AS text)), '')
  END
)        AS issue_summary,
  (
  CASE
    WHEN o.issue_category IS NULL THEN NULL
    WHEN upper(btrim(cast(o.issue_category AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.issue_category AS text)), '')
  END
)       AS issue_category,
  (
  CASE
    WHEN o.zero_plan IS NULL THEN NULL
    WHEN upper(btrim(cast(o.zero_plan AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.zero_plan AS text)), '')
  END
)            AS zero_plan,
  (
  CASE
    WHEN o.zero_plan_synced IS NULL THEN NULL
    WHEN upper(btrim(cast(o.zero_plan_synced AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.zero_plan_synced AS text)), '')
  END
)     AS zero_plan_synced,
  (
  CASE
    WHEN o.status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.status AS text)), '')
  END
)               AS status,
  (
  CASE
    WHEN o.current_progress IS NULL THEN NULL
    WHEN upper(btrim(cast(o.current_progress AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.current_progress AS text)), '')
  END
)     AS current_progress,
  (
  CASE
    WHEN o.project_manager IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_manager AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_manager AS text)), '')
  END
)      AS project_manager,
  (
  CASE
    WHEN o.filled_by IS NULL THEN NULL
    WHEN upper(btrim(cast(o.filled_by AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.filled_by AS text)), '')
  END
)            AS filled_by,

  -- === 数值字段 ===
  (
  CASE
    WHEN (
  CASE
    WHEN o.new_plan_count IS NULL THEN NULL
    WHEN upper(btrim(cast(o.new_plan_count AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.new_plan_count AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.new_plan_count IS NULL THEN NULL
    WHEN upper(btrim(cast(o.new_plan_count AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.new_plan_count AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.new_plan_count IS NULL THEN NULL
    WHEN upper(btrim(cast(o.new_plan_count AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.new_plan_count AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int  AS new_plan_count,

  -- === 日期解析 ===
  (
  CASE
    WHEN o.issue_date IS NULL THEN NULL
    WHEN btrim(cast(o.issue_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.issue_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.issue_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.issue_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)              AS issue_date,
  (
  CASE
    WHEN o.zero_complete_date IS NULL THEN NULL
    WHEN btrim(cast(o.zero_complete_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.zero_complete_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.zero_complete_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.zero_complete_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.zero_complete_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.zero_complete_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.zero_complete_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)      AS zero_complete_date,
  (
  CASE
    WHEN o.last_update_time IS NULL THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.last_update_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.last_update_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.last_update_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)        AS last_update_time,

  -- === 周数字段 ===
  (
  CASE
    WHEN (
  CASE
    WHEN o.issue_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.issue_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.issue_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.issue_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.issue_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.issue_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.issue_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.issue_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.issue_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int              AS issue_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.zero_complete_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.zero_complete_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.zero_complete_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.zero_complete_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.zero_complete_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.zero_complete_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.zero_complete_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.zero_complete_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.zero_complete_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int      AS zero_complete_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int        AS last_update_week,

  -- === 闭环标志 ===
  CASE
    WHEN btrim(COALESCE((
  CASE
    WHEN o.status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.status AS text)), '')
  END
), '')) IN ('已归零', '已闭环') THEN true
    ELSE false
  END AS is_closed,

  -- === 归零计划标志 ===
  CASE
    WHEN btrim(COALESCE((
  CASE
    WHEN o.zero_plan IS NULL THEN NULL
    WHEN upper(btrim(cast(o.zero_plan AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.zero_plan AS text)), '')
  END
), '')) IN ('无', '') THEN false
    WHEN (
  CASE
    WHEN o.zero_plan IS NULL THEN NULL
    WHEN upper(btrim(cast(o.zero_plan AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.zero_plan AS text)), '')
  END
) IS NULL THEN false
    ELSE true
  END AS has_zero_plan,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM (
  CASE
    WHEN o.issue_date IS NULL THEN NULL
    WHEN btrim(cast(o.issue_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.issue_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.issue_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.issue_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int     AS issue_year,
  EXTRACT(QUARTER FROM (
  CASE
    WHEN o.issue_date IS NULL THEN NULL
    WHEN btrim(cast(o.issue_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.issue_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.issue_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.issue_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int  AS issue_quarter,
  to_char((
  CASE
    WHEN o.issue_date IS NULL THEN NULL
    WHEN btrim(cast(o.issue_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.issue_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.issue_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.issue_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
), 'YYYY-MM')         AS issue_month,

  -- === 滞留天数 ===
  CASE
    WHEN (
  CASE
    WHEN o.issue_date IS NULL THEN NULL
    WHEN btrim(cast(o.issue_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.issue_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.issue_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.issue_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) IS NOT NULL
     AND btrim(COALESCE((
  CASE
    WHEN o.status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.status AS text)), '')
  END
), '')) NOT IN ('已归零', '已闭环')
    THEN (current_date - (
  CASE
    WHEN o.issue_date IS NULL THEN NULL
    WHEN btrim(cast(o.issue_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.issue_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.issue_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.issue_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int
    ELSE 0
  END AS pending_days,

  'ods_quality_issue'::text AS source_table,
  now() AS etl_time

FROM ods_quality_issue o
WHERE o.project_no IS NOT NULL
  AND btrim(COALESCE(o.project_no, '')) != '';

-- ── biz_dwd_quality_measure ──
DROP TABLE IF EXISTS biz_dwd_quality_measure CASCADE;
CREATE TABLE biz_dwd_quality_measure AS
SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.issue_name), '') || '|' ||
    COALESCE(btrim(o.measure_title), '') || '|' ||
    COALESCE(btrim(o.follow_up_date), '')
  ) AS measure_id,

  -- === 原始业务字段（质量问题部分） ===
  (
  CASE
    WHEN o.project_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_no AS text)), '')
  END
)           AS project_no,
  (
  CASE
    WHEN o.subsystem IS NULL THEN NULL
    WHEN upper(btrim(cast(o.subsystem AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.subsystem AS text)), '')
  END
)            AS subsystem,
  (
  CASE
    WHEN o.issue_name IS NULL THEN NULL
    WHEN upper(btrim(cast(o.issue_name AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.issue_name AS text)), '')
  END
)           AS issue_name,
  (
  CASE
    WHEN o.dept IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept AS text)), '')
  END
)                 AS dept,
  (
  CASE
    WHEN o.team_leader IS NULL THEN NULL
    WHEN upper(btrim(cast(o.team_leader AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.team_leader AS text)), '')
  END
)          AS team_leader,
  (
  CASE
    WHEN o.dept_leader IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept_leader AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept_leader AS text)), '')
  END
)          AS dept_leader,
  (
  CASE
    WHEN o.issue_summary IS NULL THEN NULL
    WHEN upper(btrim(cast(o.issue_summary AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.issue_summary AS text)), '')
  END
)        AS issue_summary,
  (
  CASE
    WHEN o.issue_category IS NULL THEN NULL
    WHEN upper(btrim(cast(o.issue_category AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.issue_category AS text)), '')
  END
)       AS issue_category,
  (
  CASE
    WHEN o.zero_plan IS NULL THEN NULL
    WHEN upper(btrim(cast(o.zero_plan AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.zero_plan AS text)), '')
  END
)            AS zero_plan,
  (
  CASE
    WHEN o.zero_plan_synced IS NULL THEN NULL
    WHEN upper(btrim(cast(o.zero_plan_synced AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.zero_plan_synced AS text)), '')
  END
)     AS zero_plan_synced,
  (
  CASE
    WHEN o.status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.status AS text)), '')
  END
)               AS status,
  (
  CASE
    WHEN o.current_progress IS NULL THEN NULL
    WHEN upper(btrim(cast(o.current_progress AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.current_progress AS text)), '')
  END
)     AS current_progress,
  (
  CASE
    WHEN o.project_manager IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_manager AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_manager AS text)), '')
  END
)      AS project_manager,

  -- === 跟进措施字段 ===
  (
  CASE
    WHEN o.measure_category IS NULL THEN NULL
    WHEN upper(btrim(cast(o.measure_category AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.measure_category AS text)), '')
  END
)         AS measure_category,
  (
  CASE
    WHEN o.measure_title IS NULL THEN NULL
    WHEN upper(btrim(cast(o.measure_title AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.measure_title AS text)), '')
  END
)            AS measure_title,
  (
  CASE
    WHEN o.follow_up_person IS NULL THEN NULL
    WHEN upper(btrim(cast(o.follow_up_person AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.follow_up_person AS text)), '')
  END
)         AS follow_up_person,
  (
  CASE
    WHEN o.main_recipient IS NULL THEN NULL
    WHEN upper(btrim(cast(o.main_recipient AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.main_recipient AS text)), '')
  END
)           AS main_recipient,
  (
  CASE
    WHEN o.cc_recipient IS NULL THEN NULL
    WHEN upper(btrim(cast(o.cc_recipient AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.cc_recipient AS text)), '')
  END
)             AS cc_recipient,
  (
  CASE
    WHEN o.closure_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_status AS text)), '')
  END
)           AS closure_status,
  (
  CASE
    WHEN o.closure_deliverable_type IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_deliverable_type AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_deliverable_type AS text)), '')
  END
) AS closure_deliverable_type,
  (
  CASE
    WHEN o.closure_deliverable IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_deliverable AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_deliverable AS text)), '')
  END
)      AS closure_deliverable,
  (
  CASE
    WHEN o.risk_content IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_content AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_content AS text)), '')
  END
)             AS risk_content,
  (
  CASE
    WHEN o.remark IS NULL THEN NULL
    WHEN upper(btrim(cast(o.remark AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.remark AS text)), '')
  END
)                   AS remark,
  (
  CASE
    WHEN o.filled_by IS NULL THEN NULL
    WHEN upper(btrim(cast(o.filled_by AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.filled_by AS text)), '')
  END
)                AS filled_by,

  -- === 数值字段 ===
  (
  CASE
    WHEN (
  CASE
    WHEN o.new_plan_count IS NULL THEN NULL
    WHEN upper(btrim(cast(o.new_plan_count AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.new_plan_count AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.new_plan_count IS NULL THEN NULL
    WHEN upper(btrim(cast(o.new_plan_count AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.new_plan_count AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.new_plan_count IS NULL THEN NULL
    WHEN upper(btrim(cast(o.new_plan_count AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.new_plan_count AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int      AS new_plan_count,

  -- === 日期解析 ===
  (
  CASE
    WHEN o.issue_date IS NULL THEN NULL
    WHEN btrim(cast(o.issue_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.issue_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.issue_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.issue_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.issue_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)              AS issue_date,
  (
  CASE
    WHEN o.follow_up_date IS NULL THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.follow_up_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.follow_up_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.follow_up_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)          AS follow_up_date,
  (
  CASE
    WHEN o.final_closure_date IS NULL THEN NULL
    WHEN btrim(cast(o.final_closure_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.final_closure_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.final_closure_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.final_closure_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)      AS final_closure_date,
  (
  CASE
    WHEN o.last_update_time IS NULL THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.last_update_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.last_update_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.last_update_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)        AS last_update_time,

  -- === 周数字段 ===
  (
  CASE
    WHEN (
  CASE
    WHEN o.issue_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.issue_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.issue_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.issue_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.issue_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.issue_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.issue_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.issue_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.issue_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int              AS issue_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.follow_up_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.follow_up_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.follow_up_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.follow_up_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.follow_up_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.follow_up_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.follow_up_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.follow_up_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.follow_up_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int          AS follow_up_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.final_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.final_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.final_closure_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.final_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.final_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.final_closure_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.final_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.final_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.final_closure_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int      AS final_closure_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int        AS last_update_week,

  -- === 闭环标志 ===
  CASE
    WHEN btrim(COALESCE((
  CASE
    WHEN o.closure_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_status AS text)), '')
  END
), '')) = '已闭环' THEN true
    ELSE false
  END AS is_closed,

  'ods_quality_measure'::text AS source_table,
  now() AS etl_time

FROM ods_quality_measure o
WHERE o.project_no IS NOT NULL
  AND btrim(COALESCE(o.project_no, '')) != '';

-- ── biz_dwd_tech_state ──
DROP TABLE IF EXISTS biz_dwd_tech_state CASCADE;
CREATE TABLE biz_dwd_tech_state AS
SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.tech_state_name), '') || '|' ||
    COALESCE(btrim(o.change_item), '')
  ) AS tech_state_id,

  -- === 原始业务字段 ===
  (
  CASE
    WHEN o.project_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_no AS text)), '')
  END
)               AS project_no,
  (
  CASE
    WHEN o.tech_state_name IS NULL THEN NULL
    WHEN upper(btrim(cast(o.tech_state_name AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.tech_state_name AS text)), '')
  END
)          AS tech_state_name,
  (
  CASE
    WHEN o.change_item IS NULL THEN NULL
    WHEN upper(btrim(cast(o.change_item AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.change_item AS text)), '')
  END
)              AS change_item,
  (
  CASE
    WHEN o.owner IS NULL THEN NULL
    WHEN upper(btrim(cast(o.owner AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.owner AS text)), '')
  END
)                    AS owner,
  (
  CASE
    WHEN o.dept IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept AS text)), '')
  END
)                     AS dept,
  (
  CASE
    WHEN o.dept_leader IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept_leader AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept_leader AS text)), '')
  END
)              AS dept_leader,
  (
  CASE
    WHEN o.completion_signature IS NULL THEN NULL
    WHEN upper(btrim(cast(o.completion_signature AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.completion_signature AS text)), '')
  END
)     AS completion_signature,
  (
  CASE
    WHEN o.change_reason IS NULL THEN NULL
    WHEN upper(btrim(cast(o.change_reason AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.change_reason AS text)), '')
  END
)            AS change_reason,
  (
  CASE
    WHEN o.change_category IS NULL THEN NULL
    WHEN upper(btrim(cast(o.change_category AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.change_category AS text)), '')
  END
)          AS change_category,
  (
  CASE
    WHEN o.plan_synced IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_synced AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_synced AS text)), '')
  END
)              AS plan_synced,
  (
  CASE
    WHEN o.review_situation IS NULL THEN NULL
    WHEN upper(btrim(cast(o.review_situation AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.review_situation AS text)), '')
  END
)         AS review_situation,
  (
  CASE
    WHEN o.affected_files IS NULL THEN NULL
    WHEN upper(btrim(cast(o.affected_files AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.affected_files AS text)), '')
  END
)           AS affected_files,
  (
  CASE
    WHEN o.affected_objects IS NULL THEN NULL
    WHEN upper(btrim(cast(o.affected_objects AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.affected_objects AS text)), '')
  END
)         AS affected_objects,
  (
  CASE
    WHEN o.file_signature_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.file_signature_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.file_signature_status AS text)), '')
  END
)    AS file_signature_status,
  (
  CASE
    WHEN o.reform_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.reform_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.reform_status AS text)), '')
  END
)            AS reform_status,
  (
  CASE
    WHEN o.project_manager IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_manager AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_manager AS text)), '')
  END
)          AS project_manager,
  (
  CASE
    WHEN o.filled_by IS NULL THEN NULL
    WHEN upper(btrim(cast(o.filled_by AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.filled_by AS text)), '')
  END
)                AS filled_by,
  (
  CASE
    WHEN o.remark IS NULL THEN NULL
    WHEN upper(btrim(cast(o.remark AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.remark AS text)), '')
  END
)                   AS remark,

  -- === 数值字段 ===
  (
  CASE
    WHEN (
  CASE
    WHEN o.new_plan_count IS NULL THEN NULL
    WHEN upper(btrim(cast(o.new_plan_count AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.new_plan_count AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.new_plan_count IS NULL THEN NULL
    WHEN upper(btrim(cast(o.new_plan_count AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.new_plan_count AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.new_plan_count IS NULL THEN NULL
    WHEN upper(btrim(cast(o.new_plan_count AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.new_plan_count AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int      AS new_plan_count,

  -- === 日期解析 ===
  (
  CASE
    WHEN o.change_submit_time IS NULL THEN NULL
    WHEN btrim(cast(o.change_submit_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.change_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.change_submit_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.change_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.change_submit_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.change_submit_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.change_submit_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)          AS change_submit_time,
  (
  CASE
    WHEN o.signature_closure_date IS NULL THEN NULL
    WHEN btrim(cast(o.signature_closure_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.signature_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.signature_closure_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.signature_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.signature_closure_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.signature_closure_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.signature_closure_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)      AS signature_closure_date,
  (
  CASE
    WHEN o.plan_file_closure_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_file_closure_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_file_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_file_closure_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_file_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_file_closure_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_file_closure_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_file_closure_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)      AS plan_file_closure_date,
  (
  CASE
    WHEN o.plan_reform_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_reform_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_reform_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_reform_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_reform_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_reform_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_reform_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_reform_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)            AS plan_reform_date,
  (
  CASE
    WHEN o.file_signature_date IS NULL THEN NULL
    WHEN btrim(cast(o.file_signature_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.file_signature_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.file_signature_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.file_signature_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.file_signature_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.file_signature_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.file_signature_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)         AS file_signature_date,
  (
  CASE
    WHEN o.reform_date IS NULL THEN NULL
    WHEN btrim(cast(o.reform_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.reform_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.reform_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.reform_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.reform_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.reform_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.reform_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)                 AS reform_date,
  (
  CASE
    WHEN o.last_update_time IS NULL THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.last_update_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.last_update_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.last_update_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)            AS last_update_time,

  -- === 周数字段 ===
  (
  CASE
    WHEN (
  CASE
    WHEN o.change_submit_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.change_submit_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.change_submit_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.change_submit_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.change_submit_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.change_submit_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.change_submit_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.change_submit_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.change_submit_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int          AS change_submit_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.signature_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.signature_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.signature_closure_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.signature_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.signature_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.signature_closure_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.signature_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.signature_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.signature_closure_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int      AS signature_closure_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.plan_file_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_file_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_file_closure_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.plan_file_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_file_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_file_closure_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.plan_file_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_file_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_file_closure_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int      AS plan_file_closure_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.plan_reform_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_reform_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_reform_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.plan_reform_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_reform_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_reform_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.plan_reform_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_reform_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_reform_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int            AS plan_reform_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.file_signature_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.file_signature_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.file_signature_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.file_signature_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.file_signature_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.file_signature_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.file_signature_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.file_signature_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.file_signature_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int         AS file_signature_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.reform_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.reform_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.reform_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.reform_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.reform_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.reform_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.reform_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.reform_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.reform_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int                 AS reform_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int            AS last_update_week,

  -- === 签署完成标志 ===
  CASE
    WHEN btrim(COALESCE((
  CASE
    WHEN o.completion_signature IS NULL THEN NULL
    WHEN upper(btrim(cast(o.completion_signature AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.completion_signature AS text)), '')
  END
), '')) = '是' THEN true
    ELSE false
  END AS is_signature_completed,

  -- === 文件签署完成标志 ===
  CASE
    WHEN btrim(COALESCE((
  CASE
    WHEN o.file_signature_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.file_signature_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.file_signature_status AS text)), '')
  END
), '')) = '已完成' THEN true
    ELSE false
  END AS is_file_signed,

  -- === 整改落实完成标志 ===
  CASE
    WHEN btrim(COALESCE((
  CASE
    WHEN o.reform_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.reform_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.reform_status AS text)), '')
  END
), '')) = '已完成' THEN true
    ELSE false
  END AS is_reformed,

  'ods_tech_state'::text AS source_table,
  now() AS etl_time

FROM ods_tech_state o
WHERE o.project_no IS NOT NULL
  AND btrim(COALESCE(o.project_no, '')) != '';

-- ── biz_dwd_tech_state_measure ──
DROP TABLE IF EXISTS biz_dwd_tech_state_measure CASCADE;
CREATE TABLE biz_dwd_tech_state_measure AS
SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.tech_state_name), '') || '|' ||
    COALESCE(btrim(o.measure_title), '') || '|' ||
    COALESCE(btrim(o.follow_up_date), '')
  ) AS measure_id,

  -- === 技术状态主体字段 ===
  (
  CASE
    WHEN o.project_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_no AS text)), '')
  END
)               AS project_no,
  (
  CASE
    WHEN o.tech_state_name IS NULL THEN NULL
    WHEN upper(btrim(cast(o.tech_state_name AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.tech_state_name AS text)), '')
  END
)          AS tech_state_name,
  (
  CASE
    WHEN o.change_item IS NULL THEN NULL
    WHEN upper(btrim(cast(o.change_item AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.change_item AS text)), '')
  END
)              AS change_item,
  (
  CASE
    WHEN o.owner IS NULL THEN NULL
    WHEN upper(btrim(cast(o.owner AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.owner AS text)), '')
  END
)                    AS owner,
  (
  CASE
    WHEN o.dept IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept AS text)), '')
  END
)                     AS dept,
  (
  CASE
    WHEN o.dept_leader IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept_leader AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept_leader AS text)), '')
  END
)              AS dept_leader,
  (
  CASE
    WHEN o.completion_signature IS NULL THEN NULL
    WHEN upper(btrim(cast(o.completion_signature AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.completion_signature AS text)), '')
  END
)     AS completion_signature,
  (
  CASE
    WHEN o.change_reason IS NULL THEN NULL
    WHEN upper(btrim(cast(o.change_reason AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.change_reason AS text)), '')
  END
)            AS change_reason,
  (
  CASE
    WHEN o.change_category IS NULL THEN NULL
    WHEN upper(btrim(cast(o.change_category AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.change_category AS text)), '')
  END
)          AS change_category,
  (
  CASE
    WHEN o.plan_synced IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_synced AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_synced AS text)), '')
  END
)              AS plan_synced,
  (
  CASE
    WHEN o.affected_files IS NULL THEN NULL
    WHEN upper(btrim(cast(o.affected_files AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.affected_files AS text)), '')
  END
)           AS affected_files,
  (
  CASE
    WHEN o.affected_objects IS NULL THEN NULL
    WHEN upper(btrim(cast(o.affected_objects AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.affected_objects AS text)), '')
  END
)         AS affected_objects,
  (
  CASE
    WHEN o.file_signature_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.file_signature_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.file_signature_status AS text)), '')
  END
)    AS file_signature_status,
  (
  CASE
    WHEN o.reform_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.reform_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.reform_status AS text)), '')
  END
)            AS reform_status,
  (
  CASE
    WHEN o.project_manager IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_manager AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_manager AS text)), '')
  END
)          AS project_manager,

  -- === 跟进措施字段 ===
  (
  CASE
    WHEN o.measure_category IS NULL THEN NULL
    WHEN upper(btrim(cast(o.measure_category AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.measure_category AS text)), '')
  END
)         AS measure_category,
  (
  CASE
    WHEN o.measure_title IS NULL THEN NULL
    WHEN upper(btrim(cast(o.measure_title AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.measure_title AS text)), '')
  END
)            AS measure_title,
  (
  CASE
    WHEN o.follow_up_person IS NULL THEN NULL
    WHEN upper(btrim(cast(o.follow_up_person AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.follow_up_person AS text)), '')
  END
)         AS follow_up_person,
  (
  CASE
    WHEN o.main_recipient IS NULL THEN NULL
    WHEN upper(btrim(cast(o.main_recipient AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.main_recipient AS text)), '')
  END
)           AS main_recipient,
  (
  CASE
    WHEN o.cc_recipient IS NULL THEN NULL
    WHEN upper(btrim(cast(o.cc_recipient AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.cc_recipient AS text)), '')
  END
)             AS cc_recipient,
  (
  CASE
    WHEN o.closure_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_status AS text)), '')
  END
)           AS closure_status,
  (
  CASE
    WHEN o.closure_deliverable_type IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_deliverable_type AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_deliverable_type AS text)), '')
  END
) AS closure_deliverable_type,
  (
  CASE
    WHEN o.closure_deliverable IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_deliverable AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_deliverable AS text)), '')
  END
)      AS closure_deliverable,
  (
  CASE
    WHEN o.risk_content IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_content AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_content AS text)), '')
  END
)             AS risk_content,
  (
  CASE
    WHEN o.remark IS NULL THEN NULL
    WHEN upper(btrim(cast(o.remark AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.remark AS text)), '')
  END
)                   AS remark,
  (
  CASE
    WHEN o.filled_by IS NULL THEN NULL
    WHEN upper(btrim(cast(o.filled_by AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.filled_by AS text)), '')
  END
)                AS filled_by,

  -- === 数值字段 ===
  (
  CASE
    WHEN (
  CASE
    WHEN o.new_plan_count IS NULL THEN NULL
    WHEN upper(btrim(cast(o.new_plan_count AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.new_plan_count AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.new_plan_count IS NULL THEN NULL
    WHEN upper(btrim(cast(o.new_plan_count AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.new_plan_count AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.new_plan_count IS NULL THEN NULL
    WHEN upper(btrim(cast(o.new_plan_count AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.new_plan_count AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int      AS new_plan_count,

  -- === 日期解析 ===
  (
  CASE
    WHEN o.change_submit_time IS NULL THEN NULL
    WHEN btrim(cast(o.change_submit_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.change_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.change_submit_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.change_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.change_submit_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.change_submit_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.change_submit_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)          AS change_submit_time,
  (
  CASE
    WHEN o.signature_closure_date IS NULL THEN NULL
    WHEN btrim(cast(o.signature_closure_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.signature_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.signature_closure_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.signature_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.signature_closure_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.signature_closure_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.signature_closure_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)      AS signature_closure_date,
  (
  CASE
    WHEN o.plan_file_closure_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_file_closure_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_file_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_file_closure_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_file_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_file_closure_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_file_closure_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_file_closure_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)      AS plan_file_closure_date,
  (
  CASE
    WHEN o.plan_reform_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_reform_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_reform_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_reform_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_reform_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_reform_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_reform_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_reform_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)            AS plan_reform_date,
  (
  CASE
    WHEN o.follow_up_date IS NULL THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.follow_up_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.follow_up_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.follow_up_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)              AS follow_up_date,
  (
  CASE
    WHEN o.final_closure_date IS NULL THEN NULL
    WHEN btrim(cast(o.final_closure_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.final_closure_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.final_closure_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.final_closure_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)          AS final_closure_date,
  (
  CASE
    WHEN o.last_update_time IS NULL THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.last_update_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.last_update_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.last_update_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)            AS last_update_time,

  -- === 周数字段 ===
  (
  CASE
    WHEN (
  CASE
    WHEN o.change_submit_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.change_submit_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.change_submit_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.change_submit_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.change_submit_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.change_submit_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.change_submit_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.change_submit_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.change_submit_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int          AS change_submit_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.signature_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.signature_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.signature_closure_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.signature_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.signature_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.signature_closure_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.signature_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.signature_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.signature_closure_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int      AS signature_closure_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.plan_file_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_file_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_file_closure_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.plan_file_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_file_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_file_closure_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.plan_file_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_file_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_file_closure_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int      AS plan_file_closure_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.plan_reform_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_reform_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_reform_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.plan_reform_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_reform_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_reform_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.plan_reform_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_reform_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_reform_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int            AS plan_reform_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.follow_up_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.follow_up_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.follow_up_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.follow_up_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.follow_up_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.follow_up_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.follow_up_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.follow_up_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.follow_up_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int              AS follow_up_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.final_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.final_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.final_closure_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.final_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.final_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.final_closure_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.final_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.final_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.final_closure_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int          AS final_closure_week,
  (
  CASE
    WHEN (
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
) IS NULL THEN NULL
    WHEN regexp_replace((
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
      THEN regexp_replace((
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
), ',', '', 'g')::numeric
    ELSE NULL
  END
)::int            AS last_update_week,

  -- === 闭环标志 ===
  CASE
    WHEN btrim(COALESCE((
  CASE
    WHEN o.closure_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_status AS text)), '')
  END
), '')) = '已闭环' THEN true
    ELSE false
  END AS is_closed,

  'ods_tech_state_measure'::text AS source_table,
  now() AS etl_time

FROM ods_tech_state_measure o
WHERE o.project_no IS NOT NULL
  AND btrim(COALESCE(o.project_no, '')) != '';

-- ── biz_dwd_risk_info ──
DROP TABLE IF EXISTS biz_dwd_risk_info CASCADE;
CREATE TABLE biz_dwd_risk_info AS
SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.risk_name), '') || '|' ||
    COALESCE(btrim(o.risk_submit_time), '')
  ) AS risk_id,

  -- === 原始业务字段 ===
  (
  CASE
    WHEN o.project_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_no AS text)), '')
  END
)              AS project_no,
  (
  CASE
    WHEN o.risk_name IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_name AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_name AS text)), '')
  END
)               AS risk_name,
  (
  CASE
    WHEN o.subsystem IS NULL THEN NULL
    WHEN upper(btrim(cast(o.subsystem AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.subsystem AS text)), '')
  END
)               AS subsystem,
  (
  CASE
    WHEN o.belonging_unit IS NULL THEN NULL
    WHEN upper(btrim(cast(o.belonging_unit AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.belonging_unit AS text)), '')
  END
)          AS belonging_unit,
  (
  CASE
    WHEN o.risk_description IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_description AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_description AS text)), '')
  END
)        AS risk_description,
  (
  CASE
    WHEN o.risk_phase IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_phase AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_phase AS text)), '')
  END
)              AS risk_phase,
  (
  CASE
    WHEN o.risk_category IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_category AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_category AS text)), '')
  END
)           AS risk_category,
  (
  CASE
    WHEN o.risk_level IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_level AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_level AS text)), '')
  END
)              AS risk_level,
  (
  CASE
    WHEN o.impact_scope IS NULL THEN NULL
    WHEN upper(btrim(cast(o.impact_scope AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.impact_scope AS text)), '')
  END
)            AS impact_scope,
  (
  CASE
    WHEN o.response_measure IS NULL THEN NULL
    WHEN upper(btrim(cast(o.response_measure AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.response_measure AS text)), '')
  END
)        AS response_measure,
  (
  CASE
    WHEN o.monthly_control_plan IS NULL THEN NULL
    WHEN upper(btrim(cast(o.monthly_control_plan AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.monthly_control_plan AS text)), '')
  END
)    AS monthly_control_plan,
  (
  CASE
    WHEN o.weekly_release_plan IS NULL THEN NULL
    WHEN upper(btrim(cast(o.weekly_release_plan AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.weekly_release_plan AS text)), '')
  END
)     AS weekly_release_plan,
  (
  CASE
    WHEN o.release_plan_synced IS NULL THEN NULL
    WHEN upper(btrim(cast(o.release_plan_synced AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.release_plan_synced AS text)), '')
  END
)     AS release_plan_synced,
  (
  CASE
    WHEN o.new_plan_count IS NULL THEN NULL
    WHEN upper(btrim(cast(o.new_plan_count AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.new_plan_count AS text)), '')
  END
)          AS new_plan_count,
  (
  CASE
    WHEN o.progress_situation IS NULL THEN NULL
    WHEN upper(btrim(cast(o.progress_situation AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.progress_situation AS text)), '')
  END
)      AS progress_situation,
  (
  CASE
    WHEN o.response_owner IS NULL THEN NULL
    WHEN upper(btrim(cast(o.response_owner AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.response_owner AS text)), '')
  END
)          AS response_owner,
  (
  CASE
    WHEN o.control_owner IS NULL THEN NULL
    WHEN upper(btrim(cast(o.control_owner AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.control_owner AS text)), '')
  END
)           AS control_owner,
  (
  CASE
    WHEN o.dept IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept AS text)), '')
  END
)                    AS dept,
  (
  CASE
    WHEN o.risk_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_status AS text)), '')
  END
)             AS risk_status,
  (
  CASE
    WHEN o.remark IS NULL THEN NULL
    WHEN upper(btrim(cast(o.remark AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.remark AS text)), '')
  END
)                  AS remark,
  (
  CASE
    WHEN o.filled_by IS NULL THEN NULL
    WHEN upper(btrim(cast(o.filled_by AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.filled_by AS text)), '')
  END
)               AS filled_by,

  -- === 周数字段 ===
  (
  CASE
    WHEN o.risk_submit_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_submit_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_submit_week AS text)), '')
  END
)        AS risk_submit_week,
  (
  CASE
    WHEN o.final_release_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.final_release_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.final_release_week AS text)), '')
  END
)      AS final_release_week,
  (
  CASE
    WHEN o.progress_stat_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.progress_stat_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.progress_stat_week AS text)), '')
  END
)      AS progress_stat_week,
  (
  CASE
    WHEN o.risk_release_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_release_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_release_week AS text)), '')
  END
)       AS risk_release_week,
  (
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
)        AS last_update_week,

  -- === 风险等级标准化 ===
  CASE COALESCE((
  CASE
    WHEN o.risk_level IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_level AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_level AS text)), '')
  END
), '')
    WHEN '高' THEN 3
    WHEN '中' THEN 2
    WHEN '低' THEN 1
    ELSE 0
  END AS risk_rank,

  -- === 闭环标志 ===
  CASE
    WHEN btrim(COALESCE((
  CASE
    WHEN o.risk_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_status AS text)), '')
  END
), '')) = '已释放' THEN true
    ELSE false
  END AS is_released,

  -- === 日期解析 ===
  (
  CASE
    WHEN o.risk_submit_time IS NULL THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.risk_submit_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.risk_submit_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.risk_submit_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)           AS risk_submit_date,
  (
  CASE
    WHEN o.final_release_time IS NULL THEN NULL
    WHEN btrim(cast(o.final_release_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.final_release_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.final_release_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_release_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.final_release_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_release_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.final_release_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)         AS final_release_date,
  (
  CASE
    WHEN o.progress_stat_time IS NULL THEN NULL
    WHEN btrim(cast(o.progress_stat_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.progress_stat_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.progress_stat_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.progress_stat_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.progress_stat_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.progress_stat_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.progress_stat_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)         AS progress_stat_date,
  (
  CASE
    WHEN o.risk_release_date IS NULL THEN NULL
    WHEN btrim(cast(o.risk_release_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.risk_release_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.risk_release_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_release_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.risk_release_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_release_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.risk_release_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)          AS risk_release_date,
  (
  CASE
    WHEN o.last_update_time IS NULL THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.last_update_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.last_update_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.last_update_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)           AS last_update_time,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM (
  CASE
    WHEN o.risk_submit_time IS NULL THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.risk_submit_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.risk_submit_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.risk_submit_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int     AS submit_year,
  EXTRACT(QUARTER FROM (
  CASE
    WHEN o.risk_submit_time IS NULL THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.risk_submit_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.risk_submit_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.risk_submit_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int  AS submit_quarter,
  to_char((
  CASE
    WHEN o.risk_submit_time IS NULL THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.risk_submit_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.risk_submit_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.risk_submit_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
), 'YYYY-MM')         AS submit_month,

  -- === 滞留天数 ===
  CASE
    WHEN (
  CASE
    WHEN o.risk_submit_time IS NULL THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.risk_submit_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.risk_submit_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.risk_submit_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) IS NOT NULL
     AND btrim(COALESCE((
  CASE
    WHEN o.risk_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_status AS text)), '')
  END
), '')) != '已释放'
    THEN (current_date - (
  CASE
    WHEN o.risk_submit_time IS NULL THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.risk_submit_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.risk_submit_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.risk_submit_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int
    ELSE 0
  END AS pending_days,

  'ods_risk_info'::text AS source_table,
  now() AS etl_time

FROM ods_risk_info o
WHERE o.project_no IS NOT NULL;

-- ── biz_dwd_risk_measure ──
DROP TABLE IF EXISTS biz_dwd_risk_measure CASCADE;
CREATE TABLE biz_dwd_risk_measure AS
SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.risk_name), '') || '|' ||
    COALESCE(btrim(o.measure_title), '') || '|' ||
    COALESCE(btrim(o.follow_up_date), '')
  ) AS risk_measure_id,

  -- === 风险基本信息字段 ===
  (
  CASE
    WHEN o.project_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_no AS text)), '')
  END
)              AS project_no,
  (
  CASE
    WHEN o.risk_name IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_name AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_name AS text)), '')
  END
)               AS risk_name,
  (
  CASE
    WHEN o.subsystem IS NULL THEN NULL
    WHEN upper(btrim(cast(o.subsystem AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.subsystem AS text)), '')
  END
)               AS subsystem,
  (
  CASE
    WHEN o.belonging_unit IS NULL THEN NULL
    WHEN upper(btrim(cast(o.belonging_unit AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.belonging_unit AS text)), '')
  END
)          AS belonging_unit,
  (
  CASE
    WHEN o.risk_description IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_description AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_description AS text)), '')
  END
)        AS risk_description,
  (
  CASE
    WHEN o.risk_phase IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_phase AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_phase AS text)), '')
  END
)              AS risk_phase,
  (
  CASE
    WHEN o.risk_category IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_category AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_category AS text)), '')
  END
)           AS risk_category,
  (
  CASE
    WHEN o.risk_level IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_level AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_level AS text)), '')
  END
)              AS risk_level,
  (
  CASE
    WHEN o.impact_scope IS NULL THEN NULL
    WHEN upper(btrim(cast(o.impact_scope AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.impact_scope AS text)), '')
  END
)            AS impact_scope,
  (
  CASE
    WHEN o.response_measure IS NULL THEN NULL
    WHEN upper(btrim(cast(o.response_measure AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.response_measure AS text)), '')
  END
)        AS response_measure,
  (
  CASE
    WHEN o.monthly_control_plan IS NULL THEN NULL
    WHEN upper(btrim(cast(o.monthly_control_plan AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.monthly_control_plan AS text)), '')
  END
)    AS monthly_control_plan,
  (
  CASE
    WHEN o.weekly_release_plan IS NULL THEN NULL
    WHEN upper(btrim(cast(o.weekly_release_plan AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.weekly_release_plan AS text)), '')
  END
)     AS weekly_release_plan,
  (
  CASE
    WHEN o.release_plan_synced IS NULL THEN NULL
    WHEN upper(btrim(cast(o.release_plan_synced AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.release_plan_synced AS text)), '')
  END
)     AS release_plan_synced,
  (
  CASE
    WHEN o.new_plan_count IS NULL THEN NULL
    WHEN upper(btrim(cast(o.new_plan_count AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.new_plan_count AS text)), '')
  END
)          AS new_plan_count,
  (
  CASE
    WHEN o.progress_situation IS NULL THEN NULL
    WHEN upper(btrim(cast(o.progress_situation AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.progress_situation AS text)), '')
  END
)      AS progress_situation,
  (
  CASE
    WHEN o.response_owner IS NULL THEN NULL
    WHEN upper(btrim(cast(o.response_owner AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.response_owner AS text)), '')
  END
)          AS response_owner,
  (
  CASE
    WHEN o.control_owner IS NULL THEN NULL
    WHEN upper(btrim(cast(o.control_owner AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.control_owner AS text)), '')
  END
)           AS control_owner,
  (
  CASE
    WHEN o.dept IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept AS text)), '')
  END
)                    AS dept,
  (
  CASE
    WHEN o.risk_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_status AS text)), '')
  END
)             AS risk_status,
  (
  CASE
    WHEN o.project_manager IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_manager AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_manager AS text)), '')
  END
)         AS project_manager,
  (
  CASE
    WHEN o.remark IS NULL THEN NULL
    WHEN upper(btrim(cast(o.remark AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.remark AS text)), '')
  END
)                  AS remark,
  (
  CASE
    WHEN o.filled_by IS NULL THEN NULL
    WHEN upper(btrim(cast(o.filled_by AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.filled_by AS text)), '')
  END
)               AS filled_by,

  -- === 跟进措施字段 ===
  (
  CASE
    WHEN o.measure_category IS NULL THEN NULL
    WHEN upper(btrim(cast(o.measure_category AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.measure_category AS text)), '')
  END
)        AS measure_category,
  (
  CASE
    WHEN o.measure_title IS NULL THEN NULL
    WHEN upper(btrim(cast(o.measure_title AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.measure_title AS text)), '')
  END
)           AS measure_title,
  (
  CASE
    WHEN o.follow_up_person IS NULL THEN NULL
    WHEN upper(btrim(cast(o.follow_up_person AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.follow_up_person AS text)), '')
  END
)        AS follow_up_person,
  (
  CASE
    WHEN o.main_recipient IS NULL THEN NULL
    WHEN upper(btrim(cast(o.main_recipient AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.main_recipient AS text)), '')
  END
)          AS main_recipient,
  (
  CASE
    WHEN o.cc_recipient IS NULL THEN NULL
    WHEN upper(btrim(cast(o.cc_recipient AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.cc_recipient AS text)), '')
  END
)            AS cc_recipient,
  (
  CASE
    WHEN o.closure_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_status AS text)), '')
  END
)          AS closure_status,
  (
  CASE
    WHEN o.closure_deliverable_type IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_deliverable_type AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_deliverable_type AS text)), '')
  END
) AS closure_deliverable_type,
  (
  CASE
    WHEN o.closure_deliverable IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_deliverable AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_deliverable AS text)), '')
  END
)     AS closure_deliverable,
  (
  CASE
    WHEN o.risk_content IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_content AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_content AS text)), '')
  END
)            AS risk_content,

  -- === 周数字段 ===
  (
  CASE
    WHEN o.risk_submit_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_submit_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_submit_week AS text)), '')
  END
)        AS risk_submit_week,
  (
  CASE
    WHEN o.progress_stat_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.progress_stat_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.progress_stat_week AS text)), '')
  END
)      AS progress_stat_week,
  (
  CASE
    WHEN o.follow_up_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.follow_up_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.follow_up_week AS text)), '')
  END
)          AS follow_up_week,
  (
  CASE
    WHEN o.final_closure_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.final_closure_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.final_closure_week AS text)), '')
  END
)      AS final_closure_week,
  (
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
)        AS last_update_week,

  -- === 风险等级标准化 ===
  CASE COALESCE((
  CASE
    WHEN o.risk_level IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_level AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_level AS text)), '')
  END
), '')
    WHEN '高' THEN 3
    WHEN '中' THEN 2
    WHEN '低' THEN 1
    ELSE 0
  END AS risk_rank,

  -- === 闭环标志 ===
  CASE
    WHEN btrim(COALESCE((
  CASE
    WHEN o.closure_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_status AS text)), '')
  END
), '')) = '已闭环' THEN true
    ELSE false
  END AS is_closed,

  -- === 日期解析 ===
  (
  CASE
    WHEN o.risk_submit_time IS NULL THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.risk_submit_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.risk_submit_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.risk_submit_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)           AS risk_submit_date,
  (
  CASE
    WHEN o.final_release_time IS NULL THEN NULL
    WHEN btrim(cast(o.final_release_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.final_release_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.final_release_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_release_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.final_release_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_release_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.final_release_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)         AS final_release_date,
  (
  CASE
    WHEN o.progress_stat_time IS NULL THEN NULL
    WHEN btrim(cast(o.progress_stat_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.progress_stat_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.progress_stat_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.progress_stat_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.progress_stat_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.progress_stat_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.progress_stat_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)         AS progress_stat_date,
  (
  CASE
    WHEN o.follow_up_date IS NULL THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.follow_up_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.follow_up_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.follow_up_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)             AS follow_up_date,
  (
  CASE
    WHEN o.final_closure_date IS NULL THEN NULL
    WHEN btrim(cast(o.final_closure_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.final_closure_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.final_closure_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.final_closure_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.final_closure_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)         AS final_closure_date,
  (
  CASE
    WHEN o.last_update_time IS NULL THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.last_update_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.last_update_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.last_update_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)           AS last_update_time,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM (
  CASE
    WHEN o.risk_submit_time IS NULL THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.risk_submit_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.risk_submit_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.risk_submit_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int     AS submit_year,
  EXTRACT(QUARTER FROM (
  CASE
    WHEN o.risk_submit_time IS NULL THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.risk_submit_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.risk_submit_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.risk_submit_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int  AS submit_quarter,
  to_char((
  CASE
    WHEN o.risk_submit_time IS NULL THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.risk_submit_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.risk_submit_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.risk_submit_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.risk_submit_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
), 'YYYY-MM')         AS submit_month,

  -- === 滞留天数 ===
  CASE
    WHEN (
  CASE
    WHEN o.follow_up_date IS NULL THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.follow_up_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.follow_up_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.follow_up_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) IS NOT NULL
     AND btrim(COALESCE((
  CASE
    WHEN o.closure_status IS NULL THEN NULL
    WHEN upper(btrim(cast(o.closure_status AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.closure_status AS text)), '')
  END
), '')) != '已闭环'
    THEN (current_date - (
  CASE
    WHEN o.follow_up_date IS NULL THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.follow_up_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.follow_up_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.follow_up_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.follow_up_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int
    ELSE 0
  END AS pending_days,

  'ods_risk_measure'::text AS source_table,
  now() AS etl_time

FROM ods_risk_measure o
WHERE o.project_no IS NOT NULL;

-- ── biz_dwd_material_info ──
DROP TABLE IF EXISTS biz_dwd_material_info CASCADE;
CREATE TABLE biz_dwd_material_info AS
SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.pbs_no), '') || '|' ||
    COALESCE(btrim(o.supplier_name), '')
  ) AS material_id,

  -- === 原始业务字段 ===
  (
  CASE
    WHEN o.project_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.project_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.project_no AS text)), '')
  END
)              AS project_no,
  (
  CASE
    WHEN o.subsystem IS NULL THEN NULL
    WHEN upper(btrim(cast(o.subsystem AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.subsystem AS text)), '')
  END
)               AS subsystem,
  (
  CASE
    WHEN o.pbs_no IS NULL THEN NULL
    WHEN upper(btrim(cast(o.pbs_no AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.pbs_no AS text)), '')
  END
)                  AS pbs_no,
  (
  CASE
    WHEN o.pbs_name IS NULL THEN NULL
    WHEN upper(btrim(cast(o.pbs_name AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.pbs_name AS text)), '')
  END
)                AS pbs_name,
  (
  CASE
    WHEN o.self_or_outsource IS NULL THEN NULL
    WHEN upper(btrim(cast(o.self_or_outsource AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.self_or_outsource AS text)), '')
  END
)       AS self_or_outsource,
  (
  CASE
    WHEN o.supplier_name IS NULL THEN NULL
    WHEN upper(btrim(cast(o.supplier_name AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.supplier_name AS text)), '')
  END
)           AS supplier_name,
  (
  CASE
    WHEN o.is_long_cycle IS NULL THEN NULL
    WHEN upper(btrim(cast(o.is_long_cycle AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.is_long_cycle AS text)), '')
  END
)           AS is_long_cycle_raw,
  (
  CASE
    WHEN o.dept_owner IS NULL THEN NULL
    WHEN upper(btrim(cast(o.dept_owner AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.dept_owner AS text)), '')
  END
)              AS dept_owner,
  (
  CASE
    WHEN o.control_dept_owner IS NULL THEN NULL
    WHEN upper(btrim(cast(o.control_dept_owner AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.control_dept_owner AS text)), '')
  END
)      AS control_dept_owner,
  (
  CASE
    WHEN o.weekly_progress IS NULL THEN NULL
    WHEN upper(btrim(cast(o.weekly_progress AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.weekly_progress AS text)), '')
  END
)         AS weekly_progress,
  (
  CASE
    WHEN o.affects_major_node IS NULL THEN NULL
    WHEN upper(btrim(cast(o.affects_major_node AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.affects_major_node AS text)), '')
  END
)      AS affects_major_node,
  (
  CASE
    WHEN o.risk_level IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_level AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_level AS text)), '')
  END
)              AS risk_level,
  (
  CASE
    WHEN o.risk_content IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_content AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_content AS text)), '')
  END
)            AS risk_content,
  (
  CASE
    WHEN o.delay_impact IS NULL THEN NULL
    WHEN upper(btrim(cast(o.delay_impact AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.delay_impact AS text)), '')
  END
)            AS delay_impact,
  (
  CASE
    WHEN o.remark IS NULL THEN NULL
    WHEN upper(btrim(cast(o.remark AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.remark AS text)), '')
  END
)                  AS remark,

  -- === 周数字段 ===
  (
  CASE
    WHEN o.contract_negotiation_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.contract_negotiation_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.contract_negotiation_week AS text)), '')
  END
) AS contract_negotiation_week,
  (
  CASE
    WHEN o.contract_delivery_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.contract_delivery_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.contract_delivery_week AS text)), '')
  END
)    AS contract_delivery_week,
  (
  CASE
    WHEN o.actual_delivery_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.actual_delivery_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.actual_delivery_week AS text)), '')
  END
)      AS actual_delivery_week,
  (
  CASE
    WHEN o.plan_inspect_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.plan_inspect_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.plan_inspect_week AS text)), '')
  END
)         AS plan_inspect_week,
  (
  CASE
    WHEN o.complete_inspect_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.complete_inspect_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.complete_inspect_week AS text)), '')
  END
)     AS complete_inspect_week,
  (
  CASE
    WHEN o.install_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.install_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.install_week AS text)), '')
  END
)              AS install_week,
  (
  CASE
    WHEN o.last_update_week IS NULL THEN NULL
    WHEN upper(btrim(cast(o.last_update_week AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.last_update_week AS text)), '')
  END
)          AS last_update_week,

  -- === 布尔派生字段 ===
  CASE
    WHEN btrim(COALESCE((
  CASE
    WHEN o.is_long_cycle IS NULL THEN NULL
    WHEN upper(btrim(cast(o.is_long_cycle AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.is_long_cycle AS text)), '')
  END
), '')) = '是' THEN true
    ELSE false
  END AS is_long_cycle,

  CASE
    WHEN btrim(COALESCE((
  CASE
    WHEN o.self_or_outsource IS NULL THEN NULL
    WHEN upper(btrim(cast(o.self_or_outsource AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.self_or_outsource AS text)), '')
  END
), '')) = '自研' THEN true
    ELSE false
  END AS is_self_developed,

  -- === 风险等级标准化 ===
  CASE COALESCE((
  CASE
    WHEN o.risk_level IS NULL THEN NULL
    WHEN upper(btrim(cast(o.risk_level AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(o.risk_level AS text)), '')
  END
), '')
    WHEN '高' THEN 3
    WHEN '中' THEN 2
    WHEN '低' THEN 1
    ELSE 0
  END AS risk_rank,

  -- === 日期解析 ===
  (
  CASE
    WHEN o.contract_negotiation_date IS NULL THEN NULL
    WHEN btrim(cast(o.contract_negotiation_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.contract_negotiation_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.contract_negotiation_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.contract_negotiation_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.contract_negotiation_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.contract_negotiation_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.contract_negotiation_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)  AS contract_negotiation_date,
  (
  CASE
    WHEN o.contract_delivery_date IS NULL THEN NULL
    WHEN btrim(cast(o.contract_delivery_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.contract_delivery_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.contract_delivery_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.contract_delivery_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.contract_delivery_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.contract_delivery_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.contract_delivery_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)     AS contract_delivery_date,
  (
  CASE
    WHEN o.actual_delivery_date IS NULL THEN NULL
    WHEN btrim(cast(o.actual_delivery_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.actual_delivery_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.actual_delivery_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_delivery_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.actual_delivery_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_delivery_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.actual_delivery_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)       AS actual_delivery_date,
  (
  CASE
    WHEN o.plan_inspect_date IS NULL THEN NULL
    WHEN btrim(cast(o.plan_inspect_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.plan_inspect_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.plan_inspect_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_inspect_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.plan_inspect_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.plan_inspect_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.plan_inspect_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)          AS plan_inspect_date,
  (
  CASE
    WHEN o.complete_inspect_date IS NULL THEN NULL
    WHEN btrim(cast(o.complete_inspect_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.complete_inspect_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.complete_inspect_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.complete_inspect_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.complete_inspect_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.complete_inspect_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.complete_inspect_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)      AS complete_inspect_date,
  (
  CASE
    WHEN o.install_date IS NULL THEN NULL
    WHEN btrim(cast(o.install_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.install_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.install_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.install_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.install_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.install_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.install_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)               AS install_date,
  (
  CASE
    WHEN o.last_update_time IS NULL THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) = '' THEN NULL
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.last_update_time AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.last_update_time AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.last_update_time AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.last_update_time AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
)           AS last_update_time,

  -- === 交付延迟天数 ===
  CASE
    WHEN (
  CASE
    WHEN o.contract_delivery_date IS NULL THEN NULL
    WHEN btrim(cast(o.contract_delivery_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.contract_delivery_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.contract_delivery_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.contract_delivery_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.contract_delivery_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.contract_delivery_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.contract_delivery_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) IS NOT NULL
     AND (
  CASE
    WHEN o.actual_delivery_date IS NULL THEN NULL
    WHEN btrim(cast(o.actual_delivery_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.actual_delivery_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.actual_delivery_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_delivery_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.actual_delivery_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_delivery_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.actual_delivery_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) IS NOT NULL
    THEN ((
  CASE
    WHEN o.actual_delivery_date IS NULL THEN NULL
    WHEN btrim(cast(o.actual_delivery_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.actual_delivery_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.actual_delivery_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_delivery_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.actual_delivery_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.actual_delivery_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.actual_delivery_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
) - (
  CASE
    WHEN o.contract_delivery_date IS NULL THEN NULL
    WHEN btrim(cast(o.contract_delivery_date AS text)) = '' THEN NULL
    WHEN btrim(cast(o.contract_delivery_date AS text)) ~ '^\d{4}-\d{2}-\d{2}$'
      THEN to_date(btrim(cast(o.contract_delivery_date AS text)), 'YYYY-MM-DD')
    WHEN btrim(cast(o.contract_delivery_date AS text)) ~ '^\d{4}-\d{2}-\d{2}\s+.*$'
      THEN to_date(substr(btrim(cast(o.contract_delivery_date AS text)), 1, 10), 'YYYY-MM-DD')
    WHEN btrim(cast(o.contract_delivery_date AS text)) ~ '^\d{4}/\d{2}/\d{2}$'
      THEN to_date(replace(btrim(cast(o.contract_delivery_date AS text)), '/', '-'), 'YYYY-MM-DD')
    ELSE NULL
  END
))::int
    ELSE NULL
  END AS delivery_delay_days,

  'ods_material_info'::text AS source_table,
  now() AS etl_time

FROM ods_material_info o
WHERE o.project_no IS NOT NULL;

-- ── biz_dwd_project_node_enriched ──
DROP TABLE IF EXISTS biz_dwd_project_node_enriched CASCADE;
CREATE TABLE biz_dwd_project_node_enriched AS
WITH base AS (
  SELECT *
  FROM biz_dwd_project_node
),
classified AS (
  SELECT
    b.*,
    -- project_no = 项目名称，subsystem = 子项目名称
    -- 不再按 / 拆分，直接使用原始字段
    b.project_no AS _derived_major_project_name,
    b.subsystem  AS _derived_subproject_name,
    CASE
      WHEN (
  CASE
    WHEN b.incomplete_reason IS NULL THEN NULL
    WHEN upper(btrim(cast(b.incomplete_reason AS text))) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
      THEN NULL
    ELSE nullif(btrim(cast(b.incomplete_reason AS text)), '')
  END
) IS NULL THEN 'normal'
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
LEFT JOIN pm_map_node_subject map
  ON map.project_no = c.project_no
 AND map.subsystem = c.subsystem
 AND map.node_task = c.node_task
LEFT JOIN pm_dim_subproject sp
  ON sp.subproject_id = map.subproject_id
LEFT JOIN pm_dim_major_project mp
  ON mp.major_project_id = COALESCE(map.major_project_id, sp.major_project_id)
LEFT JOIN pm_dim_delay_reason dr
  ON dr.delay_reason_category = COALESCE(map.delay_reason_category, c.delay_reason_category_fallback);

-- ── biz_dws_period_node_summary ──
DROP TABLE IF EXISTS biz_dws_period_node_summary CASCADE;
CREATE TABLE biz_dws_period_node_summary AS
SELECT
  d.plan_year,
  d.plan_quarter,
  d.plan_month,
  d.project_no,

  COUNT(*)                                                                        AS total_cnt,
  SUM(CASE WHEN d.completion_status = '正常待完成' THEN 1 ELSE 0 END)             AS pending_normal_cnt,
  SUM(CASE WHEN d.completion_status != '正常待完成' OR d.is_due THEN 1 ELSE 0 END) AS due_cnt,
  SUM(CASE WHEN d.is_on_time THEN 1 ELSE 0 END)                                  AS on_time_cnt,
  SUM(CASE WHEN d.is_overdue_completed THEN 1 ELSE 0 END)                        AS overdue_completed_cnt,
  SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END)                                AS completed_cnt,
  SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END)                               AS incomplete_cnt,
  SUM(CASE WHEN d.completion_status = '不正常待变更' THEN 1 ELSE 0 END)           AS abnormal_pending_cnt,
  SUM(CASE WHEN d.completion_status = '超期未完成未变更' THEN 1 ELSE 0 END)       AS overdue_incomplete_unchanged_cnt,
  SUM(CASE WHEN d.completion_status = '超期未完成已变更' THEN 1 ELSE 0 END)       AS overdue_incomplete_changed_cnt,
  SUM(CASE WHEN d.completion_status = '超期已完成未变更' THEN 1 ELSE 0 END)       AS overdue_completed_unchanged_cnt

FROM biz_dwd_project_node d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month, d.project_no;

-- ── biz_dws_period_node_type_summary ──
DROP TABLE IF EXISTS biz_dws_period_node_type_summary CASCADE;
CREATE TABLE biz_dws_period_node_type_summary AS
SELECT
  d.plan_year,
  d.plan_quarter,
  d.plan_month,
  d.project_no,
  d.node_type,
  d.is_general_node,

  COUNT(*)                                                                        AS total_cnt,
  SUM(CASE WHEN d.is_on_time THEN 1 ELSE 0 END)                                  AS on_time_cnt,
  SUM(CASE WHEN d.is_overdue_completed THEN 1 ELSE 0 END)                        AS overdue_completed_cnt,
  SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END)                                AS completed_cnt,
  SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END)                               AS incomplete_cnt,
  SUM(CASE WHEN d.completion_status = '正常待完成' THEN 1 ELSE 0 END)             AS pending_normal_cnt,
  SUM(CASE WHEN d.completion_status = '不正常待变更' THEN 1 ELSE 0 END)           AS abnormal_pending_cnt,
  SUM(CASE WHEN d.completion_status = '超期未完成未变更' THEN 1 ELSE 0 END)       AS overdue_incomplete_unchanged_cnt,
  SUM(CASE WHEN d.completion_status = '超期未完成已变更' THEN 1 ELSE 0 END)       AS overdue_incomplete_changed_cnt,
  SUM(CASE WHEN d.completion_status = '超期已完成未变更' THEN 1 ELSE 0 END)       AS overdue_completed_unchanged_cnt,
  SUM(CASE WHEN d.completion_status = '超期已完成已变更' THEN 1 ELSE 0 END)       AS overdue_completed_changed_cnt

FROM biz_dwd_project_node d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month, d.project_no, d.node_type, d.is_general_node;

-- ── biz_dws_period_risk_summary ──
DROP TABLE IF EXISTS biz_dws_period_risk_summary CASCADE;
CREATE TABLE biz_dws_period_risk_summary AS
SELECT
  d.plan_year,
  d.plan_quarter,
  d.plan_month,
  d.project_no,
  d.risk_level,
  d.node_type,

  COUNT(*)                                           AS total_cnt,
  SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END)  AS incomplete_cnt,
  SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END)   AS completed_cnt

FROM biz_dwd_project_node d
WHERE d.plan_year IS NOT NULL
  AND d.risk_level IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month, d.project_no, d.risk_level, d.node_type;

-- ── biz_dws_week_subproject_summary ──
DROP TABLE IF EXISTS biz_dws_week_subproject_summary CASCADE;
CREATE TABLE biz_dws_week_subproject_summary AS
SELECT
  major_project_id,
  major_project_name,
  subproject_id,
  subproject_name,
  date_trunc('week', plan_date)::date AS week_start_date,
  to_char(date_trunc('week', plan_date), 'IYYY-"W"IW') AS plan_iso_week,
  COUNT(*) AS total_nodes,
  SUM(CASE WHEN is_completed THEN 1 ELSE 0 END) AS completed_nodes,
  SUM(CASE WHEN node_status_bucket = 'overdue-open' THEN 1 ELSE 0 END) AS overdue_open_nodes,
  SUM(CASE WHEN risk_level = '高' THEN 1 ELSE 0 END) AS high_risk_nodes,
  SUM(CASE WHEN is_milestone THEN 1 ELSE 0 END) AS milestone_nodes,
  SUM(CASE WHEN is_milestone AND is_completed THEN 1 ELSE 0 END) AS milestone_completed_nodes,
  ROUND(AVG(health_score)::numeric, 2) AS avg_health_score,
  ROUND(AVG(GREATEST(COALESCE(delay_days, 0), 0))::numeric, 2) AS avg_delay_days,
  MAX(GREATEST(COALESCE(delay_days, 0), 0)) AS max_delay_days,
  CASE WHEN COUNT(*) = 0 THEN 0
       ELSE ROUND(SUM(CASE WHEN is_completed THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
  END AS completion_rate,
  CASE WHEN SUM(CASE WHEN is_milestone THEN 1 ELSE 0 END) = 0 THEN 0
       ELSE ROUND(
         SUM(CASE WHEN is_milestone AND is_completed THEN 1 ELSE 0 END)::numeric
         / SUM(CASE WHEN is_milestone THEN 1 ELSE 0 END)::numeric, 4)
  END AS milestone_completion_rate
FROM biz_dwd_project_node_enriched
WHERE subproject_id IS NOT NULL
  AND plan_date IS NOT NULL
GROUP BY
  major_project_id,
  major_project_name,
  subproject_id,
  subproject_name,
  date_trunc('week', plan_date);

-- ── biz_dws_progress_measure_summary ──
DROP TABLE IF EXISTS biz_dws_progress_measure_summary CASCADE;
CREATE TABLE biz_dws_progress_measure_summary AS
SELECT
  d.project_no,
  d.subsystem,

  COUNT(*)                                                                      AS total_measure_cnt,
  SUM(CASE WHEN d.closure_status = '已闭环' THEN 1 ELSE 0 END)                 AS closed_cnt,
  SUM(CASE WHEN d.closure_status IS NULL
            OR d.closure_status != '已闭环' THEN 1 ELSE 0 END)                 AS open_cnt,
  CASE WHEN COUNT(*) = 0 THEN 0
       ELSE ROUND(
         SUM(CASE WHEN d.closure_status = '已闭环' THEN 1 ELSE 0 END)::numeric
         / COUNT(*)::numeric, 4)
  END                                                                           AS closure_rate,

  -- 按 measure_category 分类计数
  SUM(CASE WHEN d.measure_category = '设计' THEN 1 ELSE 0 END)                 AS cat_design_cnt,
  SUM(CASE WHEN d.measure_category = '工艺' THEN 1 ELSE 0 END)                 AS cat_process_cnt,
  SUM(CASE WHEN d.measure_category = '管理' THEN 1 ELSE 0 END)                 AS cat_management_cnt,
  SUM(CASE WHEN d.measure_category = '元器件' THEN 1 ELSE 0 END)               AS cat_component_cnt,
  SUM(CASE WHEN d.measure_category = '操作' THEN 1 ELSE 0 END)                 AS cat_operation_cnt,
  SUM(CASE WHEN d.measure_category = '外协' THEN 1 ELSE 0 END)                 AS cat_outsource_cnt,
  SUM(CASE WHEN d.measure_category = '软件' THEN 1 ELSE 0 END)                 AS cat_software_cnt,
  SUM(CASE WHEN d.measure_category NOT IN ('设计','工艺','管理','元器件','操作','外协','软件')
            OR d.measure_category IS NULL THEN 1 ELSE 0 END)                    AS cat_other_cnt

FROM biz_dwd_progress_measure d
WHERE d.project_no IS NOT NULL
GROUP BY d.project_no, d.subsystem;

-- ── biz_dws_quality_period_summary ──
DROP TABLE IF EXISTS biz_dws_quality_period_summary CASCADE;
CREATE TABLE biz_dws_quality_period_summary AS
SELECT
  qi.issue_year                                                                  AS period_year,
  qi.issue_month                                                                 AS period_month,
  qi.project_no,
  qi.dept,

  COUNT(DISTINCT qi.issue_id)                                                    AS total_issue_cnt,
  SUM(CASE WHEN qi.status = '未完成归零' THEN 1 ELSE 0 END)                      AS open_issue_cnt,
  SUM(CASE WHEN qi.zero_complete_date IS NOT NULL THEN 1 ELSE 0 END)             AS closed_cnt,
  CASE WHEN COUNT(DISTINCT qi.issue_id) = 0 THEN 0
       ELSE ROUND(
         SUM(CASE WHEN qi.zero_complete_date IS NOT NULL THEN 1 ELSE 0 END)::numeric
         / COUNT(DISTINCT qi.issue_id)::numeric, 4)
  END                                                                            AS closure_rate,

  -- 归零计划
  SUM(CASE WHEN qi.has_zero_plan THEN 1 ELSE 0 END)                             AS has_zero_plan_cnt,
  SUM(CASE WHEN qi.zero_plan_synced IS NOT NULL
        AND btrim(qi.zero_plan_synced) != '' THEN 1 ELSE 0 END)                 AS zero_plan_synced_cnt,

  -- 按 issue_category 分类计数
  SUM(CASE WHEN qi.issue_category = '设计' THEN 1 ELSE 0 END)                   AS cat_design,
  SUM(CASE WHEN qi.issue_category = '工艺' THEN 1 ELSE 0 END)                   AS cat_process,
  SUM(CASE WHEN qi.issue_category = '管理' THEN 1 ELSE 0 END)                   AS cat_management,
  SUM(CASE WHEN qi.issue_category = '元器件' THEN 1 ELSE 0 END)                 AS cat_component,
  SUM(CASE WHEN qi.issue_category = '操作' THEN 1 ELSE 0 END)                   AS cat_operation,
  SUM(CASE WHEN qi.issue_category = '外协' THEN 1 ELSE 0 END)                   AS cat_outsource,
  SUM(CASE WHEN qi.issue_category = '软件' THEN 1 ELSE 0 END)                   AS cat_software,
  SUM(CASE WHEN qi.issue_category NOT IN ('设计','工艺','管理','元器件','操作','外协','软件')
            OR qi.issue_category IS NULL THEN 1 ELSE 0 END)                      AS cat_other,

  -- 关联措施数
  COUNT(DISTINCT qm.measure_id)                                                  AS measure_count

FROM biz_dwd_quality_issue qi
LEFT JOIN biz_dwd_quality_measure qm
  ON qi.project_no = qm.project_no
 AND qi.issue_name = qm.issue_name
WHERE qi.issue_year IS NOT NULL
GROUP BY qi.issue_year, qi.issue_month, qi.project_no, qi.dept;

-- ── biz_dws_tech_state_period_summary ──
DROP TABLE IF EXISTS biz_dws_tech_state_period_summary CASCADE;
CREATE TABLE biz_dws_tech_state_period_summary AS
SELECT
  EXTRACT(YEAR FROM d.change_submit_time)::int                                   AS period_year,
  to_char(d.change_submit_time, 'YYYY-MM')                                       AS period_month,
  d.project_no,
  d.dept,

  COUNT(*)                                                                       AS total_change_cnt,

  -- 按 change_category 分类
  SUM(CASE WHEN d.change_category = 'I类' THEN 1 ELSE 0 END)                    AS cat_i,
  SUM(CASE WHEN d.change_category = 'II类' THEN 1 ELSE 0 END)                   AS cat_ii,
  SUM(CASE WHEN d.change_category = 'III类' THEN 1 ELSE 0 END)                  AS cat_iii,

  -- 签署完成
  SUM(CASE WHEN d.is_signature_completed THEN 1 ELSE 0 END)                     AS signature_completed_cnt,

  -- 文件签署
  SUM(CASE WHEN d.is_file_signed THEN 1 ELSE 0 END)                             AS file_signed_cnt,

  -- 整改落实
  SUM(CASE WHEN d.is_reformed THEN 1 ELSE 0 END)                                AS reform_done_cnt,
  SUM(CASE WHEN NOT d.is_reformed THEN 1 ELSE 0 END)                            AS reform_pending_cnt

FROM biz_dwd_tech_state d
WHERE d.change_submit_time IS NOT NULL
GROUP BY
  EXTRACT(YEAR FROM d.change_submit_time)::int,
  to_char(d.change_submit_time, 'YYYY-MM'),
  d.project_no,
  d.dept;

-- ── biz_dws_risk_period_summary ──
DROP TABLE IF EXISTS biz_dws_risk_period_summary CASCADE;
CREATE TABLE biz_dws_risk_period_summary AS
SELECT
  d.submit_year                                                                  AS period_year,
  d.submit_month                                                                 AS period_month,
  d.project_no,
  d.dept,

  COUNT(*)                                                                       AS total_risk_cnt,

  -- 按 risk_level 分类
  SUM(CASE WHEN d.risk_level = '高' THEN 1 ELSE 0 END)                          AS high_cnt,
  SUM(CASE WHEN d.risk_level = '中' THEN 1 ELSE 0 END)                          AS mid_cnt,
  SUM(CASE WHEN d.risk_level = '低' THEN 1 ELSE 0 END)                          AS low_cnt,

  -- 释放状态
  SUM(CASE WHEN d.is_released THEN 1 ELSE 0 END)                                AS released_cnt,
  SUM(CASE WHEN NOT d.is_released THEN 1 ELSE 0 END)                            AS open_cnt,
  CASE WHEN COUNT(*) = 0 THEN 0
       ELSE ROUND(
         SUM(CASE WHEN d.is_released THEN 1 ELSE 0 END)::numeric
         / COUNT(*)::numeric, 4)
  END                                                                            AS release_rate

FROM biz_dwd_risk_info d
WHERE d.submit_year IS NOT NULL
GROUP BY d.submit_year, d.submit_month, d.project_no, d.dept;

-- ── biz_dws_material_period_summary ──
DROP TABLE IF EXISTS biz_dws_material_period_summary CASCADE;
CREATE TABLE biz_dws_material_period_summary AS
SELECT
  d.project_no,
  d.subsystem,

  COUNT(*)                                                                       AS total_cnt,

  -- 长周期
  SUM(CASE WHEN d.is_long_cycle THEN 1 ELSE 0 END)                              AS long_cycle_cnt,
  CASE WHEN COUNT(*) = 0 THEN 0
       ELSE ROUND(
         SUM(CASE WHEN d.is_long_cycle THEN 1 ELSE 0 END)::numeric
         / COUNT(*)::numeric, 4)
  END                                                                            AS long_cycle_rate,

  -- 自研 vs 外协
  SUM(CASE WHEN d.is_self_developed THEN 1 ELSE 0 END)                          AS self_developed_cnt,
  SUM(CASE WHEN NOT d.is_self_developed THEN 1 ELSE 0 END)                      AS outsource_cnt,
  CASE WHEN COUNT(*) = 0 THEN 0
       ELSE ROUND(
         SUM(CASE WHEN NOT d.is_self_developed THEN 1 ELSE 0 END)::numeric
         / COUNT(*)::numeric, 4)
  END                                                                            AS outsource_rate,

  -- 风险
  SUM(CASE WHEN d.risk_rank > 0 THEN 1 ELSE 0 END)                              AS has_risk_cnt,

  -- 供应商数
  COUNT(DISTINCT d.supplier_name)                                                AS supplier_count

FROM biz_dwd_material_info d
WHERE d.project_no IS NOT NULL
GROUP BY d.project_no, d.subsystem;

-- ── biz_ads_project_kpi_overview ──
DROP TABLE IF EXISTS biz_ads_project_kpi_overview CASCADE;
CREATE TABLE biz_ads_project_kpi_overview AS
WITH base AS (
  SELECT
    plan_year,
    plan_quarter,
    plan_month,

    SUM(total_cnt)                          AS total_cnt,
    SUM(pending_normal_cnt)                 AS pending_normal_cnt,
    SUM(due_cnt)                            AS due_cnt,
    SUM(on_time_cnt)                        AS on_time_cnt,
    SUM(overdue_completed_cnt)              AS overdue_completed_cnt,
    SUM(completed_cnt)                      AS completed_cnt,
    SUM(incomplete_cnt)                     AS incomplete_cnt,
    SUM(abnormal_pending_cnt)               AS abnormal_pending_cnt,
    SUM(overdue_incomplete_unchanged_cnt)   AS overdue_incomplete_unchanged_cnt,
    SUM(overdue_incomplete_changed_cnt)     AS overdue_incomplete_changed_cnt
  FROM biz_dws_period_node_summary
  GROUP BY plan_year, plan_quarter, plan_month
),

outside_completed AS (
  SELECT
    d.actual_year   AS plan_year,
    d.actual_month  AS plan_month,
    COUNT(*)        AS outside_completed_cnt
  FROM biz_dwd_project_node d
  WHERE d.is_completed = true
    AND d.actual_year IS NOT NULL
    AND (d.plan_year != d.actual_year OR d.plan_month != d.actual_month)
  GROUP BY d.actual_year, d.actual_month
)

SELECT
  b.plan_year,
  b.plan_quarter,
  b.plan_month,

  b.total_cnt,
  b.pending_normal_cnt,
  b.due_cnt,
  COALESCE(oc.outside_completed_cnt, 0)                           AS outside_completed_cnt,
  b.incomplete_cnt,
  b.on_time_cnt,
  b.overdue_completed_cnt,
  b.completed_cnt + COALESCE(oc.outside_completed_cnt, 0)         AS completed_total_cnt,

  CASE WHEN (b.due_cnt + COALESCE(oc.outside_completed_cnt, 0)) = 0 THEN 0
       ELSE ROUND(
         (b.completed_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric
         / (b.due_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric, 4)
  END AS completion_rate,

  CASE WHEN (b.due_cnt + COALESCE(oc.outside_completed_cnt, 0)) = 0 THEN 0
       ELSE ROUND(
         b.on_time_cnt::numeric
         / (b.due_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric, 4)
  END AS on_time_rate,

  CASE WHEN (b.total_cnt + COALESCE(oc.outside_completed_cnt, 0)) = 0 THEN 0
       ELSE ROUND(
         (b.overdue_completed_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric
         / (b.total_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric, 4)
  END AS overdue_completion_rate,

  b.abnormal_pending_cnt,
  b.overdue_incomplete_unchanged_cnt,
  b.overdue_incomplete_changed_cnt

FROM base b
LEFT JOIN outside_completed oc
  ON oc.plan_year = b.plan_year AND oc.plan_month = b.plan_month
ORDER BY b.plan_year, b.plan_month;

-- ── biz_ads_project_milestone_kpi ──
DROP TABLE IF EXISTS biz_ads_project_milestone_kpi CASCADE;
CREATE TABLE biz_ads_project_milestone_kpi AS
SELECT
  d.plan_year,
  d.plan_quarter,
  d.plan_month,

  -- 里程碑节点专项
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_on_time THEN 1 ELSE 0 END)                          AS milestone_on_time_cnt,
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_overdue_completed THEN 1 ELSE 0 END)                AS milestone_overdue_completed_cnt,
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.completion_status = '正常待完成' THEN 1 ELSE 0 END)     AS milestone_pending_cnt,
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_incomplete THEN 1 ELSE 0 END)                        AS milestone_incomplete_cnt,

  CASE
    WHEN (SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_incomplete THEN 1 ELSE 0 END)
        + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_on_time THEN 1 ELSE 0 END)
        + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_overdue_completed THEN 1 ELSE 0 END)) = 0
    THEN 0
    ELSE ROUND(
      (SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_on_time THEN 1 ELSE 0 END)
       + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_overdue_completed THEN 1 ELSE 0 END))::numeric
      / (SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_incomplete THEN 1 ELSE 0 END)
       + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_on_time THEN 1 ELSE 0 END)
       + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_overdue_completed THEN 1 ELSE 0 END))::numeric
    , 4)
  END AS milestone_completion_rate,

  -- 风险统计
  SUM(CASE WHEN d.risk_level = '高' THEN 1 ELSE 0 END)        AS high_risk_cnt,
  SUM(CASE WHEN d.risk_level = '中' THEN 1 ELSE 0 END)        AS mid_risk_cnt,

  -- 节点类型总数
  SUM(CASE WHEN d.node_type = '里程碑节点' THEN 1 ELSE 0 END) AS milestone_total_cnt,
  SUM(CASE WHEN d.node_type = '重大节点' THEN 1 ELSE 0 END)   AS major_total_cnt,
  SUM(CASE WHEN d.node_type = '重要节点' THEN 1 ELSE 0 END)   AS important_total_cnt

FROM biz_dwd_project_node d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month
ORDER BY d.plan_year, d.plan_month;

-- ── biz_ads_project_incomplete_risk ──
DROP TABLE IF EXISTS biz_ads_project_incomplete_risk CASCADE;
CREATE TABLE biz_ads_project_incomplete_risk AS
SELECT
  d.plan_year,
  d.plan_quarter,
  d.plan_month,

  SUM(CASE WHEN d.risk_level = '高' AND d.is_incomplete THEN 1 ELSE 0 END)        AS incomplete_high_risk_cnt,
  SUM(CASE WHEN d.risk_level = '中' AND d.is_incomplete THEN 1 ELSE 0 END)        AS incomplete_mid_risk_cnt,
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_incomplete THEN 1 ELSE 0 END) AS incomplete_milestone_cnt,
  SUM(CASE WHEN d.node_type = '重大节点' AND d.is_incomplete THEN 1 ELSE 0 END)   AS incomplete_major_cnt,
  SUM(CASE WHEN d.node_type = '重要节点' AND d.is_incomplete THEN 1 ELSE 0 END)   AS incomplete_important_cnt

FROM biz_dwd_project_node d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month
ORDER BY d.plan_year, d.plan_month;

-- ── biz_ads_project_non_general_kpi ──
DROP TABLE IF EXISTS biz_ads_project_non_general_kpi CASCADE;
CREATE TABLE biz_ads_project_non_general_kpi AS
WITH base AS (
  SELECT
    plan_year,
    plan_quarter,
    plan_month,

    SUM(CASE WHEN NOT is_general_node THEN abnormal_pending_cnt ELSE 0 END)             AS abnormal_pending_cnt,
    SUM(CASE WHEN NOT is_general_node THEN overdue_incomplete_unchanged_cnt ELSE 0 END) AS overdue_incomplete_unchanged_cnt,
    SUM(CASE WHEN NOT is_general_node THEN overdue_incomplete_changed_cnt ELSE 0 END)   AS overdue_incomplete_changed_cnt,
    SUM(CASE WHEN NOT is_general_node THEN overdue_completed_unchanged_cnt ELSE 0 END)  AS overdue_completed_unchanged_cnt,
    SUM(CASE WHEN NOT is_general_node THEN total_cnt - pending_normal_cnt ELSE 0 END)   AS due_cnt_non_general
  FROM biz_dws_period_node_type_summary
  GROUP BY plan_year, plan_quarter, plan_month
)

SELECT
  plan_year,
  plan_quarter,
  plan_month,

  abnormal_pending_cnt,
  overdue_incomplete_unchanged_cnt,
  overdue_incomplete_changed_cnt,
  overdue_completed_unchanged_cnt,

  CASE WHEN due_cnt_non_general = 0 THEN 0
       ELSE ROUND(
         (abnormal_pending_cnt + overdue_incomplete_unchanged_cnt)::numeric
         / due_cnt_non_general::numeric, 4)
  END AS abnormal_rate,

  CASE WHEN due_cnt_non_general = 0 THEN 0
       ELSE ROUND(
         (overdue_incomplete_unchanged_cnt + overdue_incomplete_changed_cnt)::numeric
         / due_cnt_non_general::numeric, 4)
  END AS overdue_rate

FROM base
ORDER BY plan_year, plan_month;

-- ── biz_ads_major_project_overview ──
DROP TABLE IF EXISTS biz_ads_major_project_overview CASCADE;
CREATE TABLE biz_ads_major_project_overview AS
SELECT
  major_project_id,
  major_project_name,
  COUNT(*) AS total_nodes,
  COUNT(DISTINCT subproject_id) AS subproject_count,
  SUM(CASE WHEN is_completed THEN 1 ELSE 0 END) AS completed_nodes,
  SUM(CASE WHEN node_status_bucket = 'overdue-open' THEN 1 ELSE 0 END) AS overdue_open_nodes,
  SUM(CASE WHEN risk_level = '高' THEN 1 ELSE 0 END) AS high_risk_nodes,
  SUM(CASE WHEN is_milestone THEN 1 ELSE 0 END) AS milestone_nodes,
  SUM(CASE WHEN is_milestone AND is_completed THEN 1 ELSE 0 END) AS milestone_completed_nodes,
  ROUND(AVG(health_score)::numeric, 2) AS avg_health_score,
  ROUND(AVG(GREATEST(COALESCE(delay_days, 0), 0))::numeric, 2) AS avg_delay_days,
  MAX(GREATEST(COALESCE(delay_days, 0), 0)) AS max_delay_days,
  CASE WHEN COUNT(*) = 0 THEN 0
       ELSE ROUND(SUM(CASE WHEN is_completed THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
  END AS completion_rate,
  CASE WHEN SUM(CASE WHEN is_milestone THEN 1 ELSE 0 END) = 0 THEN 0
       ELSE ROUND(
         SUM(CASE WHEN is_milestone AND is_completed THEN 1 ELSE 0 END)::numeric
         / SUM(CASE WHEN is_milestone THEN 1 ELSE 0 END)::numeric, 4)
  END AS milestone_completion_rate
FROM biz_dwd_project_node_enriched
WHERE major_project_id IS NOT NULL
GROUP BY major_project_id, major_project_name;

-- ── biz_ads_major_project_tree_snapshot ──
DROP TABLE IF EXISTS biz_ads_major_project_tree_snapshot CASCADE;
CREATE TABLE biz_ads_major_project_tree_snapshot AS
WITH major_level AS (
  SELECT
    'major'::text AS snapshot_level,
    major_project_id AS entity_id,
    NULL::text AS parent_id,
    major_project_name AS entity_name,
    major_project_id,
    major_project_name,
    NULL::text AS subproject_id,
    NULL::text AS subproject_name,
    COUNT(*) AS total_nodes,
    SUM(CASE WHEN is_completed THEN 1 ELSE 0 END) AS completed_nodes,
    SUM(CASE WHEN node_status_bucket = 'overdue-open' THEN 1 ELSE 0 END) AS overdue_open_nodes,
    SUM(CASE WHEN risk_level = '高' THEN 1 ELSE 0 END) AS high_risk_nodes,
    ROUND(AVG(health_score)::numeric, 2) AS avg_health_score,
    MIN(plan_date) AS plan_start_date,
    MAX(plan_date) AS plan_end_date,
    MAX(actual_date) AS actual_end_date,
    CASE WHEN COUNT(*) = 0 THEN 0
         ELSE ROUND(SUM(CASE WHEN is_completed THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
    END AS progress_rate,
    0::int AS sort_order,
    NULL::text AS node_id,
    NULL::text AS node_task,
    NULL::text AS node_type,
    NULL::text AS risk_level,
    0::int AS delay_days
  FROM biz_dwd_project_node_enriched
  WHERE major_project_id IS NOT NULL
  GROUP BY major_project_id, major_project_name
),
subproject_level AS (
  SELECT
    'subproject'::text AS snapshot_level,
    subproject_id AS entity_id,
    major_project_id AS parent_id,
    subproject_name AS entity_name,
    major_project_id,
    major_project_name,
    subproject_id,
    subproject_name,
    COUNT(*) AS total_nodes,
    SUM(CASE WHEN is_completed THEN 1 ELSE 0 END) AS completed_nodes,
    SUM(CASE WHEN node_status_bucket = 'overdue-open' THEN 1 ELSE 0 END) AS overdue_open_nodes,
    SUM(CASE WHEN risk_level = '高' THEN 1 ELSE 0 END) AS high_risk_nodes,
    ROUND(AVG(health_score)::numeric, 2) AS avg_health_score,
    MIN(plan_date) AS plan_start_date,
    MAX(plan_date) AS plan_end_date,
    MAX(actual_date) AS actual_end_date,
    CASE WHEN COUNT(*) = 0 THEN 0
         ELSE ROUND(SUM(CASE WHEN is_completed THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
    END AS progress_rate,
    0::int AS sort_order,
    NULL::text AS node_id,
    NULL::text AS node_task,
    NULL::text AS node_type,
    NULL::text AS risk_level,
    0::int AS delay_days
  FROM biz_dwd_project_node_enriched
  WHERE subproject_id IS NOT NULL
  GROUP BY
    major_project_id,
    major_project_name,
    subproject_id,
    subproject_name
),
node_level AS (
  SELECT
    'node'::text AS snapshot_level,
    node_id AS entity_id,
    subproject_id AS parent_id,
    node_task AS entity_name,
    major_project_id,
    major_project_name,
    subproject_id,
    subproject_name,
    1 AS total_nodes,
    CASE WHEN is_completed THEN 1 ELSE 0 END AS completed_nodes,
    CASE WHEN node_status_bucket = 'overdue-open' THEN 1 ELSE 0 END AS overdue_open_nodes,
    CASE WHEN risk_level = '高' THEN 1 ELSE 0 END AS high_risk_nodes,
    health_score::numeric(10, 2) AS avg_health_score,
    plan_date AS plan_start_date,
    plan_date AS plan_end_date,
    actual_date AS actual_end_date,
    CASE WHEN is_completed THEN 1 ELSE 0 END::numeric(10, 4) AS progress_rate,
    COALESCE(sort_order, 0) AS sort_order,
    node_id,
    node_task,
    node_type,
    risk_level,
    GREATEST(COALESCE(delay_days, 0), 0) AS delay_days
  FROM biz_dwd_project_node_enriched
  WHERE subproject_id IS NOT NULL
)
SELECT * FROM major_level
UNION ALL
SELECT * FROM subproject_level
UNION ALL
SELECT * FROM node_level;

-- ── biz_ads_delay_reason_trend ──
DROP TABLE IF EXISTS biz_ads_delay_reason_trend CASCADE;
CREATE TABLE biz_ads_delay_reason_trend AS
SELECT
  major_project_id,
  major_project_name,
  dept,
  date_trunc('week', plan_date)::date AS week_start_date,
  to_char(date_trunc('week', plan_date), 'IYYY-"W"IW') AS plan_iso_week,
  delay_reason_category,
  delay_reason_label,
  COUNT(*) FILTER (
    WHERE node_status_bucket = 'overdue-open'
       OR completion_status IN ('超期已完成已变更', '超期已完成未变更')
  ) AS delayed_node_count,
  COUNT(*) FILTER (WHERE risk_level = '高') AS high_risk_node_count,
  COUNT(DISTINCT subproject_id) FILTER (
    WHERE node_status_bucket = 'overdue-open'
       OR completion_status IN ('超期已完成已变更', '超期已完成未变更')
  ) AS delayed_subproject_count
FROM biz_dwd_project_node_enriched
WHERE plan_date IS NOT NULL
GROUP BY
  major_project_id,
  major_project_name,
  dept,
  date_trunc('week', plan_date),
  delay_reason_category,
  delay_reason_label;

-- ── biz_ads_quality_kpi ──
DROP TABLE IF EXISTS biz_ads_quality_kpi CASCADE;
CREATE TABLE biz_ads_quality_kpi AS
SELECT
  s.period_year,
  s.period_month,

  SUM(s.total_issue_cnt)                                                         AS total_issue_cnt,
  SUM(s.open_issue_cnt)                                                          AS open_issue_cnt,
  SUM(s.closed_cnt)                                                              AS closed_cnt,
  CASE WHEN SUM(s.total_issue_cnt) = 0 THEN 0
       ELSE ROUND(
         SUM(s.closed_cnt)::numeric
         / SUM(s.total_issue_cnt)::numeric, 4)
  END                                                                            AS closure_rate,

  SUM(s.has_zero_plan_cnt)                                                       AS has_zero_plan_cnt,
  SUM(s.zero_plan_synced_cnt)                                                    AS zero_plan_synced_cnt,

  SUM(s.cat_design)                                                              AS cat_design,
  SUM(s.cat_process)                                                             AS cat_process,
  SUM(s.cat_management)                                                          AS cat_management,
  SUM(s.cat_component)                                                           AS cat_component,
  SUM(s.cat_operation)                                                           AS cat_operation,
  SUM(s.cat_outsource)                                                           AS cat_outsource,
  SUM(s.cat_software)                                                            AS cat_software,
  SUM(s.cat_other)                                                               AS cat_other,

  SUM(s.measure_count)                                                           AS measure_count

FROM biz_dws_quality_period_summary s
GROUP BY s.period_year, s.period_month
ORDER BY s.period_year, s.period_month;

-- ── biz_ads_tech_state_kpi ──
DROP TABLE IF EXISTS biz_ads_tech_state_kpi CASCADE;
CREATE TABLE biz_ads_tech_state_kpi AS
SELECT
  s.period_year,
  s.period_month,

  SUM(s.total_change_cnt)                                                        AS total_change_cnt,

  SUM(s.cat_i)                                                                   AS cat_i,
  SUM(s.cat_ii)                                                                  AS cat_ii,
  SUM(s.cat_iii)                                                                 AS cat_iii,

  SUM(s.signature_completed_cnt)                                                 AS signature_completed_cnt,
  SUM(s.file_signed_cnt)                                                         AS file_signed_cnt,
  SUM(s.reform_done_cnt)                                                         AS reform_done_cnt,
  SUM(s.reform_pending_cnt)                                                      AS reform_pending_cnt

FROM biz_dws_tech_state_period_summary s
GROUP BY s.period_year, s.period_month
ORDER BY s.period_year, s.period_month;

-- ── biz_ads_risk_kpi ──
DROP TABLE IF EXISTS biz_ads_risk_kpi CASCADE;
CREATE TABLE biz_ads_risk_kpi AS
SELECT
  s.period_year,
  s.period_month,

  SUM(s.total_risk_cnt)                                                          AS total_risk_cnt,

  SUM(s.high_cnt)                                                                AS high_cnt,
  SUM(s.mid_cnt)                                                                 AS mid_cnt,
  SUM(s.low_cnt)                                                                 AS low_cnt,

  SUM(s.released_cnt)                                                            AS released_cnt,
  SUM(s.open_cnt)                                                                AS open_cnt,
  CASE WHEN SUM(s.total_risk_cnt) = 0 THEN 0
       ELSE ROUND(
         SUM(s.released_cnt)::numeric
         / SUM(s.total_risk_cnt)::numeric, 4)
  END                                                                            AS release_rate

FROM biz_dws_risk_period_summary s
GROUP BY s.period_year, s.period_month
ORDER BY s.period_year, s.period_month;

-- ── biz_ads_material_kpi ──
DROP TABLE IF EXISTS biz_ads_material_kpi CASCADE;
CREATE TABLE biz_ads_material_kpi AS
SELECT
  s.project_no,

  SUM(s.total_cnt)                                                               AS total_cnt,
  SUM(s.long_cycle_cnt)                                                          AS long_cycle_cnt,
  CASE WHEN SUM(s.total_cnt) = 0 THEN 0
       ELSE ROUND(
         SUM(s.long_cycle_cnt)::numeric
         / SUM(s.total_cnt)::numeric, 4)
  END                                                                            AS long_cycle_rate,

  SUM(s.self_developed_cnt)                                                      AS self_developed_cnt,
  SUM(s.outsource_cnt)                                                           AS outsource_cnt,
  CASE WHEN SUM(s.total_cnt) = 0 THEN 0
       ELSE ROUND(
         SUM(s.outsource_cnt)::numeric
         / SUM(s.total_cnt)::numeric, 4)
  END                                                                            AS outsource_rate,

  SUM(s.has_risk_cnt)                                                            AS has_risk_cnt,
  SUM(s.supplier_count)                                                          AS supplier_count

FROM biz_dws_material_period_summary s
GROUP BY s.project_no
ORDER BY s.project_no;

COMMIT;
