-- ============================================================
-- 项目管理数仓模型一键构建（无需 dbt）
-- 适用场景：Excel/CSV 导入 ODS 后，手工/调度执行本 SQL
-- 目标库：PostgreSQL（public schema）
-- 数据域：项目节点管理（project-cockpit）
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

-- ============================================================
-- 1) SEED 表（主数据，CSV 导入等价物）
-- ============================================================

-- ------------------------------------------------------------
-- 1.1) SEED：延期原因种子表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.pm_dim_delay_reason_seed CASCADE;
CREATE TABLE public.pm_dim_delay_reason_seed (
  delay_reason_category text,
  delay_reason_label    text,
  description           text
);

INSERT INTO public.pm_dim_delay_reason_seed (delay_reason_category, delay_reason_label, description) VALUES
  ('normal',       '正常推进', '计划内推进或已按时完成'),
  ('technical',    '技术攻关', '关键技术或算法攻关导致延期'),
  ('quality',      '质量整改', '质量问题或试验整改导致延期'),
  ('change',       '计划变更', '计划调整或技术状态变更导致延期'),
  ('coordination', '接口协同', '跨部门接口或联调协同导致延期'),
  ('supplier',     '外协外购', '器件到货或外协加工导致延期'),
  ('test',         '试验排期', '测试排队或标定复测导致延期'),
  ('archive',      '资料归档', '归零报告周报归档等收尾工作拖延');

-- ------------------------------------------------------------
-- 1.2) SEED：重大项目种子表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.pm_dim_major_project_seed CASCADE;
CREATE TABLE public.pm_dim_major_project_seed (
  major_project_id   text,
  major_project_code text,
  major_project_name text,
  program_id         text,
  program_name       text,
  project_level      text,
  owner_dept         text,
  owner_leader       text,
  priority_level     text,
  start_date         date,
  plan_end_date      date,
  status             text,
  remark             text
);

INSERT INTO public.pm_dim_major_project_seed (major_project_id, major_project_code, major_project_name, program_id, program_name, project_level, owner_dept, owner_leader, priority_level, start_date, plan_end_date, status, remark) VALUES
  ('major-aurora',  'AURORA',  '苍穹导航综合工程',   'program-core',     '核心装备群',   '重大项目', '导航室', '李总', 'A', '2026-01-01', '2026-06-30', '执行中', '导航链路重点攻关'),
  ('major-beacon',  'BEACON',  '北斗信号增强工程',   'program-core',     '核心装备群',   '重大项目', '通信室', '吴总', 'A', '2026-01-01', '2026-07-31', '执行中', '信号与天线并行推进'),
  ('major-cosmos',  'COSMOS',  '星链信号攻关工程',   'program-advanced', '先进预研群',   '重大项目', '雷达室', '林总', 'A', '2025-11-01', '2026-08-31', '执行中', '正样评审前冲刺'),
  ('major-dragon',  'DRAGON',  '龙眼光电探测工程',   'program-opto',     '光电探测群',   '重大项目', '光学室', '梁总', 'A', '2026-01-01', '2026-07-15', '执行中', '交付评审关键期');

