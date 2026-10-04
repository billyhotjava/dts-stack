-- ============================================================
-- 项目管理数仓模型一键构建（无需 dbt）
-- 适用场景：Excel/CSV 导入 ODS 后，手工/调度执行本 SQL
-- 目标库：PostgreSQL（public schema）
-- 数据域：项目节点管理（project-cockpit）
-- 版本：v2.2.2（dbt 模型同步重构，全量自动推导，无 seed 依赖）
-- ============================================================
-- 用法：
--   psql -U biadmin -d biadmin -f 99-build-all.sql
--   psql -U biadmin -d biadmin -v ods_table=my_ods_table -f 99-build-all.sql
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- 可选：指定 ODS 来源表（psql 参数 ods_table）
-- 用法：
--   psql -v ods_table=ods_project_subject_domain_2026q1 -f 99-build-all.sql
-- 若未指定，则默认使用 ods_project_subject_domain
-- ------------------------------------------------------------
\if :{?ods_table}
SELECT set_config('dts.ods_table', :'ods_table', false);
\endif

-- 若未通过参数传入，设置默认值
DO $$
BEGIN
  IF current_setting('dts.ods_table', true) IS NULL OR current_setting('dts.ods_table', true) = '' THEN
    PERFORM set_config('dts.ods_table', 'ods_project_subject_domain', false);
  END IF;
END $$;

-- ------------------------------------------------------------
-- 0) 前置函数：安全解析日期（若已存在可重复执行）
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION parse_date_safe(p_text text)
RETURNS date
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
  v text;
  parts text[];
  p1 int; p2 int; p3 int;
BEGIN
  IF p_text IS NULL THEN
    RETURN NULL;
  END IF;

  v := btrim(p_text);
  IF v = '' THEN
    RETURN NULL;
  END IF;

  -- Normalize separators: . and / → -
  v := replace(replace(v, '.', '-'), '/', '-');
  -- Strip time part if present (e.g. "2025-08-29 10:30:00")
  v := split_part(v, ' ', 1);

  -- YYYY-MM-DD  (2025-08-29, 2025-8-29)
  IF v ~ '^\d{4}-\d{1,2}-\d{1,2}$' THEN
    RETURN to_date(v, 'YYYY-MM-DD');
  END IF;

  -- YYYYMMDD  (20250829)
  IF v ~ '^\d{8}$' THEN
    RETURN to_date(v, 'YYYYMMDD');
  END IF;

  -- M/D/YYYY or D/M/YYYY  (8/29/2025, 29/8/2025)
  -- After normalization: 8-29-2025 or 29-8-2025
  IF v ~ '^\d{1,2}-\d{1,2}-\d{4}$' THEN
    parts := string_to_array(v, '-');
    p1 := parts[1]::int;  -- first number
    p2 := parts[2]::int;  -- second number
    p3 := parts[3]::int;  -- year

    -- Disambiguate: if p2 > 12, it must be a day → p1 is month (M/D/YYYY)
    IF p2 > 12 THEN
      RETURN make_date(p3, p1, p2);
    END IF;
    -- If p1 > 12, it must be a day → p2 is month (D/M/YYYY)
    IF p1 > 12 THEN
      RETURN make_date(p3, p2, p1);
    END IF;
    -- Both ≤ 12: ambiguous, default to M/D/YYYY (US format, common in Excel)
    RETURN make_date(p3, p1, p2);
  END IF;

  RETURN NULL;
END;
$$;

-- ------------------------------------------------------------
-- 前置函数：清理占位符值（等同于 nullif_placeholder 宏）
-- 将 '/', '#VALUE!', 'N/A' 等无效值统一转为 NULL
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION nullif_placeholder(p_text text)
RETURNS text
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT
    CASE
      WHEN p_text IS NULL THEN NULL
      WHEN upper(btrim(p_text)) IN ('', '/', '-', '--', 'N/A', 'NA', '#N/A', '#VALUE!', '#DIV/0!', 'NULL')
        THEN NULL
      ELSE nullif(btrim(p_text), '')
    END
