{{ config(materialized='table', tags=['project-management', 'dim', 'project-cockpit', 'dwd']) }}

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
) AS t(delay_reason_category, delay_reason_label, description)
