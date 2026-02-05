-- ============================================================
-- 专利数仓模型一键构建（无需 dbt）
-- 适用场景：Excel/CSV 导入 ODS 后，手工/调度执行本 SQL
-- 目标库：PostgreSQL（public schema）
-- ============================================================

BEGIN;

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
BEGIN
  IF p_text IS NULL THEN
    RETURN NULL;
  END IF;

  v := btrim(p_text);
  IF v = '' THEN
    RETURN NULL;
  END IF;

  v := replace(replace(v, '.', '-'), '/', '-');
  v := split_part(v, ' ', 1);

  IF v ~ '^\d{4}-\d{1,2}-\d{1,2}$' THEN
    RETURN to_date(v, 'YYYY-MM-DD');
  END IF;

  IF v ~ '^\d{8}$' THEN
    RETURN to_date(v, 'YYYYMMDD');
  END IF;

  RETURN NULL;
END;
$$;

-- ------------------------------------------------------------
-- 1) ODS 兜底字段（若 Excel 导入后缺列则补齐）
-- ------------------------------------------------------------
ALTER TABLE public.ods_patent_info
  ADD COLUMN IF NOT EXISTS accept_date varchar(500),
  ADD COLUMN IF NOT EXISTS dept_code varchar(500);

-- ------------------------------------------------------------
-- 2) 如 Excel header 为中文且列名未标准化，可用视图映射
--    如果已是标准列名，可忽略本段或自行调整来源表
-- ------------------------------------------------------------
-- CREATE OR REPLACE VIEW public.ods_patent_info_std AS
-- SELECT
--   "序号"         AS seq_no,
--   "专利名称"     AS patent_title_cn,
--   "专利类型"     AS patent_type,
--   "专利号"       AS patent_no,
--   "受理日"       AS accept_date,
--   "首次公开日"   AS first_publication_date,
--   "授权日"       AS grant_date,
--   "专利权人"     AS assignee_name,
--   "发明人"       AS inventor_names,
--   "部门"         AS dept_name,
--   "代理公司 "    AS agent_org_name,
--   "状态"         AS state,
--   dept_code      AS dept_code
-- FROM public.ods_patent_info;
--
-- 若使用映射视图，请在后续 DWD 中将来源表替换为 ods_patent_info_std

-- ------------------------------------------------------------
-- 3) DIM：专利状态维度
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.dim_patent_status;
CREATE TABLE public.dim_patent_status AS
SELECT
  status_code,
  status_std,
  is_accepted,
  is_granted,
  remark
FROM (VALUES
  ('在审',   'accepted',  true,  false, '在审/审查中'),
  ('受理',   'accepted',  true,  false, '受理'),
  ('初审',   'accepted',  true,  false, '初审'),
  ('实审',   'accepted',  true,  false, '实审'),
  ('已公开', 'published', false, false, '已公开/公示'),
  ('公布',   'published', false, false, '公布'),
  ('已授权', 'granted',   false, true,  '已授权'),
  ('授权',   'granted',   false, true,  '授权'),
  ('失效',   'invalid',   false, false, '失效/终止'),
  ('无效',   'invalid',   false, false, '无效/宣告无效')
) AS t(status_code, status_std, is_accepted, is_granted, remark);

CREATE INDEX IF NOT EXISTS idx_dim_patent_status_code
  ON public.dim_patent_status(status_code);

-- ------------------------------------------------------------
-- 4) DWD：专利明细
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.dwd_patent;
CREATE TABLE public.dwd_patent AS
SELECT
  COALESCE(
    NULLIF(btrim(o.patent_no), ''),
    md5(
      COALESCE(btrim(o.patent_title_cn), '') || '|' ||
      COALESCE(parse_date_safe(COALESCE(NULLIF(btrim(o.application_date), ''), NULLIF(btrim(o.accept_date), '')))::text, '') || '|' ||
      COALESCE(btrim(o.assignee_name), '')
    )
  ) AS patent_id,

  NULLIF(btrim(o.patent_no), '')               AS patent_no,
  NULLIF(btrim(o.patent_title_cn), '')         AS patent_title_cn,
  NULLIF(btrim(o.patent_type), '')             AS patent_type,

  NULLIF(btrim(o.state), '')                   AS patent_status_raw,
  COALESCE(s.status_std, 'other')              AS patent_status_std,
  COALESCE(s.is_accepted, false)               AS is_accepted,
  COALESCE(s.is_granted, false)                AS is_granted,

  parse_date_safe(
    COALESCE(NULLIF(btrim(o.application_date), ''), NULLIF(btrim(o.accept_date), ''))
  ) AS application_date,
  parse_date_safe(NULLIF(btrim(o.accept_date), '')) AS accept_date,
  parse_date_safe(o.grant_date)               AS grant_date,
  parse_date_safe(o.first_publication_date)   AS first_publication_date,

  NULLIF(btrim(o.assignee_name), '')          AS assignee_name,
  NULLIF(btrim(o.agent_org_name), '')         AS agent_org_name,
  NULLIF(btrim(o.dept_name), '')              AS dept_name,
  NULLIF(btrim(o.dept_code), '')              AS dept_code,
  NULLIF(btrim(o.inventor_names), '')         AS inventor_names,

  EXTRACT(YEAR FROM parse_date_safe(
    COALESCE(NULLIF(btrim(o.application_date), ''), NULLIF(btrim(o.accept_date), ''))
  ))::int AS application_year,
  to_char(
    parse_date_safe(COALESCE(NULLIF(btrim(o.application_date), ''), NULLIF(btrim(o.accept_date), ''))),
    'YYYY-MM'
  ) AS application_month,
  EXTRACT(YEAR FROM parse_date_safe(o.grant_date))::int AS grant_year,
  to_char(parse_date_safe(o.grant_date), 'YYYY-MM')     AS grant_month,

  CASE
    WHEN parse_date_safe(COALESCE(NULLIF(btrim(o.application_date), ''), NULLIF(btrim(o.accept_date), ''))) IS NOT NULL
     AND parse_date_safe(o.grant_date) IS NOT NULL
    THEN (parse_date_safe(o.grant_date) - parse_date_safe(
      COALESCE(NULLIF(btrim(o.application_date), ''), NULLIF(btrim(o.accept_date), ''))
    ))::int
  END AS days_to_grant,

  'ods_patent_info'::text AS source_table,
  now()                   AS etl_time