$$;

-- ------------------------------------------------------------
-- 前置函数：安全解析数值（等同于 parse_numeric_safe 宏）
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION parse_numeric_safe(p_text text)
RETURNS numeric
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT
    CASE
      WHEN nullif_placeholder(p_text) IS NULL THEN NULL
      WHEN regexp_replace(nullif_placeholder(p_text), ',', '', 'g') ~ '^-?\d+(\.\d+)?$'
        THEN regexp_replace(nullif_placeholder(p_text), ',', '', 'g')::numeric
      ELSE NULL
    END
$$;

-- ============================================================
-- 1) DIM 维度表（静态枚举，从 VALUES 内联构建）
-- ============================================================

-- ------------------------------------------------------------
-- 1.1) DIM：完成状态维度
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.dim_completion_status CASCADE;
CREATE TABLE public.dim_completion_status AS
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

ANALYZE public.dim_completion_status;

-- ------------------------------------------------------------
-- 1.2) DIM：节点类型维度
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.dim_node_type CASCADE;
CREATE TABLE public.dim_node_type AS
SELECT code, label, is_general, severity_rank
FROM (VALUES
  ('一般节点',   '一般节点',   true,  1),
  ('重要节点',   '重要节点',   false, 2),
  ('重大节点',   '重大节点',   false, 3),
  ('里程碑节点', '里程碑节点', false, 4)
) AS t(code, label, is_general, severity_rank);

ANALYZE public.dim_node_type;

-- ------------------------------------------------------------
-- 1.3) DIM：风险等级维度
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.dim_risk_level CASCADE;
CREATE TABLE public.dim_risk_level AS
SELECT code, label, severity_rank
FROM (VALUES
  ('高', '高风险', 3),
  ('中', '中风险', 2),
  ('低', '低风险', 1)
) AS t(code, label, severity_rank);

ANALYZE public.dim_risk_level;

-- ------------------------------------------------------------
-- 1.4) DIM：延期原因维度（内联定义，无需 seed）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.pm_dim_delay_reason CASCADE;
CREATE TABLE public.pm_dim_delay_reason AS
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

ANALYZE public.pm_dim_delay_reason;

-- ============================================================
-- 2) DIM：从 ODS 自动推导的维度表
-- ============================================================

-- ------------------------------------------------------------
-- 2.1) DIM：项目维度（从 ODS 自动推导）
-- project_no 即为项目标识（客户确认：项目编号代表项目名称）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.pm_dim_major_project CASCADE;
CREATE TABLE public.pm_dim_major_project (
  major_project_id   text,
  major_project_code text,
  major_project_name text,
  project_level      text,
  owner_dept         text,
  owner_leader       text,
  priority_level     text,
  start_date         date,
  plan_end_date      date,
  status             text,
  remark             text
);

DO $$
DECLARE
  ods_table text := current_setting('dts.ods_table', true);
BEGIN
  IF ods_table IS NULL OR ods_table = '' THEN
    ods_table := 'ods_project_subject_domain';
  END IF;
  EXECUTE format($fmt$
WITH raw AS (
  SELECT
    nullif_placeholder(o.project_no) AS project_no,
    nullif_placeholder(o.project_no) AS major_project_name,
    nullif_placeholder(o.dept) AS dept,
    nullif_placeholder(o.dept_leader) AS dept_leader,
    parse_date_safe(o.plan_date) AS plan_date
  FROM public.%I o
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
INSERT INTO public.pm_dim_major_project
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
LEFT JOIN first_dept d ON d.project_no = a.project_no
$fmt$, ods_table);
END $$;

ANALYZE public.pm_dim_major_project;

-- ------------------------------------------------------------
-- 2.2) DIM：子项目维度（从 ODS 自动推导）
-- subsystem 直接作为子项目名称，不再用 "/" 分割
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.pm_dim_subproject CASCADE;
CREATE TABLE public.pm_dim_subproject (
  subproject_id    text,
  subproject_code  text,
  subproject_name  text,
  major_project_id text,
  project_no       text,
  subsystem_name   text,
  owner_dept       text,
  owner_user       text,
  project_manager  text,
  plan_start_date  date,
  plan_end_date    date,
  actual_end_date  date,
  status           text,
  priority_level   text,
  remark           text
);

