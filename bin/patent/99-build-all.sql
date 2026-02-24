-- ============================================================
-- 专利数仓模型一键构建（无需 dbt）
-- 适用场景：Excel/CSV 导入 ODS 后，手工/调度执行本 SQL
-- 目标库：PostgreSQL（public schema）
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- 可选：指定统计年份（psql 参数 report_year）
-- 用法：
--   psql -v report_year=2024 -f 99-build-all.sql
-- 若未指定，则默认取当前年份
-- ------------------------------------------------------------
\if :{?report_year}
SELECT set_config('dts.report_year', :'report_year', false);
\endif
\if :{?report_years}
SELECT set_config('dts.report_years', :'report_years', false);
\endif
\if :{?ods_table}
SELECT set_config('dts.ods_table', :'ods_table', false);
\endif

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

-- ------------------------------------------------------------
-- 1) ODS 兜底字段（若 Excel 导入后缺列则补齐）
--    支持参数 ods_table（不需要手工建 view）
-- ------------------------------------------------------------
DO $$
DECLARE
  ods_table text := current_setting('dts.ods_table', true);
BEGIN
  IF ods_table IS NULL OR ods_table = '' THEN
    ods_table := 'ods_patent_info';
  END IF;
  EXECUTE format('ALTER TABLE public.%I ADD COLUMN IF NOT EXISTS accept_date varchar(500);', ods_table);
  EXECUTE format('ALTER TABLE public.%I ADD COLUMN IF NOT EXISTS dept_code varchar(500);', ods_table);
END $$;

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
-- 2.1) ODS 来源表选择
-- 逻辑：
--  - 若传入 ods_table 参数，则使用该表
--  - 否则若存在 ods_patent_info_std（中文映射视图）则优先使用
--  - 否则回退 ods_patent_info
-- ------------------------------------------------------------
DO $$
BEGIN
  IF current_setting('dts.ods_table', true) IS NULL OR current_setting('dts.ods_table', true) = '' THEN
    IF to_regclass('public.ods_patent_info_std') IS NOT NULL THEN
      PERFORM set_config('dts.ods_table', 'ods_patent_info_std', false);
    ELSE
      PERFORM set_config('dts.ods_table', 'ods_patent_info', false);
    END IF;
  END IF;
END $$;

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
DO $$
DECLARE
  ods_table text := current_setting('dts.ods_table', true);
  cols text[];
  patent_no_expr text;
  patent_title_expr text;
  patent_type_expr text;
  state_expr text;
  app_date_expr text;
  accept_date_expr text;
  grant_date_expr text;
  first_pub_expr text;
  assignee_expr text;
  agent_expr text;
  dept_name_expr text;
  dept_code_expr text;
  inventor_expr text;