-- ------------------------------------------------------------
-- 1.3) SEED：子项目种子表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.pm_dim_subproject_seed CASCADE;
CREATE TABLE public.pm_dim_subproject_seed (
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

INSERT INTO public.pm_dim_subproject_seed (subproject_id, subproject_code, subproject_name, major_project_id, project_no, subsystem_name, owner_dept, owner_user, project_manager, plan_start_date, plan_end_date, actual_end_date, status, priority_level, remark) VALUES
  ('sub-aurora-nav',      'AURORA-NAV',  '导航处理机',       'major-aurora', 'PRJ-A001', '导航处理机',       '导航室', '张三', '赵主管', '2026-01-01', '2026-04-30', NULL, '重点关注', 'A', '算法与试验链路'),
  ('sub-beacon-signal',   'BEACON-SIG',  '信号处理分系统',   'major-beacon', 'PRJ-B002', '信号处理分系统',   '通信室', '钱八', '吴主管', '2026-01-01', '2026-04-30', NULL, '重点关注', 'A', 'FPGA链路'),
  ('sub-beacon-antenna',  'BEACON-ANT',  '天线分系统',       'major-beacon', 'PRJ-B002', '天线分系统',       '天线室', '冯十', '吴主管', '2026-01-01', '2026-03-31', NULL, '稳态推进', 'B', '暗室标定与归零'),
  ('sub-cosmos-signal',   'COSMOS-SIG',  '信号处理攻关',     'major-cosmos', 'PRJ-C003', '信号处理',         '雷达室', '陈一', '林主管', '2025-11-01', '2026-04-30', NULL, '高风险',   'A', '正样前技术攻关'),
  ('sub-cosmos-link',     'COSMOS-LINK', '数据链路',         'major-cosmos', 'PRJ-C003', '数据链路',         '雷达室', '吕四', '林主管', '2026-01-01', '2026-03-31', NULL, '稳态推进', 'B', '接口协同'),
  ('sub-dragon-optical',  'DRAGON-OPT',  '光学分系统',       'major-dragon', 'PRJ-D004', '光学分系统',       '光学室', '周五', '梁主管', '2026-01-01', '2026-04-30', NULL, '重点关注', 'A', '光机装调'),
  ('sub-dragon-detector', 'DRAGON-DET',  '探测器分系统',     'major-dragon', 'PRJ-D004', '探测器分系统',     '探测室', '郑七', '梁主管', '2026-01-01', '2026-03-31', NULL, '重点关注', 'B', '到货与复测');

-- ------------------------------------------------------------
-- 1.4) SEED：节点-主题映射种子表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.pm_map_node_subject_seed CASCADE;
CREATE TABLE public.pm_map_node_subject_seed (
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

INSERT INTO public.pm_map_node_subject_seed (map_id, project_no, subsystem, node_task, subproject_id, major_project_id, node_category, delay_reason_category, is_key_node, is_milestone, sort_order, source_flag, remark) VALUES
  ('MAP-001', 'PRJ-A001', '导航处理机', '完成方案设计评审',   'sub-aurora-nav',      'major-aurora', 'milestone', 'normal',       true,  true,  1,  'demo', NULL),
  ('MAP-002', 'PRJ-A001', '导航处理机', '关键器件到货确认',   'sub-aurora-nav',      'major-aurora', 'critical',  'supplier',     true,  false, 2,  'demo', NULL),
  ('MAP-003', 'PRJ-A001', '导航处理机', 'PCB设计完成',        'sub-aurora-nav',      'major-aurora', 'critical',  'normal',       true,  false, 3,  'demo', NULL),
  ('MAP-004', 'PRJ-A001', '导航处理机', '联调测试启动',       'sub-aurora-nav',      'major-aurora', 'milestone', 'coordination', true,  true,  4,  'demo', NULL),
  ('MAP-005', 'PRJ-A001', '导航处理机', '软件需求分析',       'sub-aurora-nav',      'major-aurora', 'routine',   'normal',       false, false, 5,  'demo', NULL),
  ('MAP-006', 'PRJ-A001', '导航处理机', '关键算法验证',       'sub-aurora-nav',      'major-aurora', 'critical',  'technical',    true,  false, 6,  'demo', NULL),
  ('MAP-007', 'PRJ-A001', '导航处理机', '可靠性试验',         'sub-aurora-nav',      'major-aurora', 'critical',  'quality',      true,  false, 7,  'demo', NULL),
  ('MAP-008', 'PRJ-A001', '导航处理机', '初样交付',           'sub-aurora-nav',      'major-aurora', 'milestone', 'change',       true,  true,  8,  'demo', NULL),
  ('MAP-009', 'PRJ-A001', '导航处理机', 'EMC测试',            'sub-aurora-nav',      'major-aurora', 'routine',   'quality',      false, false, 9,  'demo', NULL),
  ('MAP-010', 'PRJ-A001', '导航处理机', '周报汇总',           'sub-aurora-nav',      'major-aurora', 'routine',   'archive',      false, false, 10, 'demo', NULL),
  ('MAP-011', 'PRJ-B002', '信号处理分系统', '总体方案评审',   'sub-beacon-signal',   'major-beacon', 'milestone', 'normal',       true,  true,  1,  'demo', NULL),
  ('MAP-012', 'PRJ-B002', '信号处理分系统', 'FPGA代码编写',   'sub-beacon-signal',   'major-beacon', 'critical',  'normal',       true,  false, 2,  'demo', NULL),
  ('MAP-013', 'PRJ-B002', '信号处理分系统', 'FPGA仿真验证',   'sub-beacon-signal',   'major-beacon', 'critical',  'change',       true,  false, 3,  'demo', NULL),
  ('MAP-014', 'PRJ-B002', '信号处理分系统', 'PCB投板',        'sub-beacon-signal',   'major-beacon', 'routine',   'normal',       false, false, 4,  'demo', NULL),
  ('MAP-015', 'PRJ-B002', '信号处理分系统', '整机联调',       'sub-beacon-signal',   'major-beacon', 'milestone', 'coordination', true,  true,  5,  'demo', NULL),
  ('MAP-016', 'PRJ-B002', '天线分系统',     '天线样机加工',   'sub-beacon-antenna',  'major-beacon', 'critical',  'change',       true,  false, 1,  'demo', NULL),
  ('MAP-017', 'PRJ-B002', '天线分系统',     '天线方向图测试', 'sub-beacon-antenna',  'major-beacon', 'critical',  'test',         true,  false, 2,  'demo', NULL),
  ('MAP-018', 'PRJ-B002', '天线分系统',     '天线暗室标定',   'sub-beacon-antenna',  'major-beacon', 'routine',   'change',       false, false, 3,  'demo', NULL),
  ('MAP-019', 'PRJ-B002', '天线分系统',     '技术归零报告',   'sub-beacon-antenna',  'major-beacon', 'routine',   'change',       false, false, 4,  'demo', NULL),
  ('MAP-020', 'PRJ-B002', '天线分系统',     '日常检查',       'sub-beacon-antenna',  'major-beacon', 'routine',   'normal',       false, false, 5,  'demo', NULL),
  ('MAP-021', 'PRJ-C003', '信号处理',   '系统方案论证',       'sub-cosmos-signal',   'major-cosmos', 'milestone', 'normal',       true,  true,  1,  'demo', NULL),
  ('MAP-022', 'PRJ-C003', '信号处理',   '关键技术攻关',       'sub-cosmos-signal',   'major-cosmos', 'critical',  'technical',    true,  false, 2,  'demo', NULL),
  ('MAP-023', 'PRJ-C003', '信号处理',   '仿真平台搭建',       'sub-cosmos-signal',   'major-cosmos', 'critical',  'normal',       true,  false, 3,  'demo', NULL),
  ('MAP-024', 'PRJ-C003', '信号处理',   '算法原型验证',       'sub-cosmos-signal',   'major-cosmos', 'critical',  'normal',       true,  false, 4,  'demo', NULL),
  ('MAP-025', 'PRJ-C003', '信号处理',   '硬件原型设计',       'sub-cosmos-signal',   'major-cosmos', 'critical',  'technical',    true,  false, 5,  'demo', NULL),
  ('MAP-026', 'PRJ-C003', '信号处理',   '软件架构设计',       'sub-cosmos-signal',   'major-cosmos', 'routine',   'normal',       false, false, 6,  'demo', NULL),
  ('MAP-027', 'PRJ-C003', '信号处理',   '正样评审',           'sub-cosmos-signal',   'major-cosmos', 'milestone', 'normal',       true,  true,  7,  'demo', NULL),
  ('MAP-028', 'PRJ-C003', '信号处理',   '硬件调试',           'sub-cosmos-signal',   'major-cosmos', 'critical',  'normal',       true,  false, 8,  'demo', NULL),
  ('MAP-029', 'PRJ-C003', '数据链路',   '链路预算分析',       'sub-cosmos-link',     'major-cosmos', 'routine',   'coordination', false, false, 1,  'demo', NULL),
  ('MAP-030', 'PRJ-C003', '数据链路',   '接口协议定义',       'sub-cosmos-link',     'major-cosmos', 'critical',  'change',       true,  false, 2,  'demo', NULL),
  ('MAP-031', 'PRJ-D004', '光学分系统', '光学设计完成',       'sub-dragon-optical',  'major-dragon', 'milestone', 'normal',       true,  true,  1,  'demo', NULL),
  ('MAP-032', 'PRJ-D004', '光学分系统', '镜片加工',           'sub-dragon-optical',  'major-dragon', 'critical',  'supplier',     true,  false, 2,  'demo', NULL),
  ('MAP-033', 'PRJ-D004', '光学分系统', '光机装调',           'sub-dragon-optical',  'major-dragon', 'critical',  'quality',      true,  false, 3,  'demo', NULL),
  ('MAP-034', 'PRJ-D004', '光学分系统', '环境适应性测试',     'sub-dragon-optical',  'major-dragon', 'critical',  'test',         true,  false, 4,  'demo', NULL),
  ('MAP-035', 'PRJ-D004', '光学分系统', '交付评审',           'sub-dragon-optical',  'major-dragon', 'milestone', 'normal',       true,  true,  5,  'demo', NULL),
  ('MAP-036', 'PRJ-D004', '探测器分系统', '探测器选型',       'sub-dragon-detector', 'major-dragon', 'routine',   'normal',       false, false, 1,  'demo', NULL),
  ('MAP-037', 'PRJ-D004', '探测器分系统', '探测器到货验收',   'sub-dragon-detector', 'major-dragon', 'critical',  'change',       true,  false, 2,  'demo', NULL),
  ('MAP-038', 'PRJ-D004', '探测器分系统', '探测器标定',       'sub-dragon-detector', 'major-dragon', 'critical',  'test',         true,  false, 3,  'demo', NULL),
  ('MAP-039', 'PRJ-D004', '探测器分系统', '探测灵敏度复测',   'sub-dragon-detector', 'major-dragon', 'routine',   'change',       false, false, 4,  'demo', NULL),
  ('MAP-040', 'PRJ-D004', '探测器分系统', '文档归档',         'sub-dragon-detector', 'major-dragon', 'routine',   'archive',      false, false, 5,  'demo', NULL);

-- ============================================================
-- 2) DIM 维度表（静态枚举 + seed 衍生）
-- ============================================================