DO $$
DECLARE
  ods_table text := current_setting('dts.ods_table', true);
BEGIN
  IF ods_table IS NULL OR ods_table = '' THEN
    ods_table := 'ods_project_subject_domain';
  END IF;
  EXECUTE format($fmt$
WITH raw AS (
  SELECT
    nullif_placeholder(o.project_no) AS project_no,
    nullif_placeholder(o.subsystem) AS subsystem,
    nullif_placeholder(o.subsystem) AS subproject_name,
    nullif_placeholder(o.dept) AS dept,
    nullif_placeholder(o.project_manager) AS project_manager,
    parse_date_safe(o.plan_date) AS plan_date
  FROM public.%I o
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
INSERT INTO public.pm_dim_subproject
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
LEFT JOIN first_dept d ON d.project_no = a.project_no AND d.subsystem = a.subsystem
$fmt$, ods_table);
END $$;

ANALYZE public.pm_dim_subproject;

-- ------------------------------------------------------------
-- 2.3) DIM：节点-主题映射（从 ODS 自动推导）
-- node_category/is_key_node/is_milestone 从 node_type 自动推导
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.pm_map_node_subject CASCADE;
CREATE TABLE public.pm_map_node_subject (
  map_id                 text,
  project_no             text,
  subsystem              text,
  node_task              text,
  subproject_id          text,
  major_project_id       text,
  node_category          text,
  delay_reason_category  text,
  is_key_node            boolean,
  is_milestone           boolean,
  sort_order             int,
  source_flag            text,
  remark                 text
);

DO $$
DECLARE
  ods_table text := current_setting('dts.ods_table', true);
BEGIN
  IF ods_table IS NULL OR ods_table = '' THEN
    ods_table := 'ods_project_subject_domain';
  END IF;
  EXECUTE format($fmt$
INSERT INTO public.pm_map_node_subject
SELECT DISTINCT
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.subsystem), '') || '|' ||
    COALESCE(btrim(o.node_task), '')
  ) AS map_id,
  nullif_placeholder(o.project_no) AS project_no,
  nullif_placeholder(o.subsystem) AS subsystem,
  nullif_placeholder(o.node_task) AS node_task,
  md5(COALESCE(btrim(o.project_no), '') || '/' || COALESCE(btrim(o.subsystem), '')) AS subproject_id,
  nullif_placeholder(o.project_no) AS major_project_id,
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
FROM public.%I o
WHERE btrim(COALESCE(o.project_no, '')) != ''
  AND btrim(COALESCE(o.node_task, '')) != ''
$fmt$, ods_table);
END $$;

ANALYZE public.pm_map_node_subject;

-- ============================================================
-- 3) DWD：明细层
-- ============================================================

-- ------------------------------------------------------------
-- 3.1) DWD：项目节点明细表
-- 来源：ODS（通过 dts.ods_table 参数指定，默认 ods_project_subject_domain）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_dwd_project_node CASCADE;
DO $$
DECLARE
  ods_table text := current_setting('dts.ods_table', true);
