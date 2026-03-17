-- SEED：延期原因种子表
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
