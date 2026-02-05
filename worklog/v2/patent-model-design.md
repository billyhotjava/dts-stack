# 专利数据仓库模型设计方案

## 数据源

ODS 层表 `public.ods_patent_info`，字段全部为 `varchar(500)`：

| 字段 | 含义 |
|---|---|
| id | 自增主键 |
| seq_no | 序号 |
| patent_title_cn | 专利名称 |
| patent_type | 专利类型 |
| patent_no | 专利号 |
| application_date | 申请日（字符串） |
| grant_date | 授权日（字符串） |
| first_publication_date | 首次公开日（字符串） |
| assignee_name | 申请人 |
| inventor_names | 发明人 |
| dept_name | 部门 |
| agent_org_name | 代理机构 |
| state | 专利状态 |

---

## 模型分层与依赖关系

```
ods_patent_info (已有，数据入湖完成)
       |
       v
  dim_patent_status (维度表，种子数据)
       |
       v
  dwd_patent (明细宽表，清洗 + 标准化)
       |
       +---> dws_patent_year_kpi (年度 KPI)
       +---> dws_patent_year_type (年度-类型)
       +---> dws_patent_month_trend (月度趋势)
       +---> dws_patent_year_dept (年度-部门)
              |
              +---> ads_patent_dashboard_kpi (仪表盘核心指标)
              +---> ads_patent_type_share (类型占比)
              +---> ads_patent_month_trend (月度趋势-当年)
              +---> ads_patent_dept_rank (部门排名 TOP20)
              +---> ads_patent_recent_grant (近期授权列表)
              +---> ads_patent_detail_year (当年明细)
              +---> ads_patent_overdue_list (超期预警)
```

---

## 前置准备（手动执行一次，不放模型）

在 PG 中手动执行，创建日期解析函数：

```sql
CREATE OR REPLACE FUNCTION parse_date_safe(p_text text)
RETURNS date LANGUAGE plpgsql AS $$
DECLARE v text;
BEGIN
  IF p_text IS NULL THEN RETURN NULL; END IF;
  v := btrim(p_text);
  IF v = '' THEN RETURN NULL; END IF;
  v := replace(replace(v, '.', '-'), '/', '-');
  v := split_part(v, ' ', 1);
  IF v ~ '^\d{4}-\d{1,2}-\d{1,2}$' THEN RETURN to_date(v, 'YYYY-MM-DD'); END IF;
  IF v ~ '^\d{8}$' THEN RETURN to_date(v, 'YYYYMMDD'); END IF;
  RETURN NULL;
END; $$;
```

---

## 模型清单（每个模型 = 平台上一个 SQL 模型）

### 1. dim_patent_status

- **层级**: DIM
- **物化**: table
- **说明**: 专利状态维度映射表

```sql
SELECT status_code, status_std, is_accepted, is_granted, remark
FROM (VALUES
  ('在审',   'accepted',  true,  false, '在审/审查中'),
  ('受理',   'accepted',  true,  false, '受理'),
  ('已公开', 'published', false, false, '公开'),
  ('已授权', 'granted',   false, true,  '授权'),
  ('失效',   'invalid',   false, false, '失效/终止'),
  ('无效',   'invalid',   false, false, '无效')
) AS t(status_code, status_std, is_accepted, is_granted, remark)
```

---

### 2. dwd_patent

- **层级**: DWD
- **物化**: table
- **说明**: 专利明细宽表，清洗日期、标准化状态、补充衍生字段