-- ------------------------------------------------------------
-- 2.1) DIM：完成状态维度
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

-- ------------------------------------------------------------
-- 2.2) DIM：节点类型维度
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

-- ------------------------------------------------------------
-- 2.3) DIM：风险等级维度
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.dim_risk_level CASCADE;
CREATE TABLE public.dim_risk_level AS
SELECT code, label, severity_rank
FROM (VALUES
  ('高', '高风险', 3),
  ('中', '中风险', 2),
  ('低', '低风险', 1)
) AS t(code, label, severity_rank);

-- ------------------------------------------------------------
-- 2.4) DIM：延期原因维度（从 seed 构建）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.pm_dim_delay_reason CASCADE;
CREATE TABLE public.pm_dim_delay_reason AS
SELECT
  NULLIF(btrim(delay_reason_category), '') AS delay_reason_category,
  NULLIF(btrim(delay_reason_label), '') AS delay_reason_label,
  NULLIF(btrim(description), '') AS description
FROM public.pm_dim_delay_reason_seed;

-- ------------------------------------------------------------
-- 2.5) DIM：重大项目维度（从 seed 构建）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.pm_dim_major_project CASCADE;
CREATE TABLE public.pm_dim_major_project AS
SELECT
  NULLIF(btrim(major_project_id), '') AS major_project_id,
  NULLIF(btrim(major_project_code), '') AS major_project_code,
  NULLIF(btrim(major_project_name), '') AS major_project_name,
  NULLIF(btrim(program_id), '') AS program_id,
  NULLIF(btrim(program_name), '') AS program_name,
  NULLIF(btrim(project_level), '') AS project_level,
  NULLIF(btrim(owner_dept), '') AS owner_dept,
  NULLIF(btrim(owner_leader), '') AS owner_leader,
  NULLIF(btrim(priority_level), '') AS priority_level,
  CASE WHEN start_date IS NULL THEN NULL ELSE start_date::date END AS start_date,
  CASE WHEN plan_end_date IS NULL THEN NULL ELSE plan_end_date::date END AS plan_end_date,
  NULLIF(btrim(status), '') AS status,
  NULLIF(btrim(remark), '') AS remark
