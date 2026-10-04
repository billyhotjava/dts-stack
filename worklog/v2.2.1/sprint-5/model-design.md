# 项目主体域数仓模型设计

> 版本：v1.0 | 日期：2026-03-11 | 方案：C（混合预计算 + 参数化查询）

---

## 1. 模型分层与依赖关系

```
ods_project_progress           (Excel 原始导入)
       |
       +---> dim_completion_status    (完成情况枚举)
       +---> dim_node_type            (节点类型枚举)
       +---> dim_risk_level           (风险等级枚举)
       |
       v
  dwd_project_node                    (清洗标准化 + 衍生字段)
       |
       +---> dws_period_node_summary         (按周期+项目聚合)
       +---> dws_period_node_type_summary    (按周期+节点类型聚合)
       +---> dws_period_risk_summary         (按周期+风险等级聚合)
              |
              +---> ads_project_kpi_overview       (综合 KPI：35 指标中的前 14 项)
              +---> ads_project_non_general_kpi    (除一般节点 KPI：4 项)
              +---> ads_project_incomplete_risk    (未完成节点风险分布：5 项)
              +---> ads_project_milestone_kpi      (里程碑与本周期节点：12 项)
```

---

## 2. ODS 层

### 2.1 ods_project_progress

- **来源**：Excel 文件导入（全量覆盖）
- **物化**：table
- **说明**：29 个字段全部为 varchar，保留原始值不做任何转换

```sql
-- 平台自动建表，字段对应 Excel 列名
-- 导入时需跳过第 2 行（字段说明行），或在 DWD 层过滤
```

| # | 字段名 | 中文 | 类型 |
|---|--------|------|------|
| 1 | project_no | 项目编号 | varchar |
| 2 | subsystem | 分系统/分任务 | varchar |
| 3 | node_task | 节点任务及目标 | varchar |
| 4 | plan_date | 节点计划时间 | varchar |
| 5 | plan_week | 节点计划周数 | varchar |
| 6 | node_type | 节点类型 | varchar |
| 7 | owner | 负责人 | varchar |
| 8 | dept | 责任科室 | varchar |
| 9 | dept_leader | 分管室领导 | varchar |
| 10 | completion_status | 完成情况 | varchar |
| 11 | collab_dept | 协同部门 | varchar |
| 12 | supervisor_dept | 责任监管部门 | varchar |
| 13 | delay_expected_date | 延期预计完成时间 | varchar |
| 14 | incomplete_reason | 未完成原因及当前进展 | varchar |
| 15 | risk_level | 风险等级 | varchar |
| 16 | risk_content | 主要风险内容及措施 | varchar |
| 17 | delay_impact | 延期影响分析 | varchar |
| 18 | actual_date | 实际完成时间 | varchar |
| 19 | actual_week | 实际完成周数 | varchar |
| 20 | institute_leader | 所领导 | varchar |
| 21 | source | 来源 | varchar |
| 22 | original_plan_date | 延期项目原计划时间 | varchar |
| 23 | delay_days_changed | 计划延误时间（已变更） | varchar |
| 24 | delay_days_unchanged | 计划延误时间（未变更） | varchar |
| 25 | delay_applied | 是否提交延期申请 | varchar |
| 26 | project_manager | 项目主管 | varchar |
| 27 | last_update_time | 最后更新时间 | varchar |
| 28 | filled_by | 填写人 | varchar |
| 29 | highlight | 亮点工作 | varchar |

---

## 3. DIM 层

### 3.1 dim_completion_status

- **物化**：table
- **说明**：完成情况枚举，附带分类标签用于指标快速筛选

```sql
SELECT code, label, is_completed, is_on_time, is_overdue_completed, is_incomplete, sort_order
FROM (VALUES
  ('正常待完成',         '正常待完成',     false, false, false, false, 1),
  ('按时完成',           '按时完成',       true,  true,  false, false, 2),
  ('超期已完成已变更',   '超期已完成已变更', true,  false, true,  false, 3),
  ('超期已完成未变更',   '超期已完成未变更', true,  false, true,  false, 4),
  ('不正常待变更',       '不正常待变更',   false, false, false, true,  5),
  ('超期未完成未变更',   '超期未完成未变更', false, false, false, true,  6),
  ('超期未完成已变更',   '超期未完成已变更', false, false, false, true,  7)
) AS t(code, label, is_completed, is_on_time, is_overdue_completed, is_incomplete, sort_order)
```

