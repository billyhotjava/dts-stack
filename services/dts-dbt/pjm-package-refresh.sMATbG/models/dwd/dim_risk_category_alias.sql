{{ config(materialized='table', tags=['project-management-v3', 'dim', 'dwd', 'alias']) }}

SELECT alias_raw, canonical_code
FROM (VALUES
  ('技术',     '技术'),
  ('技术风险', '技术'),
  ('进度',     '进度'),
  ('进度风险', '进度'),
  ('供应链',   '进度'),
  ('管理',     '进度'),
  ('资源',     '进度'),
  ('成本',     '成本'),
  ('成本风险', '成本'),
  ('设计',     '设计'),
  ('设计风险', '设计'),
  ('质量',     '质量'),
  ('质量风险', '质量')
) AS t(alias_raw, canonical_code)