```sql
SELECT
  COALESCE(
    NULLIF(btrim(o.patent_no), ''),
    md5(COALESCE(btrim(o.patent_title_cn), '') || '|'
     || COALESCE(parse_date_safe(o.application_date)::text, '') || '|'
     || COALESCE(btrim(o.assignee_name), ''))
  ) AS patent_id,

  NULLIF(btrim(o.patent_no), '')               AS patent_no,
  NULLIF(btrim(o.patent_title_cn), '')          AS patent_title_cn,
  NULLIF(btrim(o.patent_type), '')              AS patent_type,

  NULLIF(btrim(o.state), '')                    AS patent_status_raw,
  COALESCE(s.status_std, 'other')               AS patent_status_std,
  COALESCE(s.is_accepted, false)                AS is_accepted,
  COALESCE(s.is_granted, false)                 AS is_granted,

  parse_date_safe(o.application_date)            AS application_date,
  parse_date_safe(o.grant_date)                  AS grant_date,
  parse_date_safe(o.first_publication_date)       AS first_publication_date,

  NULLIF(btrim(o.assignee_name), '')             AS assignee_name,
  NULLIF(btrim(o.agent_org_name), '')            AS agent_org_name,
  NULLIF(btrim(o.dept_name), '')                 AS dept_name,
  NULLIF(btrim(o.inventor_names), '')            AS inventor_names,

  EXTRACT(YEAR FROM parse_date_safe(o.application_date))::int   AS application_year,
  to_char(parse_date_safe(o.application_date), 'YYYY-MM')       AS application_month,
  EXTRACT(YEAR FROM parse_date_safe(o.grant_date))::int          AS grant_year,
  to_char(parse_date_safe(o.grant_date), 'YYYY-MM')              AS grant_month,

  CASE
    WHEN parse_date_safe(o.application_date) IS NOT NULL
     AND parse_date_safe(o.grant_date) IS NOT NULL
    THEN (parse_date_safe(o.grant_date) - parse_date_safe(o.application_date))::int
  END AS days_to_grant,

  'ods_patent_info'::text AS source_table,
  now()                   AS etl_time

FROM {{ source('public', 'ods_patent_info') }} o
LEFT JOIN {{ ref('dim_patent_status') }} s
  ON s.status_code = NULLIF(btrim(o.state), '')
```

> **注意**: ODS 表中状态字段是 `state`，不是 `patent_status`。

---

### 3. dws_patent_year_kpi

- **层级**: DWS
- **物化**: table

```sql
SELECT
  application_year                                           AS stat_year,
  COUNT(*)                                                   AS apply_cnt,
  SUM(CASE WHEN is_accepted THEN 1 ELSE 0 END)              AS accepted_cnt,
  SUM(CASE WHEN is_granted THEN 1 ELSE 0 END)               AS granted_cnt_app,
  CASE WHEN COUNT(*) = 0 THEN 0
       ELSE ROUND(SUM(CASE WHEN is_granted THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric, 4)
  END                                                        AS grant_rate_app
FROM {{ ref('dwd_patent') }}
WHERE application_year IS NOT NULL
GROUP BY application_year
```

---

### 4. dws_patent_year_type

- **层级**: DWS
- **物化**: table

```sql
SELECT
  application_year                                    AS stat_year,
  COALESCE(NULLIF(patent_type, ''), '未知')           AS patent_type,
  COUNT(*)                                            AS apply_cnt
FROM {{ ref('dwd_patent') }}
WHERE application_year IS NOT NULL
GROUP BY application_year, COALESCE(NULLIF(patent_type, ''), '未知')
```

---

### 5. dws_patent_month_trend

- **层级**: DWS
- **物化**: table

```sql
SELECT
  stat_year, stat_month,
  SUM(accepted_cnt) AS accepted_cnt,
  SUM(granted_cnt)  AS granted_cnt
FROM (
  SELECT
    application_year  AS stat_year,
    application_month AS stat_month,
    SUM(CASE WHEN is_accepted THEN 1 ELSE 0 END) AS accepted_cnt,
    0 AS granted_cnt
  FROM {{ ref('dwd_patent') }}
  WHERE application_year IS NOT NULL AND application_month IS NOT NULL
  GROUP BY application_year, application_month

  UNION ALL

  SELECT
    grant_year  AS stat_year,
    grant_month AS stat_month,
    0 AS accepted_cnt,
    COUNT(*) AS granted_cnt
  FROM {{ ref('dwd_patent') }}
  WHERE grant_year IS NOT NULL AND grant_month IS NOT NULL
  GROUP BY grant_year, grant_month
) sub
GROUP BY stat_year, stat_month
```

---

### 6. dws_patent_year_dept

- **层级**: DWS
- **物化**: table

```sql
SELECT
  application_year                                    AS stat_year,
  COALESCE(NULLIF(dept_name, ''), '未知')             AS dept_name,
  COUNT(*)                                            AS apply_cnt
FROM {{ ref('dwd_patent') }}
WHERE application_year IS NOT NULL
GROUP BY application_year, COALESCE(NULLIF(dept_name, ''), '未知')
```

---

### 7. ads_patent_dashboard_kpi

- **层级**: ADS
- **物化**: table

