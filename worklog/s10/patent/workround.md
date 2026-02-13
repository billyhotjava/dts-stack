# Patent Workaround (Offline)

> 现场离线环境可直接复制执行。  
> 默认连接：`biadmin@127.0.0.1:5432/biadmin`

## 1) 常用执行方式

### 1.1 一键重建模型（按单年）

```bash
PG_PASSWORD='Devops123@' ODS_TABLE=ods_patent_info_202602 REPORT_YEAR=2025 \
  ./worklog/s10/patent/run-build-all.sh --force-docker
```

注意：如果 Card SQL 固定写 `stat_year = 2025`，这里必须也用 `REPORT_YEAR=2025`。

### 1.2 删除旧 ODS 表（你要求的命令）

```bash
PG_PASSWORD='Devops123@' docker run --rm --network host \
  -e PGPASSWORD='Devops123@' \
  postgres:17.6 \
  psql -h 127.0.0.1 -p 5432 -U biadmin -d biadmin \
  -v ON_ERROR_STOP=1 \
  -c "DROP TABLE IF EXISTS public.ods_patent_info CASCADE;"
```

### 1.3 检查表是否存在

```bash
PG_PASSWORD='Devops123@' docker run --rm --network host \
  -e PGPASSWORD='Devops123@' \
  postgres:17.6 \
  psql -h 127.0.0.1 -p 5432 -U biadmin -d biadmin \
  -c "SELECT to_regclass('public.ods_patent_info') AS ods_patent_info, to_regclass('public.ods_patent_info_202602') AS ods_patent_info_202602;"
```

## 2) 结构核对 SQL（DBeaver 可直接执行）

### 2.1 查看 ODS 字段清单

```sql
SELECT
  ordinal_position,
  column_name,
  data_type,
  character_maximum_length,
  is_nullable,
  column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'ods_patent_info'
ORDER BY ordinal_position;
```

### 2.2 对比两张 ODS 表结构（如果你用了 `_202602`）

```sql
SELECT
  table_name,
  ordinal_position,
  column_name,
  data_type,
  character_maximum_length,
  is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('ods_patent_info', 'ods_patent_info_202602')
ORDER BY table_name, ordinal_position;
```

### 2.3 查看行数

```sql
SELECT 'ods_patent_info' AS table_name, COUNT(*) AS cnt FROM public.ods_patent_info
UNION ALL
SELECT 'ods_patent_info_202602' AS table_name, COUNT(*) AS cnt FROM public.ods_patent_info_202602;
```

## 3) `stat_year` / `state_year` 核对 SQL

> 正确字段是 `stat_year`，不是 `state_year`。

```sql
SELECT
  table_name,
  column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name LIKE '%patent%'
  AND column_name IN ('stat_year', 'state_year')
ORDER BY table_name, column_name;
```

## 4) 常用检查 SQL

### 4.1 2025 年 KPI 是否已产出

```sql
SELECT * FROM public.ads_patent_dashboard_kpi WHERE stat_year = 2025;
```

### 4.2 2025 年类型占比

```sql
SELECT * FROM public.ads_patent_type_share WHERE stat_year = 2025 ORDER BY apply_cnt DESC;
```

### 4.3 2025 年月度趋势

```sql
SELECT * FROM public.ads_patent_month_trend WHERE stat_year = 2025 ORDER BY stat_month;
```

### 4.4 2025 年部门排行

```sql
SELECT * FROM public.ads_patent_dept_rank WHERE stat_year = 2025 ORDER BY rank_no;
```

### 4.5 `ads_patent_detail_year` 可用年份

```sql
SELECT DISTINCT application_year
FROM public.ads_patent_detail_year
WHERE application_year IS NOT NULL
ORDER BY application_year;
```

## 5) Card SQL（固定 2025 版）

### C01 申请总量

```sql
SELECT this_year_apply_cnt
FROM ads_patent_dashboard_kpi
WHERE stat_year = 2025;
```

### C02 受理数量