标签说明：
- `is_completed`：已完成（按时 + 超期已完成）
- `is_on_time`：按时完成
- `is_overdue_completed`：超期已完成（已变更 + 未变更）
- `is_incomplete`：未完成（不正常待变更 + 超期未完成未变更 + 超期未完成已变更）

### 3.2 dim_node_type

- **物化**：table

```sql
SELECT code, label, is_general, severity_rank
FROM (VALUES
  ('一般节点',   '一般节点',   true,  1),
  ('重要节点',   '重要节点',   false, 2),
  ('重大节点',   '重大节点',   false, 3),
  ('里程碑节点', '里程碑节点', false, 4)
) AS t(code, label, is_general, severity_rank)
```

### 3.3 dim_risk_level

- **物化**：table

```sql
SELECT code, label, severity_rank
FROM (VALUES
  ('高', '高风险', 3),
  ('中', '中风险', 2),
  ('低', '低风险', 1)
) AS t(code, label, severity_rank)
```

---

## 4. DWD 层

### 4.1 dwd_project_node

- **物化**：table
- **说明**：清洗日期、标准化枚举、添加时间维度标签与衍生字段

```sql
SELECT
  -- === 主键 ===
  md5(
    COALESCE(btrim(o.project_no), '') || '|' ||
    COALESCE(btrim(o.subsystem), '') || '|' ||
    COALESCE(btrim(o.node_task), '') || '|' ||
    COALESCE(btrim(o.plan_date), '')
  ) AS node_id,

  -- === 原始业务字段 ===
  NULLIF(btrim(o.project_no), '')          AS project_no,
  NULLIF(btrim(o.subsystem), '')           AS subsystem,
  NULLIF(btrim(o.node_task), '')           AS node_task,
  NULLIF(btrim(o.owner), '')               AS owner,
  NULLIF(btrim(o.dept), '')                AS dept,
  NULLIF(btrim(o.dept_leader), '')         AS dept_leader,
  NULLIF(btrim(o.collab_dept), '')         AS collab_dept,
  NULLIF(btrim(o.supervisor_dept), '')     AS supervisor_dept,
  NULLIF(btrim(o.incomplete_reason), '')   AS incomplete_reason,
  NULLIF(btrim(o.risk_content), '')        AS risk_content,
  NULLIF(btrim(o.delay_impact), '')        AS delay_impact,
  NULLIF(btrim(o.institute_leader), '')    AS institute_leader,
  NULLIF(btrim(o.project_manager), '')     AS project_manager,
  NULLIF(btrim(o.filled_by), '')           AS filled_by,
  NULLIF(btrim(o.highlight), '')           AS highlight,

  -- === 枚举标准化 ===
  NULLIF(btrim(o.completion_status), '')   AS completion_status,
  COALESCE(cs.is_completed, false)         AS is_completed,
  COALESCE(cs.is_on_time, false)           AS is_on_time,
  COALESCE(cs.is_overdue_completed, false) AS is_overdue_completed,
  COALESCE(cs.is_incomplete, false)        AS is_incomplete,

  NULLIF(btrim(o.node_type), '')           AS node_type,
  COALESCE(nt.is_general, false)           AS is_general_node,

  NULLIF(btrim(o.risk_level), '')          AS risk_level,

  NULLIF(btrim(o.source), '')              AS source,
  NULLIF(btrim(o.delay_applied), '')       AS delay_applied,

  -- === 日期解析 ===
  parse_date_safe(o.plan_date)             AS plan_date,
  parse_date_safe(o.actual_date)           AS actual_date,
  parse_date_safe(o.delay_expected_date)   AS delay_expected_date,
  parse_date_safe(o.original_plan_date)    AS original_plan_date,
  parse_date_safe(o.last_update_time)      AS last_update_time,

  -- === 周数 ===
  CASE WHEN o.plan_week ~ '^\d+$' THEN o.plan_week::int END   AS plan_week,
  CASE WHEN o.actual_week ~ '^\d+$' THEN o.actual_week::int END AS actual_week,

  -- === 时间维度标签（方案 C 核心） ===
  EXTRACT(YEAR FROM parse_date_safe(o.plan_date))::int                     AS plan_year,
  EXTRACT(QUARTER FROM parse_date_safe(o.plan_date))::int                  AS plan_quarter,
  to_char(parse_date_safe(o.plan_date), 'YYYY-MM')                         AS plan_month,
  EXTRACT(WEEK FROM parse_date_safe(o.plan_date))::int                     AS plan_week_of_year,
  to_char(parse_date_safe(o.plan_date), 'IYYY-"W"IW')                     AS plan_iso_week,

  EXTRACT(YEAR FROM parse_date_safe(o.actual_date))::int                   AS actual_year,
  to_char(parse_date_safe(o.actual_date), 'YYYY-MM')                       AS actual_month,

  -- === 衍生字段 ===
  CASE
    WHEN parse_date_safe(o.plan_date) IS NOT NULL
     AND parse_date_safe(o.actual_date) IS NOT NULL
    THEN (parse_date_safe(o.actual_date) - parse_date_safe(o.plan_date))::int
  END AS delay_days,

  CASE
    WHEN parse_date_safe(o.plan_date) IS NOT NULL
     AND parse_date_safe(o.plan_date) <= current_date
    THEN true
    ELSE false
  END AS is_due,

  'ods_project_progress'::text AS source_table,
  now() AS etl_time

FROM {{ source('public', 'ods_project_progress') }} o
LEFT JOIN {{ ref('dim_completion_status') }} cs
  ON cs.code = NULLIF(btrim(o.completion_status), '')
LEFT JOIN {{ ref('dim_node_type') }} nt
  ON nt.code = NULLIF(btrim(o.node_type), '')
WHERE btrim(COALESCE(o.project_no, '')) != ''
  AND btrim(COALESCE(o.plan_date, '')) != ''
```

