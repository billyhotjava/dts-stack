-- DIM：延期原因维度（从 seed 表构建）
DROP TABLE IF EXISTS public.pm_dim_delay_reason CASCADE;
CREATE TABLE public.pm_dim_delay_reason AS
SELECT
  NULLIF(btrim(delay_reason_category), '') AS delay_reason_category,
  NULLIF(btrim(delay_reason_label), '') AS delay_reason_label,
  NULLIF(btrim(description), '') AS description
FROM public.pm_dim_delay_reason_seed;