```sql
WITH this AS (
  SELECT EXTRACT(YEAR FROM current_date)::int AS yr
)
SELECT
  this.yr                                          AS stat_year,
  COALESCE(k1.apply_cnt, 0)                       AS this_year_apply_cnt,
  COALESCE(k0.apply_cnt, 0)                       AS last_year_apply_cnt,
  CASE WHEN COALESCE(k0.apply_cnt, 0) = 0 THEN 0
       ELSE ROUND((COALESCE(k1.apply_cnt,0) - k0.apply_cnt)::numeric / k0.apply_cnt::numeric, 4)
  END                                              AS yoy_growth_rate,
  COALESCE(k1.accepted_cnt, 0)                    AS this_year_accepted_cnt,
  COALESCE(k1.granted_cnt_app, 0)                 AS this_year_granted_cnt,
  COALESCE(k1.grant_rate_app, 0)                  AS this_year_grant_rate
FROM this
LEFT JOIN {{ ref('dws_patent_year_kpi') }} k1 ON k1.stat_year = this.yr
LEFT JOIN {{ ref('dws_patent_year_kpi') }} k0 ON k0.stat_year = this.yr - 1
```

---

### 8. ads_patent_type_share

- **层级**: ADS
- **物化**: table

```sql
SELECT
  stat_year,
  patent_type,
  apply_cnt,
  CASE WHEN SUM(apply_cnt) OVER () = 0 THEN 0
       ELSE ROUND(apply_cnt::numeric / SUM(apply_cnt) OVER ()::numeric, 4)
  END AS share
FROM {{ ref('dws_patent_year_type') }}
WHERE stat_year = EXTRACT(YEAR FROM current_date)::int
```

---

### 9. ads_patent_month_trend

- **层级**: ADS
- **物化**: table

```sql
SELECT stat_year, stat_month, accepted_cnt, granted_cnt
FROM {{ ref('dws_patent_month_trend') }}
WHERE stat_year = EXTRACT(YEAR FROM current_date)::int
ORDER BY stat_month
```

---

### 10. ads_patent_dept_rank

- **层级**: ADS
- **物化**: table

```sql
SELECT
  stat_year,
  dept_name,
  apply_cnt,
  ROW_NUMBER() OVER (ORDER BY apply_cnt DESC, dept_name) AS rank_no
FROM {{ ref('dws_patent_year_dept') }}
WHERE stat_year = EXTRACT(YEAR FROM current_date)::int
ORDER BY apply_cnt DESC
LIMIT 20
```

---

### 11. ads_patent_recent_grant

- **层级**: ADS
- **物化**: table

```sql
SELECT
  grant_date,
  patent_no,
  patent_title_cn,
  assignee_name,
  dept_name,
  agent_org_name
FROM {{ ref('dwd_patent') }}
WHERE grant_date >= (current_date - INTERVAL '30 day')::date
ORDER BY grant_date DESC NULLS LAST
LIMIT 200
```

---

### 12. ads_patent_detail_year

- **层级**: ADS
- **物化**: table

```sql
SELECT
  application_date, grant_date, patent_no, patent_title_cn, patent_type,
  patent_status_std, patent_status_raw, dept_name, assignee_name
FROM {{ ref('dwd_patent') }}
WHERE application_year = EXTRACT(YEAR FROM current_date)::int
   OR grant_year = EXTRACT(YEAR FROM current_date)::int
ORDER BY COALESCE(application_date, grant_date) DESC NULLS LAST
LIMIT 5000
```

---

### 13. ads_patent_overdue_list

- **层级**: ADS
- **物化**: table

```sql
SELECT
  application_date,
  patent_no,
  patent_title_cn,
  dept_name,
  patent_status_std,
  (current_date - application_date)::int AS overdue_days
FROM {{ ref('dwd_patent') }}
WHERE application_date IS NOT NULL
  AND is_granted = false
  AND (current_date - application_date) > 365
ORDER BY overdue_days DESC
LIMIT 2000
```

---

## 在平台上操作步骤

1. **手动执行** `parse_date_safe` 函数 DDL（只需一次）
2. 在"逻辑建模"页面中，按上述清单逐个创建 SQL 模型：
   - 名称 = 模型名（如 `dim_patent_status`）
   - 层级 = 对应层（DIM / DWD / DWS / ADS）
   - 物化 = `table`
   - SQL = 上面的 SELECT 语句（不含 `{{ config(...) }}`，平台会自动生成）
3. 配置 dbt source：需要在 `ods_sources.yml` 中注册 `ods_patent_info`
4. 提交运行：平台会生成 dbt DAG → Airflow 调度 → dbt run 执行

## 注意事项

- 每个模型只写 **SELECT 语句**，不要写 CREATE TABLE / INSERT / TRUNCATE
- dbt 会根据 `materialized='table'` 自动建表（DROP + CREATE AS SELECT）
- `{{ ref('xxx') }}` 引用上游模型，dbt 自动处理依赖顺序
- `{{ source('public', 'ods_patent_info') }}` 引用 ODS 源表
- 你原始 SQL 中 `state` 字段映射到了 `patent_status`，已修正