**关键设计说明**：
- `node_id`：由项目编号+分系统+节点任务+计划时间哈希生成，作为逻辑主键
- `is_due`：计划时间 ≤ 当前日期，标记节点是否已到期
- 时间维度标签（`plan_year/quarter/month/week`）：支持方案 C 按固定周期预聚合
- `WHERE` 过滤掉空行和说明行

---

## 5. DWS 层

### 5.1 dws_period_node_summary

- **物化**：table
- **说明**：按（年、月）+ 项目聚合节点统计，覆盖"项目（含一般节点）"14 个指标的基础数据

```sql
SELECT
  d.plan_year,
  d.plan_month,
  d.project_no,

  -- 节点总数
  COUNT(*)                                                    AS total_cnt,
  -- 正常待完成
  SUM(CASE WHEN d.completion_status = '正常待完成' THEN 1 ELSE 0 END) AS pending_normal_cnt,
  -- 已到时间节点总数 = total - 正常待完成
  SUM(CASE WHEN d.completion_status != '正常待完成' OR d.is_due THEN 1 ELSE 0 END) AS due_cnt,
  -- 按时完成
  SUM(CASE WHEN d.is_on_time THEN 1 ELSE 0 END)              AS on_time_cnt,
  -- 超期已完成（含已变更+未变更）
  SUM(CASE WHEN d.is_overdue_completed THEN 1 ELSE 0 END)    AS overdue_completed_cnt,
  -- 已完成总数 = 按时 + 超期已完成
  SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END)            AS completed_cnt,
  -- 未完成总数
  SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END)           AS incomplete_cnt,
  -- 不正常待变更
  SUM(CASE WHEN d.completion_status = '不正常待变更' THEN 1 ELSE 0 END) AS abnormal_pending_cnt,
  -- 超期未完成未变更
  SUM(CASE WHEN d.completion_status = '超期未完成未变更' THEN 1 ELSE 0 END) AS overdue_incomplete_unchanged_cnt,
  -- 超期未完成已变更
  SUM(CASE WHEN d.completion_status = '超期未完成已变更' THEN 1 ELSE 0 END) AS overdue_incomplete_changed_cnt,
  -- 超期已完成未变更
  SUM(CASE WHEN d.completion_status = '超期已完成未变更' THEN 1 ELSE 0 END) AS overdue_completed_unchanged_cnt

FROM {{ ref('dwd_project_node') }} d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_month, d.project_no
```