FROM public.ods_patent_info o
LEFT JOIN public.dim_patent_status s
  ON s.status_code = NULLIF(btrim(o.state), '');

CREATE INDEX IF NOT EXISTS idx_dwd_patent_year ON public.dwd_patent(application_year);
CREATE INDEX IF NOT EXISTS idx_dwd_patent_grant_year ON public.dwd_patent(grant_year);
CREATE INDEX IF NOT EXISTS idx_dwd_patent_dept ON public.dwd_patent(dept_code);

-- ------------------------------------------------------------
-- 5) DWS：汇总
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.dws_patent_year_kpi;
CREATE TABLE public.dws_patent_year_kpi AS
SELECT
  application_year AS stat_year,
  COUNT(*) AS apply_cnt,
  SUM(CASE WHEN is_accepted THEN 1 ELSE 0 END) AS accepted_cnt,
  SUM(CASE WHEN is_granted THEN 1 ELSE 0 END) AS granted_cnt_app,
  CASE
    WHEN COUNT(*) = 0 THEN 0
    ELSE ROUND(SUM(CASE WHEN is_granted THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
  END AS grant_rate_app
FROM public.dwd_patent
WHERE application_year IS NOT NULL
GROUP BY application_year;

DROP TABLE IF EXISTS public.dws_patent_year_grant;
CREATE TABLE public.dws_patent_year_grant AS
SELECT
  grant_year AS stat_year,
  COUNT(*)   AS granted_cnt
FROM public.dwd_patent
WHERE grant_year IS NOT NULL
  AND is_granted = true
GROUP BY grant_year;

DROP TABLE IF EXISTS public.dws_patent_year_type;
CREATE TABLE public.dws_patent_year_type AS
SELECT
  application_year                              AS stat_year,
  COALESCE(NULLIF(patent_type, ''), '未知')     AS patent_type,
  COUNT(*)                                      AS apply_cnt
FROM public.dwd_patent
WHERE application_year IS NOT NULL
GROUP BY application_year, COALESCE(NULLIF(patent_type, ''), '未知');

DROP TABLE IF EXISTS public.dws_patent_month_trend;
CREATE TABLE public.dws_patent_month_trend AS
SELECT
  stat_year,
  stat_month,
  SUM(accepted_cnt) AS accepted_cnt,
  SUM(granted_cnt)  AS granted_cnt
FROM (
  SELECT
    application_year  AS stat_year,
    application_month AS stat_month,
    SUM(CASE WHEN is_accepted THEN 1 ELSE 0 END) AS accepted_cnt,
    0 AS granted_cnt
  FROM public.dwd_patent
  WHERE application_year IS NOT NULL
    AND application_month IS NOT NULL
  GROUP BY application_year, application_month

  UNION ALL

  SELECT
    grant_year  AS stat_year,
    grant_month AS stat_month,
    0 AS accepted_cnt,
    COUNT(*) AS granted_cnt
  FROM public.dwd_patent
  WHERE grant_year IS NOT NULL
    AND grant_month IS NOT NULL
  GROUP BY grant_year, grant_month
) sub
GROUP BY stat_year, stat_month;

DROP TABLE IF EXISTS public.dws_patent_year_dept;
CREATE TABLE public.dws_patent_year_dept AS
SELECT
  application_year                              AS stat_year,
  COALESCE(NULLIF(dept_name, ''), '未知')       AS dept_name,
  NULLIF(dept_code, '')                         AS dept_code,
  COUNT(*)                                      AS apply_cnt
FROM public.dwd_patent
WHERE application_year IS NOT NULL
GROUP BY application_year, COALESCE(NULLIF(dept_name, ''), '未知'), NULLIF(dept_code, '');

-- ------------------------------------------------------------
-- 6) ADS：应用层
-- ------------------------------------------------------------
DROP TABLE IF EXISTS public.ads_patent_dashboard_kpi;
CREATE TABLE public.ads_patent_dashboard_kpi AS
WITH this AS (
  SELECT EXTRACT(YEAR FROM current_date)::int AS yr
)
SELECT
  this.yr                                          AS stat_year,
  COALESCE(k1.apply_cnt, 0)                       AS this_year_apply_cnt,
  COALESCE(k0.apply_cnt, 0)                       AS last_year_apply_cnt,
  CASE
    WHEN COALESCE(k0.apply_cnt, 0) = 0 THEN 0
    ELSE ROUND((COALESCE(k1.apply_cnt, 0) - k0.apply_cnt)::numeric / k0.apply_cnt::numeric, 4)
  END                                              AS yoy_growth_rate,
  COALESCE(k1.accepted_cnt, 0)                    AS this_year_accepted_cnt,
  COALESCE(g1.granted_cnt, 0)                     AS this_year_granted_cnt,
  COALESCE(k1.grant_rate_app, 0)                  AS this_year_grant_rate
FROM this
LEFT JOIN public.dws_patent_year_kpi k1 ON k1.stat_year = this.yr
LEFT JOIN public.dws_patent_year_kpi k0 ON k0.stat_year = this.yr - 1
LEFT JOIN public.dws_patent_year_grant g1 ON g1.stat_year = this.yr;

DROP TABLE IF EXISTS public.ads_patent_type_share;
CREATE TABLE public.ads_patent_type_share AS
SELECT
  stat_year,
  patent_type,
  apply_cnt,
  CASE
    WHEN SUM(apply_cnt) OVER () = 0 THEN 0
    ELSE ROUND(apply_cnt::numeric / SUM(apply_cnt) OVER ()::numeric, 4)
  END AS share
FROM public.dws_patent_year_type
WHERE stat_year = EXTRACT(YEAR FROM current_date)::int;

DROP TABLE IF EXISTS public.ads_patent_month_trend;
CREATE TABLE public.ads_patent_month_trend AS
SELECT
  stat_year,
  stat_month,
  accepted_cnt,
  granted_cnt
FROM public.dws_patent_month_trend
WHERE stat_year = EXTRACT(YEAR FROM current_date)::int
ORDER BY stat_month;

DROP TABLE IF EXISTS public.ads_patent_dept_rank;
CREATE TABLE public.ads_patent_dept_rank AS
SELECT
  stat_year,
  dept_name,
  dept_code,
  apply_cnt,
  ROW_NUMBER() OVER (ORDER BY apply_cnt DESC, dept_name) AS rank_no
FROM public.dws_patent_year_dept
WHERE stat_year = EXTRACT(YEAR FROM current_date)::int
ORDER BY apply_cnt DESC
LIMIT 20;

DROP TABLE IF EXISTS public.ads_patent_recent_grant;
CREATE TABLE public.ads_patent_recent_grant AS
SELECT
  grant_date,
  patent_no,
  patent_title_cn,
  assignee_name,
  dept_name,
  dept_code,
  agent_org_name
FROM public.dwd_patent
WHERE grant_date >= (current_date - INTERVAL '30 day')::date
ORDER BY grant_date DESC NULLS LAST
LIMIT 200;

DROP TABLE IF EXISTS public.ads_patent_detail_year;
CREATE TABLE public.ads_patent_detail_year AS
SELECT
  application_date,
  grant_date,
  patent_no,
  patent_title_cn,
  patent_type,
  patent_status_std,
  patent_status_raw,
  dept_name,
  dept_code,
  assignee_name
FROM public.dwd_patent
WHERE application_year = EXTRACT(YEAR FROM current_date)::int
   OR grant_year = EXTRACT(YEAR FROM current_date)::int
ORDER BY COALESCE(application_date, grant_date) DESC NULLS LAST
LIMIT 5000;

DROP TABLE IF EXISTS public.ads_patent_overdue_list;
CREATE TABLE public.ads_patent_overdue_list AS
SELECT
  application_date,
  patent_no,
  patent_title_cn,
  dept_name,
  dept_code,
  patent_status_std,
  (current_date - application_date)::int AS overdue_days
FROM public.dwd_patent
WHERE application_date IS NOT NULL
  AND is_granted = false
  AND (current_date - application_date) > 365
ORDER BY overdue_days DESC
LIMIT 2000;

COMMIT;

ANALYZE public.dim_patent_status;
ANALYZE public.dwd_patent;
ANALYZE public.dws_patent_year_kpi;
ANALYZE public.dws_patent_year_grant;
ANALYZE public.dws_patent_year_type;
ANALYZE public.dws_patent_month_trend;
ANALYZE public.dws_patent_year_dept;
ANALYZE public.ads_patent_dashboard_kpi;
ANALYZE public.ads_patent_type_share;
ANALYZE public.ads_patent_month_trend;
ANALYZE public.ads_patent_dept_rank;
ANALYZE public.ads_patent_recent_grant;
ANALYZE public.ads_patent_detail_year;
ANALYZE public.ads_patent_overdue_list;