BEGIN
  IF ods_table IS NULL OR ods_table = '' THEN
    ods_table := 'ods_patent_info';
  END IF;
  SELECT array_agg(lower(column_name))
    INTO cols
    FROM information_schema.columns
   WHERE table_schema = 'public'
     AND lower(table_name) = lower(ods_table);

  -- Helper: return column reference or NULL::text
  patent_no_expr := CASE WHEN cols @> ARRAY['patent_no'] THEN 'o.patent_no' ELSE 'NULL::text' END;
  patent_title_expr := CASE WHEN cols @> ARRAY['patent_title_cn'] THEN 'o.patent_title_cn' ELSE 'NULL::text' END;
  patent_type_expr := CASE WHEN cols @> ARRAY['patent_type'] THEN 'o.patent_type' ELSE 'NULL::text' END;
  state_expr := CASE WHEN cols @> ARRAY['state'] THEN 'o.state' ELSE 'NULL::text' END;
  app_date_expr := CASE WHEN cols @> ARRAY['application_date'] THEN 'o.application_date' ELSE 'NULL::text' END;
  accept_date_expr := CASE WHEN cols @> ARRAY['accept_date'] THEN 'o.accept_date' ELSE 'NULL::text' END;
  grant_date_expr := CASE WHEN cols @> ARRAY['grant_date'] THEN 'o.grant_date' ELSE 'NULL::text' END;
  first_pub_expr := CASE WHEN cols @> ARRAY['first_publication_date'] THEN 'o.first_publication_date' ELSE 'NULL::text' END;
  assignee_expr := CASE WHEN cols @> ARRAY['assignee_name'] THEN 'o.assignee_name' ELSE 'NULL::text' END;
  agent_expr := CASE WHEN cols @> ARRAY['agent_org_name'] THEN 'o.agent_org_name' ELSE 'NULL::text' END;
  dept_name_expr := CASE WHEN cols @> ARRAY['dept_name'] THEN 'o.dept_name' ELSE 'NULL::text' END;
  dept_code_expr := CASE WHEN cols @> ARRAY['dept_code'] THEN 'o.dept_code' ELSE 'NULL::text' END;
  inventor_expr := CASE WHEN cols @> ARRAY['inventor_names'] THEN 'o.inventor_names' ELSE 'NULL::text' END;

  EXECUTE format($fmt$
CREATE TABLE public.dwd_patent AS
SELECT
  COALESCE(
    NULLIF(btrim(%s), ''),
    md5(
      COALESCE(btrim(%s), '') || '|' ||
      COALESCE(parse_date_safe(COALESCE(NULLIF(btrim(%s), ''), NULLIF(btrim(%s), '')))::text, '') || '|' ||
      COALESCE(btrim(%s), '')
    )
  ) AS patent_id,

  NULLIF(btrim(%s), '')               AS patent_no,
  NULLIF(btrim(%s), '')               AS patent_title_cn,
  NULLIF(btrim(%s), '')               AS patent_type,

  NULLIF(btrim(%s), '')                   AS patent_status_raw,
  COALESCE(s.status_std, 'other')              AS patent_status_std,
  COALESCE(s.is_accepted, false)               AS is_accepted,
  COALESCE(s.is_granted, false)                AS is_granted,

  parse_date_safe(
    COALESCE(NULLIF(btrim(%s), ''), NULLIF(btrim(%s), ''))
  ) AS application_date,
  parse_date_safe(NULLIF(btrim(%s), '')) AS accept_date,
  parse_date_safe(%s)               AS grant_date,
  parse_date_safe(%s)   AS first_publication_date,

  NULLIF(btrim(%s), '')          AS assignee_name,
  NULLIF(btrim(%s), '')         AS agent_org_name,
  NULLIF(btrim(%s), '')              AS dept_name,
  NULLIF(btrim(%s), '')              AS dept_code,
  NULLIF(btrim(%s), '')         AS inventor_names,

  EXTRACT(YEAR FROM parse_date_safe(
    COALESCE(NULLIF(btrim(%s), ''), NULLIF(btrim(%s), ''))
  ))::int AS application_year,
  to_char(
    parse_date_safe(COALESCE(NULLIF(btrim(%s), ''), NULLIF(btrim(%s), ''))),
    'YYYY-MM'
  ) AS application_month,
  EXTRACT(YEAR FROM parse_date_safe(%s))::int AS grant_year,
  to_char(parse_date_safe(%s), 'YYYY-MM')     AS grant_month,

  CASE
    WHEN parse_date_safe(COALESCE(NULLIF(btrim(%s), ''), NULLIF(btrim(%s), ''))) IS NOT NULL
     AND parse_date_safe(%s) IS NOT NULL
    THEN (parse_date_safe(%s) - parse_date_safe(
      COALESCE(NULLIF(btrim(%s), ''), NULLIF(btrim(%s), ''))
    ))::int
  END AS days_to_grant,

  %L::text AS source_table,
  now()                   AS etl_time

FROM public.%I o
LEFT JOIN public.dim_patent_status s
  ON s.status_code = NULLIF(btrim(%s), '');
$fmt$,
    patent_no_expr,
    patent_title_expr,
    app_date_expr, accept_date_expr,
    assignee_expr,
    patent_no_expr,
    patent_title_expr,
    patent_type_expr,
    state_expr,
    app_date_expr, accept_date_expr,
    accept_date_expr,
    grant_date_expr,
    first_pub_expr,
    assignee_expr,
    agent_expr,
    dept_name_expr,
    dept_code_expr,
    inventor_expr,
    app_date_expr, accept_date_expr,
    app_date_expr, accept_date_expr,
    grant_date_expr,
    grant_date_expr,
    app_date_expr, accept_date_expr,
    grant_date_expr,
    grant_date_expr,
    app_date_expr, accept_date_expr,
    ods_table,
    ods_table,
    state_expr
  );
