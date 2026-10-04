-- DIM：风险等级维度
DROP TABLE IF EXISTS public.dim_risk_level CASCADE;
CREATE TABLE public.dim_risk_level AS
SELECT code, label, severity_rank
FROM (VALUES
  ('高', '高风险', 3),
  ('中', '中风险', 2),
  ('低', '低风险', 1)
) AS t(code, label, severity_rank);