```sql
SELECT this_year_accepted_cnt
FROM ads_patent_dashboard_kpi
WHERE stat_year = 2025;
```

### C03 授权数量

```sql
SELECT this_year_granted_cnt
FROM ads_patent_dashboard_kpi
WHERE stat_year = 2025;
```

### C04 授权率（百分比）

```sql
SELECT ROUND(this_year_grant_rate * 100, 1)
FROM ads_patent_dashboard_kpi
WHERE stat_year = 2025;
```

### C05 同比增长率（百分比）

```sql
SELECT ROUND(yoy_growth_rate * 100, 1)
FROM ads_patent_dashboard_kpi
WHERE stat_year = 2025;
```

### C06 上年申请量

```sql
SELECT last_year_apply_cnt
FROM ads_patent_dashboard_kpi
WHERE stat_year = 2025;
```

### C07 类型占比

```sql
SELECT patent_type, apply_cnt
FROM ads_patent_type_share
WHERE stat_year = 2025
ORDER BY apply_cnt DESC;
```

### C08 月度趋势

```sql
SELECT
  stat_month AS "月份",
  accepted_cnt AS "受理",
  granted_cnt AS "授权"
FROM ads_patent_month_trend
WHERE stat_year = 2025
ORDER BY stat_month;
```

### C09 部门排行

```sql
SELECT
  dept_name AS "部门",
  apply_cnt AS "申请数"
FROM ads_patent_dept_rank
WHERE stat_year = 2025
ORDER BY rank_no
LIMIT 10;
```

### C10 部门下钻（类型分布）

```sql
SELECT
  patent_type AS "专利类型",
  COUNT(*) AS "数量"
FROM ads_patent_detail_year
WHERE dept_name = {{dept_name}}
  AND application_year = 2025
GROUP BY patent_type
ORDER BY COUNT(*) DESC;
```

### C11 类型下钻（部门分布）

```sql
SELECT
  dept_name AS "部门",
  COUNT(*) AS "数量"
FROM ads_patent_detail_year
WHERE patent_type = {{patent_type}}
  AND application_year = 2025
GROUP BY dept_name
ORDER BY COUNT(*) DESC
LIMIT 10;
```

### C12 近期授权（近 180 天）

```sql
SELECT
  TO_CHAR(grant_date, 'YYYY-MM-DD') AS "授权日期",
  patent_no AS "专利号",
  patent_title_cn AS "专利名称",
  dept_name AS "部门"
FROM ads_patent_recent_grant
WHERE grant_date >= current_date - INTERVAL '180 days'
ORDER BY grant_date DESC
LIMIT 200;
```

### C13 当年明细

```sql
SELECT
  TO_CHAR(application_date, 'YYYY-MM-DD') AS "申请日期",
  patent_no AS "专利号",
  patent_title_cn AS "专利名称",
  patent_type AS "类型",
  patent_status_std AS "状态"
FROM ads_patent_detail_year
WHERE application_year = 2025
ORDER BY application_date DESC
LIMIT 200;
```

### C14 超期预警

```sql
SELECT
  TO_CHAR(application_date, 'YYYY-MM-DD') AS "申请日期",
  patent_no AS "专利号",
  patent_title_cn AS "专利名称",
  overdue_days || '天' AS "超期天数"
FROM ads_patent_overdue_list
ORDER BY overdue_days DESC
LIMIT 200;
```

## 6) DBeaver 调试模板（替换参数）

```sql
SELECT
  patent_type AS "专利类型",
  COUNT(*)    AS "数量"
FROM ads_patent_detail_year
WHERE dept_name = '研发一部'
  AND application_year = 2025
GROUP BY patent_type
ORDER BY COUNT(*) DESC;
```

---
  SELECT
    patent_type AS "专利类型",
    COUNT(*)    AS "数量"
  FROM ads_patent_detail_year
  WHERE dept_name = '制造事业部'
    AND stat_year = 2025
  GROUP BY patent_type
  ORDER BY COUNT(*) DESC;