### 5.2 dws_period_node_type_summary

- **物化**：table
- **说明**：按（年、月）+ 节点类型聚合，支持"除一般节点"和"里程碑"维度指标

```sql
SELECT
  d.plan_year,
  d.plan_month,
  d.project_no,
  d.node_type,
  d.is_general_node,

  COUNT(*)                                                    AS total_cnt,
  SUM(CASE WHEN d.is_on_time THEN 1 ELSE 0 END)              AS on_time_cnt,
  SUM(CASE WHEN d.is_overdue_completed THEN 1 ELSE 0 END)    AS overdue_completed_cnt,
  SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END)            AS completed_cnt,
  SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END)           AS incomplete_cnt,
  SUM(CASE WHEN d.completion_status = '正常待完成' THEN 1 ELSE 0 END) AS pending_normal_cnt,
  SUM(CASE WHEN d.completion_status = '不正常待变更' THEN 1 ELSE 0 END) AS abnormal_pending_cnt,
  SUM(CASE WHEN d.completion_status = '超期未完成未变更' THEN 1 ELSE 0 END) AS overdue_incomplete_unchanged_cnt,
  SUM(CASE WHEN d.completion_status = '超期未完成已变更' THEN 1 ELSE 0 END) AS overdue_incomplete_changed_cnt,
  SUM(CASE WHEN d.completion_status = '超期已完成未变更' THEN 1 ELSE 0 END) AS overdue_completed_unchanged_cnt,
  SUM(CASE WHEN d.completion_status = '超期已完成已变更' THEN 1 ELSE 0 END) AS overdue_completed_changed_cnt

FROM {{ ref('dwd_project_node') }} d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_month, d.project_no, d.node_type, d.is_general_node
```

### 5.3 dws_period_risk_summary

- **物化**：table
- **说明**：按（年、月）+ 风险等级聚合，支持风险分布指标

```sql
SELECT
  d.plan_year,
  d.plan_month,
  d.project_no,
  d.risk_level,
  d.node_type,

  COUNT(*)                                                    AS total_cnt,
  SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END)           AS incomplete_cnt,
  SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END)            AS completed_cnt

FROM {{ ref('dwd_project_node') }} d
WHERE d.plan_year IS NOT NULL
  AND d.risk_level IS NOT NULL
GROUP BY d.plan_year, d.plan_month, d.project_no, d.risk_level, d.node_type
```

---

## 6. ADS 层

### 6.1 ads_project_kpi_overview

- **物化**：table
- **说明**：项目（含一般节点）维度，14 个指标，按年月聚合

> 对应需求 Sheet3 第 1 组："项目（含一般节点）"

