-- SEED：重大项目种子表
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