BEGIN
  IF ods_table IS NULL OR ods_table = '' THEN
    ods_table := 'ods_project_subject_domain';
  END IF;
  EXECUTE format($fmt$
CREATE TABLE public.biz_dwd_project_node AS
SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.subsystem), '') || '|' ||
    COALESCE(btrim(o.node_task), '') || '|' ||
    COALESCE(btrim(o.plan_date), '')
  ) AS node_id,

  -- === 原始业务字段（nullif_placeholder：清除 /, #VALUE!, N/A 等占位符）===
  nullif_placeholder(o.project_no)          AS project_no,
  nullif_placeholder(o.subsystem)           AS subsystem,
  nullif_placeholder(o.node_task)           AS node_task,
  nullif_placeholder(o.owner)               AS owner,
  nullif_placeholder(o.dept)                AS dept,
  nullif_placeholder(o.dept_leader)         AS dept_leader,
  nullif_placeholder(o.collab_dept)         AS collab_dept,
  nullif_placeholder(o.supervisor_dept)     AS supervisor_dept,
  nullif_placeholder(o.incomplete_reason)   AS incomplete_reason,
  nullif_placeholder(o.risk_content)        AS risk_content,
  nullif_placeholder(o.delay_impact)        AS delay_impact,
  nullif_placeholder(o.institute_leader)    AS institute_leader,
  nullif_placeholder(o.project_manager)     AS project_manager,
  nullif_placeholder(o.filled_by)           AS filled_by,
  nullif_placeholder(o.highlight)           AS highlight,
  nullif_placeholder(o.deliverable)         AS deliverable,
  parse_numeric_safe(o.last_update_week)::int AS last_update_week,

  -- === 枚举标准化 ===
  nullif_placeholder(o.completion_status)   AS completion_status,
  COALESCE(cs.is_completed, false)          AS is_completed,
  COALESCE(cs.is_on_time, false)            AS is_on_time,
  COALESCE(cs.is_overdue_completed, false)  AS is_overdue_completed,
  COALESCE(cs.is_incomplete, false)         AS is_incomplete,

  nullif_placeholder(o.node_type)           AS node_type,
  COALESCE(nt.is_general, false)            AS is_general_node,

  nullif_placeholder(o.risk_level)          AS risk_level,

  nullif_placeholder(o.source)              AS data_source,
  nullif_placeholder(o.delay_applied)       AS delay_applied,

  -- === 日期解析 ===
  parse_date_safe(o.plan_date)             AS plan_date,
  parse_date_safe(o.actual_date)           AS actual_date,
  parse_date_safe(o.delay_expected_date)   AS delay_expected_date,
  parse_date_safe(o.original_plan_date)    AS original_plan_date,
  parse_date_safe(o.last_update_time)      AS last_update_time,

  -- === 周数（parse_numeric_safe：安全解析整数，忽略 #VALUE! 等）===
  parse_numeric_safe(o.plan_week)::int     AS plan_week,
  parse_numeric_safe(o.actual_week)::int   AS actual_week,

  -- === 时间维度标签 ===
  EXTRACT(YEAR FROM parse_date_safe(o.plan_date))::int            AS plan_year,
  EXTRACT(QUARTER FROM parse_date_safe(o.plan_date))::int         AS plan_quarter,
  to_char(parse_date_safe(o.plan_date), 'YYYY-MM')                AS plan_month,
  EXTRACT(WEEK FROM parse_date_safe(o.plan_date))::int            AS plan_week_of_year,
  to_char(parse_date_safe(o.plan_date), 'IYYY-"W"IW')            AS plan_iso_week,

  EXTRACT(YEAR FROM parse_date_safe(o.actual_date))::int          AS actual_year,
  to_char(parse_date_safe(o.actual_date), 'YYYY-MM')              AS actual_month,

  -- === 衍生字段 ===
  CASE
    WHEN parse_date_safe(o.plan_date) IS NOT NULL
     AND parse_date_safe(o.actual_date) IS NOT NULL
    THEN (parse_date_safe(o.actual_date) - parse_date_safe(o.plan_date))::int
  END AS delay_days,

  CASE
    WHEN parse_date_safe(o.plan_date) IS NOT NULL
     AND parse_date_safe(o.plan_date) <= current_date
    THEN true
    ELSE false
  END AS is_due,

  'ods_project_subject_domain'::text AS source_table,
  now() AS etl_time