```sql
WITH base AS (
  SELECT
    plan_year,
    plan_month,

    SUM(total_cnt)                          AS total_cnt,
    SUM(pending_normal_cnt)                 AS pending_normal_cnt,
    SUM(due_cnt)                            AS due_cnt,
    SUM(on_time_cnt)                        AS on_time_cnt,
    SUM(overdue_completed_cnt)              AS overdue_completed_cnt,
    SUM(completed_cnt)                      AS completed_cnt,
    SUM(incomplete_cnt)                     AS incomplete_cnt,
    SUM(abnormal_pending_cnt)               AS abnormal_pending_cnt,
    SUM(overdue_incomplete_unchanged_cnt)   AS overdue_incomplete_unchanged_cnt,
    SUM(overdue_incomplete_changed_cnt)     AS overdue_incomplete_changed_cnt
  FROM {{ ref('dws_period_node_summary') }}
  GROUP BY plan_year, plan_month
),

-- 本周期以外完成节点：实际完成时间在本周期内，但计划时间不在本周期内
outside_completed AS (
  SELECT
    d.actual_year        AS plan_year,
    d.actual_month       AS plan_month,
    COUNT(*)             AS outside_completed_cnt
  FROM {{ ref('dwd_project_node') }} d
  WHERE d.is_completed = true
    AND d.actual_year IS NOT NULL
    AND (d.plan_year != d.actual_year OR d.plan_month != d.actual_month)
  GROUP BY d.actual_year, d.actual_month
)

SELECT
  b.plan_year,
  b.plan_month,

  -- 1. 项目本周期节点总数
  b.total_cnt,
  -- 2. 正常待完成
  b.pending_normal_cnt,
  -- 3. 已到时间节点总数
  b.due_cnt,
  -- 4. 本周期以外完成节点总数
  COALESCE(oc.outside_completed_cnt, 0) AS outside_completed_cnt,
  -- 5. 未完成总数
  b.incomplete_cnt,
  -- 6. 按时完成数
  b.on_time_cnt,
  -- 7. 超期完成数
  b.overdue_completed_cnt,
  -- 8. 节点完成总数（含本周期以外完成）
  b.completed_cnt + COALESCE(oc.outside_completed_cnt, 0) AS completed_total_cnt,

  -- 9. 节点完成百分比
  CASE WHEN (b.due_cnt + COALESCE(oc.outside_completed_cnt, 0)) = 0 THEN 0
       ELSE ROUND(
         (b.completed_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric
         / (b.due_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric, 4)
  END AS completion_rate,

  -- 10. 按时完成百分比
  CASE WHEN (b.due_cnt + COALESCE(oc.outside_completed_cnt, 0)) = 0 THEN 0
       ELSE ROUND(
         b.on_time_cnt::numeric
         / (b.due_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric, 4)
  END AS on_time_rate,

  -- 11. 超期完成百分比
  CASE WHEN (b.total_cnt + COALESCE(oc.outside_completed_cnt, 0)) = 0 THEN 0
       ELSE ROUND(
         (b.overdue_completed_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric
         / (b.total_cnt + COALESCE(oc.outside_completed_cnt, 0))::numeric, 4)
  END AS overdue_completion_rate,

  -- 12. 不正常待变更节点数
  b.abnormal_pending_cnt,
  -- 13. 超期未完成未变更节点数
  b.overdue_incomplete_unchanged_cnt,
  -- 14. 超期未完成已变更节点数
  b.overdue_incomplete_changed_cnt

FROM base b
LEFT JOIN outside_completed oc
  ON oc.plan_year = b.plan_year AND oc.plan_month = b.plan_month
ORDER BY b.plan_year, b.plan_month
```

### 6.2 ads_project_non_general_kpi

- **物化**：table
- **说明**：项目（除一般节点）维度，4 个指标

> 对应需求 Sheet3 第 2 组："项目（除一般节点）"

```sql
WITH base AS (
  SELECT
    plan_year,
    plan_month,

    SUM(CASE WHEN NOT is_general_node THEN abnormal_pending_cnt ELSE 0 END)               AS abnormal_pending_cnt,
    SUM(CASE WHEN NOT is_general_node THEN overdue_incomplete_unchanged_cnt ELSE 0 END)   AS overdue_incomplete_unchanged_cnt,
    SUM(CASE WHEN NOT is_general_node THEN overdue_incomplete_changed_cnt ELSE 0 END)     AS overdue_incomplete_changed_cnt,
    SUM(CASE WHEN NOT is_general_node THEN overdue_completed_unchanged_cnt ELSE 0 END)    AS overdue_completed_unchanged_cnt,
    SUM(CASE WHEN NOT is_general_node THEN total_cnt - pending_normal_cnt ELSE 0 END)     AS due_cnt_non_general
  FROM {{ ref('dws_period_node_type_summary') }}
  GROUP BY plan_year, plan_month
)

SELECT
  plan_year,
  plan_month,

  -- 1. 不正常待变更节点数（除一般节点）
  abnormal_pending_cnt,
  -- 2. 超期未完成且未走变更流程的节点数（除一般节点）
  overdue_incomplete_unchanged_cnt,
  -- 3. 超期未完成但走完变更流程节点数（除一般节点）
  overdue_incomplete_changed_cnt,
  -- 4. 超期已完成未变更（除一般节点）
  overdue_completed_unchanged_cnt,

  -- 5. 不正常待变更百分比（含不正常待变更+超期未完成未变更）
  CASE WHEN due_cnt_non_general = 0 THEN 0
       ELSE ROUND(
         (abnormal_pending_cnt + overdue_incomplete_unchanged_cnt)::numeric
         / due_cnt_non_general::numeric, 4)
  END AS abnormal_rate,

  -- 6. 节点超期百分比
  CASE WHEN due_cnt_non_general = 0 THEN 0
       ELSE ROUND(
         (overdue_incomplete_unchanged_cnt + overdue_incomplete_changed_cnt)::numeric
         / due_cnt_non_general::numeric, 4)
  END AS overdue_rate

FROM base
ORDER BY plan_year, plan_month
```

