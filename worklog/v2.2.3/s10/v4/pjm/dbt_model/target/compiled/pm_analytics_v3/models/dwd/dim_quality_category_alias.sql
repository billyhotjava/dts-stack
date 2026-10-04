

SELECT alias_raw, canonical_code
FROM (VALUES
  ('设计',     '设计'),
  ('工艺',     '工艺'),
  ('管理',     '管理'),
  ('元器件',   '元器件'),
  ('操作',     '操作'),
  ('外协',     '外协'),
  ('外协外购', '外协'),
  ('软件',     '软件'),
  ('环境',     '环境'),
  ('其他',     '其他')
) AS t(alias_raw, canonical_code)