FROM public.pm_dim_major_project_seed;

-- ------------------------------------------------------------
-- 2.6) DIM：子项目维度（从 seed 构建）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.pm_dim_subproject CASCADE;
CREATE TABLE public.pm_dim_subproject AS
SELECT
  NULLIF(btrim(subproject_id), '') AS subproject_id,
  NULLIF(btrim(subproject_code), '') AS subproject_code,
  NULLIF(btrim(subproject_name), '') AS subproject_name,
  NULLIF(btrim(major_project_id), '') AS major_project_id,
  NULLIF(btrim(project_no), '') AS project_no,
  NULLIF(btrim(subsystem_name), '') AS subsystem_name,
  NULLIF(btrim(owner_dept), '') AS owner_dept,
  NULLIF(btrim(owner_user), '') AS owner_user,
  NULLIF(btrim(project_manager), '') AS project_manager,
  CASE WHEN plan_start_date IS NULL THEN NULL ELSE plan_start_date::date END AS plan_start_date,
  CASE WHEN plan_end_date IS NULL THEN NULL ELSE plan_end_date::date END AS plan_end_date,
  CASE
    WHEN actual_end_date IS NULL THEN NULL
    WHEN actual_end_date::text ~ '^\d{8}$' THEN to_date(actual_end_date::text, 'YYYYMMDD')
    WHEN actual_end_date::text ~ '^\d{4}-\d{2}-\d{2}$' THEN actual_end_date::text::date
    ELSE NULL
  END AS actual_end_date,
  NULLIF(btrim(status), '') AS status,
  NULLIF(btrim(priority_level), '') AS priority_level,
  NULLIF(btrim(remark), '') AS remark