FROM public.%I o
LEFT JOIN public.dim_completion_status cs
  ON cs.code = nullif_placeholder(o.completion_status)
LEFT JOIN public.dim_node_type nt
  ON nt.code = nullif_placeholder(o.node_type)
WHERE btrim(COALESCE(o.project_no, '')) != ''
  AND btrim(COALESCE(o.plan_date, '')) != ''
$fmt$, ods_table);
END $$;

CREATE INDEX IF NOT EXISTS idx_biz_dwd_project_node_year     ON public.biz_dwd_project_node(plan_year);
CREATE INDEX IF NOT EXISTS idx_biz_dwd_project_node_month    ON public.biz_dwd_project_node(plan_month);
CREATE INDEX IF NOT EXISTS idx_biz_dwd_project_node_proj     ON public.biz_dwd_project_node(project_no);
CREATE INDEX IF NOT EXISTS idx_biz_dwd_project_node_status   ON public.biz_dwd_project_node(completion_status);

ANALYZE public.biz_dwd_project_node;

-- ------------------------------------------------------------
-- 3.2) DWD：项目节点富化宽表
-- 来源：biz_dwd_project_node + dim + 映射表
-- v2.2.2：使用 _derived_major_project_name / _derived_subproject_name
--         作为 COALESCE 回退，替代直接使用 project_no 字符串
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_dwd_project_node_enriched CASCADE;
CREATE TABLE public.biz_dwd_project_node_enriched AS
WITH base AS (
  SELECT *
  FROM public.biz_dwd_project_node
),
classified AS (
  SELECT
    b.*,
    -- project_no = 项目名称，subsystem = 子项目名称
    -- 不再按 / 拆分，直接使用原始字段
    b.project_no AS _derived_major_project_name,
    b.subsystem  AS _derived_subproject_name,
    CASE
      WHEN NULLIF(btrim(COALESCE(b.incomplete_reason, '')), '') IS NULL THEN 'normal'
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
LEFT JOIN public.pm_map_node_subject map
  ON map.project_no = c.project_no
 AND map.subsystem = c.subsystem
 AND map.node_task = c.node_task
LEFT JOIN public.pm_dim_subproject sp
  ON sp.subproject_id = map.subproject_id
LEFT JOIN public.pm_dim_major_project mp
  ON mp.major_project_id = COALESCE(map.major_project_id, sp.major_project_id)
LEFT JOIN public.pm_dim_delay_reason dr
  ON dr.delay_reason_category = COALESCE(map.delay_reason_category, c.delay_reason_category_fallback);

CREATE INDEX IF NOT EXISTS idx_biz_dwd_enriched_major ON public.biz_dwd_project_node_enriched(major_project_id);
CREATE INDEX IF NOT EXISTS idx_biz_dwd_enriched_sub   ON public.biz_dwd_project_node_enriched(subproject_id);
CREATE INDEX IF NOT EXISTS idx_biz_dwd_enriched_month ON public.biz_dwd_project_node_enriched(plan_month);

ANALYZE public.biz_dwd_project_node_enriched;

-- ============================================================
-- 4) DWS：汇总层
-- ============================================================

-- ------------------------------------------------------------
-- 4.1) DWS：期间节点汇总（按年/季/月/项目）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_dws_period_node_summary CASCADE;
CREATE TABLE public.biz_dws_period_node_summary AS
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

FROM public.biz_dwd_project_node d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month, d.project_no;

ANALYZE public.biz_dws_period_node_summary;

-- ------------------------------------------------------------
-- 4.2) DWS：期间节点类型汇总（按年/季/月/项目/节点类型）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_dws_period_node_type_summary CASCADE;
CREATE TABLE public.biz_dws_period_node_type_summary AS
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