END $$;

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
\if :{?report_year}
-- ----------------------------
-- 单年增量刷新
-- ----------------------------
CREATE TABLE IF NOT EXISTS public.ads_patent_dashboard_kpi AS
WITH params AS (
  SELECT :report_year::int AS yr
)
SELECT
  k1.stat_year                                     AS stat_year,
  COALESCE(k1.apply_cnt, 0)                       AS this_year_apply_cnt,
  COALESCE(k0.apply_cnt, 0)                       AS last_year_apply_cnt,
  CASE
    WHEN COALESCE(k0.apply_cnt, 0) = 0 THEN 0
    ELSE ROUND((COALESCE(k1.apply_cnt, 0) - k0.apply_cnt)::numeric / k0.apply_cnt::numeric, 4)
  END                                              AS yoy_growth_rate,
  COALESCE(k1.accepted_cnt, 0)                    AS this_year_accepted_cnt,
  COALESCE(g1.granted_cnt, 0)                     AS this_year_granted_cnt,
  COALESCE(k1.grant_rate_app, 0)                  AS this_year_grant_rate
FROM public.dws_patent_year_kpi k1
LEFT JOIN public.dws_patent_year_kpi k0 ON k0.stat_year = k1.stat_year - 1
LEFT JOIN public.dws_patent_year_grant g1 ON g1.stat_year = k1.stat_year
WHERE k1.stat_year = (SELECT yr FROM params)
LIMIT 0;

DELETE FROM public.ads_patent_dashboard_kpi WHERE stat_year = :report_year::int;
INSERT INTO public.ads_patent_dashboard_kpi
SELECT
  k1.stat_year                                     AS stat_year,
  COALESCE(k1.apply_cnt, 0)                       AS this_year_apply_cnt,
  COALESCE(k0.apply_cnt, 0)                       AS last_year_apply_cnt,
  CASE
    WHEN COALESCE(k0.apply_cnt, 0) = 0 THEN 0
    ELSE ROUND((COALESCE(k1.apply_cnt, 0) - k0.apply_cnt)::numeric / k0.apply_cnt::numeric, 4)
  END                                              AS yoy_growth_rate,
  COALESCE(k1.accepted_cnt, 0)                    AS this_year_accepted_cnt,
  COALESCE(g1.granted_cnt, 0)                     AS this_year_granted_cnt,
  COALESCE(k1.grant_rate_app, 0)                  AS this_year_grant_rate
FROM public.dws_patent_year_kpi k1
LEFT JOIN public.dws_patent_year_kpi k0 ON k0.stat_year = k1.stat_year - 1
LEFT JOIN public.dws_patent_year_grant g1 ON g1.stat_year = k1.stat_year
WHERE k1.stat_year = :report_year::int;

CREATE TABLE IF NOT EXISTS public.ads_patent_type_share AS
WITH params AS (
  SELECT :report_year::int AS yr
)
SELECT
  stat_year,
  patent_type,
  apply_cnt,
  CASE
    WHEN SUM(apply_cnt) OVER (PARTITION BY stat_year) = 0 THEN 0
    ELSE ROUND(apply_cnt::numeric / SUM(apply_cnt) OVER (PARTITION BY stat_year)::numeric, 4)
  END AS share
FROM public.dws_patent_year_type
WHERE stat_year = (SELECT yr FROM params)
LIMIT 0;

DELETE FROM public.ads_patent_type_share WHERE stat_year = :report_year::int;
INSERT INTO public.ads_patent_type_share
SELECT
  stat_year,
  patent_type,
  apply_cnt,
  CASE
    WHEN SUM(apply_cnt) OVER (PARTITION BY stat_year) = 0 THEN 0
    ELSE ROUND(apply_cnt::numeric / SUM(apply_cnt) OVER (PARTITION BY stat_year)::numeric, 4)
  END AS share
FROM public.dws_patent_year_type
WHERE stat_year = :report_year::int;

CREATE TABLE IF NOT EXISTS public.ads_patent_month_trend AS
WITH params AS (
  SELECT :report_year::int AS yr
)
SELECT
  stat_year,
  stat_month,
  accepted_cnt,
  granted_cnt
FROM public.dws_patent_month_trend
WHERE stat_year = (SELECT yr FROM params)
LIMIT 0;

DELETE FROM public.ads_patent_month_trend WHERE stat_year = :report_year::int;
INSERT INTO public.ads_patent_month_trend
SELECT
  stat_year,
  stat_month,
  accepted_cnt,
  granted_cnt
FROM public.dws_patent_month_trend
WHERE stat_year = :report_year::int;