FROM public.pm_dim_subproject_seed;

-- ------------------------------------------------------------
-- 2.7) DIM：节点-主题映射（从 seed 构建）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.pm_map_node_subject CASCADE;
CREATE TABLE public.pm_map_node_subject AS
SELECT
  NULLIF(btrim(map_id), '') AS map_id,
  NULLIF(btrim(project_no), '') AS project_no,
  NULLIF(btrim(subsystem), '') AS subsystem,
  NULLIF(btrim(node_task), '') AS node_task,
  NULLIF(btrim(subproject_id), '') AS subproject_id,
  NULLIF(btrim(major_project_id), '') AS major_project_id,
  NULLIF(btrim(node_category), '') AS node_category,
  NULLIF(btrim(delay_reason_category), '') AS delay_reason_category,
  CASE
    WHEN is_key_node IS TRUE THEN true
    WHEN lower(COALESCE(is_key_node::text, '')) IN ('true', 't', '1', 'yes', 'y') THEN true
    ELSE false
  END AS is_key_node,
  CASE
    WHEN is_milestone IS TRUE THEN true
    WHEN lower(COALESCE(is_milestone::text, '')) IN ('true', 't', '1', 'yes', 'y') THEN true
    ELSE false
  END AS is_milestone,
  CASE WHEN sort_order IS NULL THEN NULL ELSE sort_order::int END AS sort_order,
  NULLIF(btrim(source_flag), '') AS source_flag,
  CASE
    WHEN remark IS NULL THEN NULL
    ELSE NULLIF(btrim(remark::text), '')
  END AS remark