FROM public.biz_dwd_project_node d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month, d.project_no, d.node_type, d.is_general_node;

ANALYZE public.biz_dws_period_node_type_summary;

-- ------------------------------------------------------------
-- 4.3) DWS：期间风险汇总（按年/季/月/项目/风险等级/节点类型）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_dws_period_risk_summary CASCADE;
CREATE TABLE public.biz_dws_period_risk_summary AS
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

FROM public.biz_dwd_project_node d
WHERE d.plan_year IS NOT NULL
  AND d.risk_level IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month, d.project_no, d.risk_level, d.node_type;

ANALYZE public.biz_dws_period_risk_summary;

-- ------------------------------------------------------------
-- 4.4) DWS：周度子项目汇总
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_dws_week_subproject_summary CASCADE;
CREATE TABLE public.biz_dws_week_subproject_summary AS
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
FROM public.biz_dwd_project_node_enriched
WHERE subproject_id IS NOT NULL
  AND plan_date IS NOT NULL
GROUP BY
  major_project_id,
  major_project_name,
  subproject_id,
  subproject_name,
  date_trunc('week', plan_date);

ANALYZE public.biz_dws_week_subproject_summary;

-- ============================================================
-- 5) ADS：应用层
-- ============================================================

-- ------------------------------------------------------------
-- 5.1) ADS：未完成节点风险 KPI
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_ads_project_incomplete_risk CASCADE;
CREATE TABLE public.biz_ads_project_incomplete_risk AS
SELECT
  d.plan_year,
  d.plan_quarter,
  d.plan_month,

  SUM(CASE WHEN d.risk_level = '高' AND d.is_incomplete THEN 1 ELSE 0 END)        AS incomplete_high_risk_cnt,
  SUM(CASE WHEN d.risk_level = '中' AND d.is_incomplete THEN 1 ELSE 0 END)        AS incomplete_mid_risk_cnt,
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_incomplete THEN 1 ELSE 0 END) AS incomplete_milestone_cnt,
  SUM(CASE WHEN d.node_type = '重大节点' AND d.is_incomplete THEN 1 ELSE 0 END)   AS incomplete_major_cnt,
  SUM(CASE WHEN d.node_type = '重要节点' AND d.is_incomplete THEN 1 ELSE 0 END)   AS incomplete_important_cnt

FROM public.biz_dwd_project_node d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month
ORDER BY d.plan_year, d.plan_month;

ANALYZE public.biz_ads_project_incomplete_risk;

-- ------------------------------------------------------------
-- 5.2) ADS：项目 KPI 总览（含一般节点）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_ads_project_kpi_overview CASCADE;
CREATE TABLE public.biz_ads_project_kpi_overview AS
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
  FROM public.biz_dws_period_node_summary
  GROUP BY plan_year, plan_quarter, plan_month
),

outside_completed AS (
  SELECT
    d.actual_year   AS plan_year,
    d.actual_month  AS plan_month,
    COUNT(*)        AS outside_completed_cnt
  FROM public.biz_dwd_project_node d
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

ANALYZE public.biz_ads_project_kpi_overview;

-- ------------------------------------------------------------
-- 5.3) ADS：里程碑节点 KPI
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_ads_project_milestone_kpi CASCADE;
CREATE TABLE public.biz_ads_project_milestone_kpi AS
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

FROM public.biz_dwd_project_node d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_quarter, d.plan_month
ORDER BY d.plan_year, d.plan_month;

ANALYZE public.biz_ads_project_milestone_kpi;

-- ------------------------------------------------------------
-- 5.4) ADS：项目 KPI（除一般节点）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_ads_project_non_general_kpi CASCADE;
CREATE TABLE public.biz_ads_project_non_general_kpi AS
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
  FROM public.biz_dws_period_node_type_summary
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

ANALYZE public.biz_ads_project_non_general_kpi;