### 6.3 ads_project_incomplete_risk

- **物化**：table
- **说明**：截止目前未完成节点，按风险等级和节点类型细分

> 对应需求 Sheet3 第 3 组："项目（截止目前未完成节点）"

```sql
SELECT
  d.plan_year,
  d.plan_month,

  -- 1. 截止目前未完成高风险节点数
  SUM(CASE WHEN d.risk_level = '高' AND d.is_incomplete THEN 1 ELSE 0 END)                     AS incomplete_high_risk_cnt,
  -- 2. 截止目前未完成中风险节点数
  SUM(CASE WHEN d.risk_level = '中' AND d.is_incomplete THEN 1 ELSE 0 END)                     AS incomplete_mid_risk_cnt,
  -- 3. 截止目前未完成里程碑节点数
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_incomplete THEN 1 ELSE 0 END)              AS incomplete_milestone_cnt,
  -- 4. 截止目前未完成重大节点数
  SUM(CASE WHEN d.node_type = '重大节点' AND d.is_incomplete THEN 1 ELSE 0 END)                AS incomplete_major_cnt,
  -- 5. 截止目前未完成重要节点数
  SUM(CASE WHEN d.node_type = '重要节点' AND d.is_incomplete THEN 1 ELSE 0 END)                AS incomplete_important_cnt

FROM {{ ref('dwd_project_node') }} d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_month
ORDER BY d.plan_year, d.plan_month
```

### 6.4 ads_project_milestone_kpi

- **物化**：table
- **说明**：本周期内节点，12 个指标（里程碑专项 + 风险 + 节点类型总数）

> 对应需求 Sheet3 第 4 组："项目（本周期内节点）"

```sql
SELECT
  d.plan_year,
  d.plan_month,

  -- === 里程碑节点专项 ===
  -- 1. 里程碑节点按时完成数
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_on_time THEN 1 ELSE 0 END)                AS milestone_on_time_cnt,
  -- 2. 里程碑节点超期完成数
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_overdue_completed THEN 1 ELSE 0 END)      AS milestone_overdue_completed_cnt,
  -- 3. 里程碑节点正常待完成数
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.completion_status = '正常待完成' THEN 1 ELSE 0 END) AS milestone_pending_cnt,
  -- 4. 里程碑未完成数（用于完成率分母）
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_incomplete THEN 1 ELSE 0 END)              AS milestone_incomplete_cnt,

  -- 5. 里程碑节点完成总百分比
  CASE
    WHEN (SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_incomplete THEN 1 ELSE 0 END)
        + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_on_time THEN 1 ELSE 0 END)
        + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_overdue_completed THEN 1 ELSE 0 END)) = 0
    THEN 0
    ELSE ROUND(
      (SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_on_time THEN 1 ELSE 0 END)
       + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_overdue_completed THEN 1 ELSE 0 END))::numeric
      / (SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_incomplete THEN 1 ELSE 0 END)
       + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_on_time THEN 1 ELSE 0 END)
       + SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_overdue_completed THEN 1 ELSE 0 END))::numeric
    , 4)
  END AS milestone_completion_rate,

  -- === 风险统计 ===
  -- 6. 高风险节点数
  SUM(CASE WHEN d.risk_level = '高' THEN 1 ELSE 0 END)           AS high_risk_cnt,
  -- 7. 中风险节点数
  SUM(CASE WHEN d.risk_level = '中' THEN 1 ELSE 0 END)           AS mid_risk_cnt,

  -- === 节点类型总数 ===
  -- 8. 里程碑节点总数
  SUM(CASE WHEN d.node_type = '里程碑节点' THEN 1 ELSE 0 END)    AS milestone_total_cnt,
  -- 9. 重大节点总数
  SUM(CASE WHEN d.node_type = '重大节点' THEN 1 ELSE 0 END)      AS major_total_cnt,
  -- 10. 重要节点总数
  SUM(CASE WHEN d.node_type = '重要节点' THEN 1 ELSE 0 END)      AS important_total_cnt,

  -- === 里程碑按完成状态细分（冗余，方便看板直接取） ===
  -- 11. 里程碑节点按时完成数（同 #1，方便区分上下文）
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_on_time THEN 1 ELSE 0 END)                AS milestone_on_time_cnt_2,
  -- 12. 里程碑节点超期完成数（同 #2）
  SUM(CASE WHEN d.node_type = '里程碑节点' AND d.is_overdue_completed THEN 1 ELSE 0 END)      AS milestone_overdue_completed_cnt_2

FROM {{ ref('dwd_project_node') }} d
WHERE d.plan_year IS NOT NULL
GROUP BY d.plan_year, d.plan_month
ORDER BY d.plan_year, d.plan_month
```