FROM public.pm_map_node_subject_seed;

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

  -- === 原始业务字段 ===
  NULLIF(btrim(o.project_no), '')          AS project_no,
  NULLIF(btrim(o.subsystem), '')           AS subsystem,
  NULLIF(btrim(o.node_task), '')           AS node_task,
  NULLIF(btrim(o.owner), '')               AS owner,
  NULLIF(btrim(o.dept), '')                AS dept,
  NULLIF(btrim(o.dept_leader), '')         AS dept_leader,
  NULLIF(btrim(o.collab_dept), '')         AS collab_dept,
  NULLIF(btrim(o.supervisor_dept), '')     AS supervisor_dept,
  NULLIF(btrim(o.incomplete_reason), '')   AS incomplete_reason,
  NULLIF(btrim(o.risk_content), '')        AS risk_content,
  NULLIF(btrim(o.delay_impact), '')        AS delay_impact,
  NULLIF(btrim(o.institute_leader), '')    AS institute_leader,
  NULLIF(btrim(o.project_manager), '')     AS project_manager,
  NULLIF(btrim(o.filled_by), '')           AS filled_by,
  NULLIF(btrim(o.highlight), '')           AS highlight,

  -- === 枚举标准化 ===
  NULLIF(btrim(o.completion_status), '')   AS completion_status,
  COALESCE(cs.is_completed, false)         AS is_completed,
  COALESCE(cs.is_on_time, false)           AS is_on_time,
  COALESCE(cs.is_overdue_completed, false) AS is_overdue_completed,
  COALESCE(cs.is_incomplete, false)        AS is_incomplete,

  NULLIF(btrim(o.node_type), '')           AS node_type,
  COALESCE(nt.is_general, false)           AS is_general_node,

  NULLIF(btrim(o.risk_level), '')          AS risk_level,

  NULLIF(btrim(o.source), '')              AS data_source,
  NULLIF(btrim(o.delay_applied), '')       AS delay_applied,

  -- === 日期解析 ===
  parse_date_safe(o.plan_date)             AS plan_date,
  parse_date_safe(o.actual_date)           AS actual_date,
  parse_date_safe(o.delay_expected_date)   AS delay_expected_date,
  parse_date_safe(o.original_plan_date)    AS original_plan_date,
  parse_date_safe(o.last_update_time)      AS last_update_time,

  -- === 周数 ===
  CASE WHEN o.plan_week ~ '^\d+$' THEN o.plan_week::int END     AS plan_week,
  CASE WHEN o.actual_week ~ '^\d+$' THEN o.actual_week::int END AS actual_week,

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

  'col_20260312'::text AS source_table,
  now() AS etl_time

FROM public.%I o
LEFT JOIN public.dim_completion_status cs
  ON cs.code = NULLIF(btrim(o.completion_status), '')
LEFT JOIN public.dim_node_type nt
  ON nt.code = NULLIF(btrim(o.node_type), '')
WHERE btrim(COALESCE(o.project_no, '')) != ''
  AND btrim(COALESCE(o.plan_date, '')) != ''
$fmt$, ods_table);
END $$;

CREATE INDEX IF NOT EXISTS idx_biz_dwd_project_node_year     ON public.biz_dwd_project_node(plan_year);
CREATE INDEX IF NOT EXISTS idx_biz_dwd_project_node_month    ON public.biz_dwd_project_node(plan_month);
CREATE INDEX IF NOT EXISTS idx_biz_dwd_project_node_proj     ON public.biz_dwd_project_node(project_no);
CREATE INDEX IF NOT EXISTS idx_biz_dwd_project_node_status   ON public.biz_dwd_project_node(completion_status);

-- ------------------------------------------------------------
-- 3.2) DWD：项目节点富化宽表
-- 来源：biz_dwd_project_node + dim + seed 映射表
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
  mp.major_project_id,
  mp.major_project_code,
  mp.major_project_name,
  mp.program_id,
  mp.program_name,
  mp.project_level AS major_project_level,
  mp.owner_dept AS major_project_owner_dept,
  mp.owner_leader AS major_project_owner_leader,
  mp.priority_level AS major_project_priority_level,
  sp.subproject_id,
  sp.subproject_code,
  sp.subproject_name,
  sp.owner_dept AS subproject_owner_dept,
  sp.owner_user AS subproject_owner_user,
  sp.project_manager AS subproject_owner_manager,
  sp.priority_level AS subproject_priority_level,
  map.map_id,
  map.node_category,
  map.is_key_node,
  map.is_milestone,
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