CREATE TABLE IF NOT EXISTS public.ads_patent_dept_rank AS
WITH params AS (
  SELECT :report_year::int AS yr
),
ranked AS (
  SELECT
    stat_year,
    dept_name,
    dept_code,
    apply_cnt,
    ROW_NUMBER() OVER (PARTITION BY stat_year ORDER BY apply_cnt DESC, dept_name) AS rank_no
  FROM public.dws_patent_year_dept
  WHERE stat_year = (SELECT yr FROM params)
)
SELECT *
FROM ranked
WHERE rank_no <= 20
ORDER BY stat_year, rank_no
LIMIT 0;

DELETE FROM public.ads_patent_dept_rank WHERE stat_year = :report_year::int;
INSERT INTO public.ads_patent_dept_rank
WITH ranked AS (
  SELECT
    stat_year,
    dept_name,
    dept_code,
    apply_cnt,
    ROW_NUMBER() OVER (PARTITION BY stat_year ORDER BY apply_cnt DESC, dept_name) AS rank_no
  FROM public.dws_patent_year_dept
  WHERE stat_year = :report_year::int
)
SELECT *
FROM ranked
WHERE rank_no <= 20
ORDER BY stat_year, rank_no;

CREATE TABLE IF NOT EXISTS public.ads_patent_recent_grant AS
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
LIMIT 0;

TRUNCATE public.ads_patent_recent_grant;
INSERT INTO public.ads_patent_recent_grant
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
  application_year,
  grant_year,
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
ORDER BY COALESCE(application_date, grant_date) DESC NULLS LAST;

-- Create per-year table for the specified year
DO $$
DECLARE
  yr int := current_setting('dts.report_year', true)::int;
BEGIN
  IF yr IS NOT NULL THEN
    EXECUTE format('DROP TABLE IF EXISTS public.ads_patent_detail_year_%s', yr);
    EXECUTE format(
      'CREATE TABLE public.ads_patent_detail_year_%s AS
       SELECT application_date, grant_date, patent_no, patent_title_cn, patent_type,
              patent_status_std, patent_status_raw, dept_name, dept_code, assignee_name
       FROM public.ads_patent_detail_year
       WHERE application_year = %s OR grant_year = %s
       ORDER BY COALESCE(application_date, grant_date) DESC NULLS LAST',
      yr, yr, yr
    );
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.ads_patent_overdue_list AS
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
LIMIT 0;

TRUNCATE public.ads_patent_overdue_list;
INSERT INTO public.ads_patent_overdue_list
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
\else
-- ----------------------------
-- 多年份缓存（默认最近 N 年）
-- ----------------------------
DROP TABLE IF EXISTS public.ads_patent_dashboard_kpi;
CREATE TABLE public.ads_patent_dashboard_kpi AS
WITH scope AS (
  SELECT
    EXTRACT(YEAR FROM current_date)::int AS cur_year,
    COALESCE(NULLIF(current_setting('dts.report_years', true), '')::int, 5) AS years
)
SELECT
  k1.stat_year                                     AS stat_year,
  COALESCE(k1.apply_cnt, 0)                       AS this_year_apply_cnt,
  COALESCE(k0.apply_cnt, 0)                       AS last_year_apply_cnt,
  CASE
    WHEN COALESCE(k0.apply_cnt, 0) = 0 THEN 0
    ELSE ROUND((COALESCE(k1.apply_cnt, 0) - k0.apply_cnt)::numeric / k0.apply_cnt::numeric, 4)
  END                                              AS yoy_growth_rate,
  COALESCE(k1.accepted_cnt, 0)                    AS this_year_accepted_cnt,
  COALESCE(g1.granted_cnt, 0)                     AS this_year_granted_cnt,
  COALESCE(k1.grant_rate_app, 0)                  AS this_year_grant_rate
FROM public.dws_patent_year_kpi k1
LEFT JOIN public.dws_patent_year_kpi k0 ON k0.stat_year = k1.stat_year - 1
LEFT JOIN public.dws_patent_year_grant g1 ON g1.stat_year = k1.stat_year
WHERE k1.stat_year BETWEEN (SELECT cur_year - (years - 1) FROM scope) AND (SELECT cur_year FROM scope);

DROP TABLE IF EXISTS public.ads_patent_type_share;
CREATE TABLE public.ads_patent_type_share AS
WITH scope AS (
  SELECT
    EXTRACT(YEAR FROM current_date)::int AS cur_year,
    COALESCE(NULLIF(current_setting('dts.report_years', true), '')::int, 5) AS years
)
SELECT
  stat_year,
  patent_type,
  apply_cnt,
  CASE
    WHEN SUM(apply_cnt) OVER (PARTITION BY stat_year) = 0 THEN 0
    ELSE ROUND(apply_cnt::numeric / SUM(apply_cnt) OVER (PARTITION BY stat_year)::numeric, 4)
  END AS share
