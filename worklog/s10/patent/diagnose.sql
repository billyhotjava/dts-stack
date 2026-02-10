-- ============================================================
-- 专利数仓诊断：检查日期解析 + 各表行数
-- 用法：
--   PG_PASSWORD='xxx' docker run --rm --network host \
--     -e PGPASSWORD='xxx' \
--     -v $(pwd)/worklog/s10/patent/diagnose.sql:/tmp/d.sql:ro \
--     postgres:17.6 psql -h 127.0.0.1 -p 5432 -U biadmin -d biadmin -f /tmp/d.sql
-- ============================================================

\echo '============================================'
\echo '1) ODS 原始日期样本（前 20 条不同值）'
\echo '============================================'
SELECT DISTINCT application_date AS raw_date, 'application_date' AS src
FROM public.ods_patent_info WHERE application_date IS NOT NULL AND btrim(application_date) <> ''
UNION ALL
SELECT DISTINCT grant_date, 'grant_date'
FROM public.ods_patent_info WHERE grant_date IS NOT NULL AND btrim(grant_date) <> ''
LIMIT 20;

\echo ''
\echo '============================================'
\echo '2) parse_date_safe 解析测试'
\echo '============================================'
SELECT
  raw,
  parse_date_safe(raw) AS parsed,
  CASE WHEN parse_date_safe(raw) IS NULL THEN '!! FAILED !!' ELSE 'OK' END AS status
FROM (
  SELECT DISTINCT btrim(application_date) AS raw
  FROM public.ods_patent_info
  WHERE application_date IS NOT NULL AND btrim(application_date) <> ''
  UNION
  SELECT DISTINCT btrim(grant_date)
  FROM public.ods_patent_info
  WHERE grant_date IS NOT NULL AND btrim(grant_date) <> ''
) t
ORDER BY status DESC, raw
LIMIT 30;

\echo ''
\echo '============================================'
\echo '3) DWD 日期解析成功率'
\echo '============================================'
SELECT
  COUNT(*) AS total,
  COUNT(application_date) AS app_date_ok,
  COUNT(*) - COUNT(application_date) AS app_date_null,
  COUNT(grant_date) AS grant_date_ok,
  COUNT(*) - COUNT(grant_date) AS grant_date_null,
  COUNT(application_year) AS app_year_ok
FROM public.dwd_patent;

\echo ''
\echo '============================================'
\echo '4) 各表行数'
\echo '============================================'
SELECT t.tablename, COALESCE(s.n_live_tup, 0) AS row_count
FROM pg_tables t
LEFT JOIN pg_stat_user_tables s ON s.relname = t.tablename AND s.schemaname = t.schemaname
WHERE t.schemaname = 'public'
  AND (t.tablename LIKE 'ods_patent%'
    OR t.tablename LIKE 'dim_patent%'
    OR t.tablename LIKE 'dwd_patent%'
    OR t.tablename LIKE 'dws_patent%'
    OR t.tablename LIKE 'ads_patent%')
ORDER BY
  CASE
    WHEN t.tablename LIKE 'ods%' THEN 1
    WHEN t.tablename LIKE 'dim%' THEN 2
    WHEN t.tablename LIKE 'dwd%' THEN 3
    WHEN t.tablename LIKE 'dws%' THEN 4
    WHEN t.tablename LIKE 'ads%' THEN 5
  END,
  t.tablename;