---

## 7. 参数化查询模板（方案 C 补充）

以下模板不作为 dbt 模型，而是保存为**平台即席查询**，供自定义时间区间场景使用。

### 7.1 自定义区间综合 KPI

```sql
-- 参数：:start_date, :end_date
WITH nodes AS (
  SELECT * FROM dwd_project_node
  WHERE plan_date BETWEEN :start_date AND :end_date
),
outside AS (
  SELECT COUNT(*) AS cnt
  FROM dwd_project_node
  WHERE is_completed = true
    AND actual_date BETWEEN :start_date AND :end_date
    AND (plan_date < :start_date OR plan_date > :end_date)
)
SELECT
  COUNT(*)                                                       AS total_cnt,
  SUM(CASE WHEN completion_status = '正常待完成' THEN 1 ELSE 0 END) AS pending_normal_cnt,
  SUM(CASE WHEN is_on_time THEN 1 ELSE 0 END)                   AS on_time_cnt,
  SUM(CASE WHEN is_overdue_completed THEN 1 ELSE 0 END)         AS overdue_completed_cnt,
  SUM(CASE WHEN is_completed THEN 1 ELSE 0 END)                 AS completed_cnt,
  SUM(CASE WHEN is_incomplete THEN 1 ELSE 0 END)                AS incomplete_cnt,
  (SELECT cnt FROM outside)                                      AS outside_completed_cnt
FROM nodes;
```

### 7.2 自定义区间里程碑节点 KPI

```sql
-- 参数：:start_date, :end_date
SELECT
  node_type,
  COUNT(*)                                                       AS total_cnt,
  SUM(CASE WHEN is_on_time THEN 1 ELSE 0 END)                   AS on_time_cnt,
  SUM(CASE WHEN is_overdue_completed THEN 1 ELSE 0 END)         AS overdue_completed_cnt,
  SUM(CASE WHEN is_incomplete THEN 1 ELSE 0 END)                AS incomplete_cnt,
  SUM(CASE WHEN completion_status = '正常待完成' THEN 1 ELSE 0 END) AS pending_normal_cnt
FROM dwd_project_node
WHERE plan_date BETWEEN :start_date AND :end_date
GROUP BY node_type
ORDER BY node_type;
```

---

## 8. 指标与模型映射总表

