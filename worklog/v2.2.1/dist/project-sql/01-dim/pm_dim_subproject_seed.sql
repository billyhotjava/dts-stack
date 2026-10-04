-- SEED：子项目种子表
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