-- ------------------------------------------------------------
-- 5.5) ADS：延期原因趋势
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_ads_delay_reason_trend CASCADE;
CREATE TABLE public.biz_ads_delay_reason_trend AS
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
FROM public.biz_dwd_project_node_enriched
WHERE plan_date IS NOT NULL
GROUP BY
  major_project_id,
  major_project_name,
  dept,
  date_trunc('week', plan_date),
  delay_reason_category,
  delay_reason_label;

ANALYZE public.biz_ads_delay_reason_trend;

-- ------------------------------------------------------------
-- 5.6) ADS：大项目概览
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_ads_major_project_overview CASCADE;
CREATE TABLE public.biz_ads_major_project_overview AS
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
FROM public.biz_dwd_project_node_enriched
WHERE major_project_id IS NOT NULL
GROUP BY major_project_id, major_project_name;

ANALYZE public.biz_ads_major_project_overview;

-- ------------------------------------------------------------
-- 5.7) ADS：大项目树形快照（三级树：大项目/子项目/节点）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_ads_major_project_tree_snapshot CASCADE;
CREATE TABLE public.biz_ads_major_project_tree_snapshot AS
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
  FROM public.biz_dwd_project_node_enriched
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
  FROM public.biz_dwd_project_node_enriched
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
  FROM public.biz_dwd_project_node_enriched
  WHERE subproject_id IS NOT NULL
)
SELECT * FROM major_level
UNION ALL
SELECT * FROM subproject_level
UNION ALL
SELECT * FROM node_level;

ANALYZE public.biz_ads_major_project_tree_snapshot;

COMMIT;

-- ============================================================
-- 6) 索引（事务外，可并行，失败不影响数据）
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_biz_dws_period_node_sum_month  ON public.biz_dws_period_node_summary(plan_month);
CREATE INDEX IF NOT EXISTS idx_biz_dws_period_node_sum_proj   ON public.biz_dws_period_node_summary(project_no);
CREATE INDEX IF NOT EXISTS idx_biz_dws_node_type_month        ON public.biz_dws_period_node_type_summary(plan_month);
CREATE INDEX IF NOT EXISTS idx_biz_dws_risk_month             ON public.biz_dws_period_risk_summary(plan_month);
CREATE INDEX IF NOT EXISTS idx_biz_dws_week_sub_sub           ON public.biz_dws_week_subproject_summary(subproject_id);
CREATE INDEX IF NOT EXISTS idx_biz_dws_week_sub_major         ON public.biz_dws_week_subproject_summary(major_project_id);
CREATE INDEX IF NOT EXISTS idx_biz_ads_kpi_month              ON public.biz_ads_project_kpi_overview(plan_month);
CREATE INDEX IF NOT EXISTS idx_biz_ads_milestone_month        ON public.biz_ads_project_milestone_kpi(plan_month);
CREATE INDEX IF NOT EXISTS idx_biz_ads_non_general_month      ON public.biz_ads_project_non_general_kpi(plan_month);
CREATE INDEX IF NOT EXISTS idx_biz_ads_incomplete_risk_month  ON public.biz_ads_project_incomplete_risk(plan_month);
CREATE INDEX IF NOT EXISTS idx_biz_ads_delay_reason_week      ON public.biz_ads_delay_reason_trend(plan_iso_week);
CREATE INDEX IF NOT EXISTS idx_biz_ads_major_overview_id      ON public.biz_ads_major_project_overview(major_project_id);
CREATE INDEX IF NOT EXISTS idx_biz_ads_tree_snap_level        ON public.biz_ads_major_project_tree_snapshot(snapshot_level);
CREATE INDEX IF NOT EXISTS idx_biz_ads_tree_snap_major        ON public.biz_ads_major_project_tree_snapshot(major_project_id);

-- ============================================================
-- 7) 构建完成摘要
-- ============================================================
SELECT
  tablename AS "表名",
  (SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = t.tablename) AS "字段数",
  (xpath('/row/c/text()',
    query_to_xml(format('SELECT count(*) AS c FROM public.%I', tablename), true, true, ''))
  )[1]::text::int AS "行数"