-- ------------------------------------------------------------
-- 4.4) DWS：周度子项目汇总
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_dws_week_subproject_summary CASCADE;
CREATE TABLE public.biz_dws_week_subproject_summary AS
SELECT
  program_id,
  program_name,
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
  program_id,
  program_name,
  major_project_id,
  major_project_name,
  subproject_id,
  subproject_name,
  date_trunc('week', plan_date);

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

-- ------------------------------------------------------------
-- 5.5) ADS：延期原因趋势
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_ads_delay_reason_trend CASCADE;
CREATE TABLE public.biz_ads_delay_reason_trend AS
SELECT
  program_id,
  program_name,
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
  program_id,
  program_name,
  major_project_id,
  major_project_name,
  dept,
  date_trunc('week', plan_date),
  delay_reason_category,
  delay_reason_label;

-- ------------------------------------------------------------
-- 5.6) ADS：大项目概览
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.biz_ads_major_project_overview CASCADE;
CREATE TABLE public.biz_ads_major_project_overview AS
SELECT
  program_id,
  program_name,
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
GROUP BY program_id, program_name, major_project_id, major_project_name;

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
    program_id,
    program_name,
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
  GROUP BY major_project_id, major_project_name, program_id, program_name
),
subproject_level AS (
  SELECT
    'subproject'::text AS snapshot_level,
    subproject_id AS entity_id,
    major_project_id AS parent_id,
    subproject_name AS entity_name,
    program_id,
    program_name,
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
    program_id,
    program_name,
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
    program_id,
    program_name,
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
-- 7) 统计分析（可选）
-- ============================================================
ANALYZE public.dim_completion_status;
ANALYZE public.dim_node_type;
ANALYZE public.dim_risk_level;
ANALYZE public.pm_dim_delay_reason;
ANALYZE public.pm_dim_major_project;
ANALYZE public.pm_dim_subproject;
ANALYZE public.pm_map_node_subject;
ANALYZE public.biz_dwd_project_node;
ANALYZE public.biz_dwd_project_node_enriched;
ANALYZE public.biz_dws_period_node_summary;
ANALYZE public.biz_dws_period_node_type_summary;
ANALYZE public.biz_dws_period_risk_summary;
ANALYZE public.biz_dws_week_subproject_summary;
ANALYZE public.biz_ads_project_kpi_overview;
ANALYZE public.biz_ads_project_milestone_kpi;
ANALYZE public.biz_ads_project_non_general_kpi;
ANALYZE public.biz_ads_project_incomplete_risk;
ANALYZE public.biz_ads_delay_reason_trend;
ANALYZE public.biz_ads_major_project_overview;
ANALYZE public.biz_ads_major_project_tree_snapshot;

-- ============================================================
-- 构建完成摘要
-- ============================================================
-- 以下表已创建（按依赖顺序）：
--
-- === SEED 种子表（4 张）===
--   pm_dim_delay_reason_seed         — 延期原因字典（8 条）
--   pm_dim_major_project_seed        — 重大项目主数据（4 条）
--   pm_dim_subproject_seed           — 子项目主数据（7 条）
--   pm_map_node_subject_seed         — 节点-主题映射（40 条）
--
-- === DIM 维度表（7 张）===
--   dim_completion_status            — 完成状态（7 种）
--   dim_node_type                    — 节点类型（4 种）
--   dim_risk_level                   — 风险等级（3 种）
--   pm_dim_delay_reason              — 延期原因（from seed）
--   pm_dim_major_project             — 重大项目（from seed）
--   pm_dim_subproject                — 子项目（from seed）
--   pm_map_node_subject              — 节点-主题映射（from seed）
--
-- === DWD 明细层（2 张）===
--   biz_dwd_project_node             — 节点明细（from ODS）
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
-- 合计：4 + 7 + 2 + 4 + 7 = 24 张表
-- ============================================================