FROM public.dws_patent_year_type
WHERE stat_year BETWEEN (SELECT cur_year - (years - 1) FROM scope) AND (SELECT cur_year FROM scope);

DROP TABLE IF EXISTS public.ads_patent_month_trend;
CREATE TABLE public.ads_patent_month_trend AS
WITH scope AS (
  SELECT
    EXTRACT(YEAR FROM current_date)::int AS cur_year,
    COALESCE(NULLIF(current_setting('dts.report_years', true), '')::int, 5) AS years
)
SELECT
  stat_year,
  stat_month,
  accepted_cnt,
  granted_cnt
FROM public.dws_patent_month_trend
WHERE stat_year BETWEEN (SELECT cur_year - (years - 1) FROM scope) AND (SELECT cur_year FROM scope)
ORDER BY stat_year, stat_month;

DROP TABLE IF EXISTS public.ads_patent_dept_rank;
CREATE TABLE public.ads_patent_dept_rank AS
WITH scope AS (
  SELECT
    EXTRACT(YEAR FROM current_date)::int AS cur_year,
    COALESCE(NULLIF(current_setting('dts.report_years', true), '')::int, 5) AS years
),
ranked AS (
  SELECT
    stat_year,
    dept_name,
    dept_code,
    apply_cnt,
    ROW_NUMBER() OVER (PARTITION BY stat_year ORDER BY apply_cnt DESC, dept_name) AS rank_no
  FROM public.dws_patent_year_dept
  WHERE stat_year BETWEEN (SELECT cur_year - (years - 1) FROM scope) AND (SELECT cur_year FROM scope)
)
SELECT *
FROM ranked
WHERE rank_no <= 20
ORDER BY stat_year, rank_no;

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
  application_year,
  grant_year,
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
ORDER BY COALESCE(application_date, grant_date) DESC NULLS LAST;

-- Create per-year independent tables: ads_patent_detail_year_YYYY
DO $$
DECLARE
  yr int;
BEGIN
  FOR yr IN
    SELECT DISTINCT y FROM (
      SELECT application_year AS y FROM public.ads_patent_detail_year WHERE application_year IS NOT NULL
      UNION
      SELECT grant_year AS y FROM public.ads_patent_detail_year WHERE grant_year IS NOT NULL
    ) t ORDER BY y
  LOOP
    EXECUTE format('DROP TABLE IF EXISTS public.ads_patent_detail_year_%s', yr);
    EXECUTE format(
      'CREATE TABLE public.ads_patent_detail_year_%s AS
       SELECT application_date, grant_date, patent_no, patent_title_cn, patent_type,
              patent_status_std, patent_status_raw, dept_name, dept_code, assignee_name
       FROM public.ads_patent_detail_year
       WHERE application_year = %s OR grant_year = %s
       ORDER BY COALESCE(application_date, grant_date) DESC NULLS LAST',
      yr, yr, yr
    );
  END LOOP;
END $$;

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
\endif

COMMIT;

-- ------------------------------------------------------------
-- 7) ADS 索引（便于前端检索与筛选）
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ads_patent_kpi_year ON public.ads_patent_dashboard_kpi(stat_year);
CREATE INDEX IF NOT EXISTS idx_ads_patent_type_share_year ON public.ads_patent_type_share(stat_year);
CREATE INDEX IF NOT EXISTS idx_ads_patent_month_trend_year ON public.ads_patent_month_trend(stat_year);
CREATE INDEX IF NOT EXISTS idx_ads_patent_month_trend_month ON public.ads_patent_month_trend(stat_month);
CREATE INDEX IF NOT EXISTS idx_ads_patent_dept_rank_year ON public.ads_patent_dept_rank(stat_year);
CREATE INDEX IF NOT EXISTS idx_ads_patent_dept_rank_rank ON public.ads_patent_dept_rank(rank_no);
CREATE INDEX IF NOT EXISTS idx_ads_patent_recent_grant_date ON public.ads_patent_recent_grant(grant_date);
CREATE INDEX IF NOT EXISTS idx_ads_patent_detail_app_year ON public.ads_patent_detail_year(application_year);
CREATE INDEX IF NOT EXISTS idx_ads_patent_detail_grant_year ON public.ads_patent_detail_year(grant_year);
CREATE INDEX IF NOT EXISTS idx_ads_patent_overdue_days ON public.ads_patent_overdue_list(overdue_days);

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