FROM (VALUES
  ('dim_completion_status'),
  ('dim_node_type'),
  ('dim_risk_level'),
  ('pm_dim_delay_reason'),
  ('pm_dim_major_project'),
  ('pm_dim_subproject'),
  ('pm_map_node_subject'),
  ('biz_dwd_project_node'),
  ('biz_dwd_project_node_enriched'),
  ('biz_dws_period_node_summary'),
  ('biz_dws_period_node_type_summary'),
  ('biz_dws_period_risk_summary'),
  ('biz_dws_week_subproject_summary'),
  ('biz_ads_project_kpi_overview'),
  ('biz_ads_project_milestone_kpi'),
  ('biz_ads_project_non_general_kpi'),
  ('biz_ads_project_incomplete_risk'),
  ('biz_ads_delay_reason_trend'),
  ('biz_ads_major_project_overview'),
  ('biz_ads_major_project_tree_snapshot')
) AS t(tablename);

-- ============================================================
-- 以下表已创建（按依赖顺序）：
--
-- === 辅助函数（3 个）===
--   parse_date_safe()         — 安全解析多格式日期字符串
--   nullif_placeholder()      — 清除 /, #VALUE!, N/A 等占位符，返回 NULL
--   parse_numeric_safe()      — 安全解析数值，支持千分位逗号
--
-- === DIM 维度表（7 张，无 seed 依赖）===
--   dim_completion_status            — 完成状态（7 种，内联 VALUES）
--   dim_node_type                    — 节点类型（4 种，内联 VALUES）
--   dim_risk_level                   — 风险等级（3 种，内联 VALUES）
--   pm_dim_delay_reason              — 延期原因（8 种，内联 VALUES）
--   pm_dim_major_project             — 项目（from ODS，以 project_no 为 ID）
--   pm_dim_subproject                — 子项目（from ODS，以 subsystem 为名称）
--   pm_map_node_subject              — 节点-主题映射（from ODS，node_type 自动推导）
--
-- === DWD 明细层（2 张）===
--   biz_dwd_project_node             — 节点明细（from ODS，新增 deliverable/last_update_week）
--   biz_dwd_project_node_enriched    — 节点富化宽表（from dwd + dim）
--
-- === DWS 汇总层（4 张）===
--   biz_dws_period_node_summary      — 期间节点汇总
--   biz_dws_period_node_type_summary — 期间节点类型汇总
--   biz_dws_period_risk_summary      — 期间风险汇总
--   biz_dws_week_subproject_summary  — 周度子项目汇总
--
-- === ADS 应用层（7 张）===
--   biz_ads_project_kpi_overview     — KPI 总览（含一般节点）
--   biz_ads_project_milestone_kpi    — 里程碑 KPI
--   biz_ads_project_non_general_kpi  — KPI（除一般节点）
--   biz_ads_project_incomplete_risk  — 未完成风险 KPI
--   biz_ads_delay_reason_trend       — 延期原因趋势
--   biz_ads_major_project_overview   — 大项目概览
--   biz_ads_major_project_tree_snapshot — 大项目树形快照
--
-- v2.2.2 变更说明：
--   - 移除 4 张 seed 表（pm_dim_delay_reason_seed / pm_dim_major_project_seed /
--     pm_dim_subproject_seed / pm_map_node_subject_seed），全量改为 ODS 自动推导
--   - pm_dim_delay_reason 改为内联 VALUES，不再依赖 seed
--   - biz_dwd_project_node 新增字段：deliverable、last_update_week
--   - biz_dwd_project_node_enriched classified CTE 新增衍生字段：
--     _derived_major_project_name、_derived_subproject_name
--
-- 合计：3 函数 + 7 DIM + 2 DWD + 4 DWS + 7 ADS = 20 张表
-- ============================================================