| # | 维度 | 指标名称 | 所在 ADS 表 | 字段 |
|---|------|---------|------------|------|
| 1 | 含一般节点 | 项目本周期节点总数 | ads_project_kpi_overview | total_cnt |
| 2 | 含一般节点 | 正常待完成 | ads_project_kpi_overview | pending_normal_cnt |
| 3 | 含一般节点 | 已到时间节点总数 | ads_project_kpi_overview | due_cnt |
| 4 | 含一般节点 | 本周期以外完成节点总数 | ads_project_kpi_overview | outside_completed_cnt |
| 5 | 含一般节点 | 未完成总数 | ads_project_kpi_overview | incomplete_cnt |
| 6 | 含一般节点 | 按时完成数 | ads_project_kpi_overview | on_time_cnt |
| 7 | 含一般节点 | 正常待完成（重复） | ads_project_kpi_overview | pending_normal_cnt |
| 8 | 含一般节点 | 超期完成数 | ads_project_kpi_overview | overdue_completed_cnt |
| 9 | 含一般节点 | 节点完成总数 | ads_project_kpi_overview | completed_total_cnt |
| 10 | 含一般节点 | 节点完成百分比 | ads_project_kpi_overview | completion_rate |
| 11 | 含一般节点 | 按时完成百分比 | ads_project_kpi_overview | on_time_rate |
| 12 | 含一般节点 | 超期完成百分比 | ads_project_kpi_overview | overdue_completion_rate |
| 13 | 含一般节点 | 不正常待变更节点数 | ads_project_kpi_overview | abnormal_pending_cnt |
| 14 | 含一般节点 | 超期未完成未变更节点数 | ads_project_kpi_overview | overdue_incomplete_unchanged_cnt |
| 15 | 除一般节点 | 超期未完成已变更节点数 | ads_project_non_general_kpi | overdue_incomplete_changed_cnt |
| 16 | 除一般节点 | 超期已完成未变更 | ads_project_non_general_kpi | overdue_completed_unchanged_cnt |
| 17 | 除一般节点 | 不正常待变更百分比 | ads_project_non_general_kpi | abnormal_rate |
| 18 | 除一般节点 | 节点超期百分比 | ads_project_non_general_kpi | overdue_rate |
| 19 | 未完成节点 | 未完成高风险节点数 | ads_project_incomplete_risk | incomplete_high_risk_cnt |
| 20 | 未完成节点 | 未完成中风险节点数 | ads_project_incomplete_risk | incomplete_mid_risk_cnt |
| 21 | 未完成节点 | 未完成里程碑节点数 | ads_project_incomplete_risk | incomplete_milestone_cnt |
| 22 | 未完成节点 | 未完成重大节点数 | ads_project_incomplete_risk | incomplete_major_cnt |
| 23 | 未完成节点 | 未完成重要节点数 | ads_project_incomplete_risk | incomplete_important_cnt |
| 24 | 本周期节点 | 里程碑按时完成数 | ads_project_milestone_kpi | milestone_on_time_cnt |
| 25 | 本周期节点 | 里程碑超期完成数 | ads_project_milestone_kpi | milestone_overdue_completed_cnt |
| 26 | 本周期节点 | 里程碑正常待完成数 | ads_project_milestone_kpi | milestone_pending_cnt |
| 27 | 本周期节点 | 里程碑完成总百分比 | ads_project_milestone_kpi | milestone_completion_rate |
| 28 | 本周期节点 | 高风险节点数 | ads_project_milestone_kpi | high_risk_cnt |
| 29 | 本周期节点 | 中风险节点数 | ads_project_milestone_kpi | mid_risk_cnt |
| 30 | 本周期节点 | 里程碑节点总数 | ads_project_milestone_kpi | milestone_total_cnt |
| 31 | 本周期节点 | 重大节点总数 | ads_project_milestone_kpi | major_total_cnt |
| 32 | 本周期节点 | 重要节点总数 | ads_project_milestone_kpi | important_total_cnt |
| 33 | 本周期节点 | 里程碑按时完成数 | ads_project_milestone_kpi | milestone_on_time_cnt_2 |
| 34 | 本周期节点 | 里程碑超期完成数 | ads_project_milestone_kpi | milestone_overdue_completed_cnt_2 |
| 35 | - | 自定义区间查询 | 参数化模板 | 即席查询 |

---

## 9. 模型清单汇总

| 层级 | 模型名 | 物化 | 上游依赖 |
|------|--------|------|---------|
| ODS | ods_project_progress | table (Excel 导入) | - |
| DIM | dim_completion_status | table | - |
| DIM | dim_node_type | table | - |
| DIM | dim_risk_level | table | - |
| DWD | dwd_project_node | table | ods + 3 dim |
| DWS | dws_period_node_summary | table | dwd |
| DWS | dws_period_node_type_summary | table | dwd |
| DWS | dws_period_risk_summary | table | dwd |
| ADS | ads_project_kpi_overview | table | dws_period_node_summary + dwd |
| ADS | ads_project_non_general_kpi | table | dws_period_node_type_summary |
| ADS | ads_project_incomplete_risk | table | dwd |
| ADS | ads_project_milestone_kpi | table | dwd |
| - | 参数化查询模板（2 个） | 即席查询 | dwd |

共 **12 个 dbt 模型 + 2 个即席查询模板**。
