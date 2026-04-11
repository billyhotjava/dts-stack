# 一级看板 — 项目综合看板 (Overview) 设计方案

> v3 重建 · 方案 C (进度为主轴 + 异常标红)
> 核心维度：**项目** + **科室**
> 全局筛选器：`{{dateFrom}}` / `{{dateTo}}` / `{{deptId}}` / `{{projectNo}}`

---

## 整体布局 (1920 × 1080)

```
┌──────────────────────────────────────────────────────────────┐
│ Row 0: 标题栏 + 全局筛选器 (h=60)                              │
├──────────────────────────────────────────────────────────────┤
│ Row 1: KPI 指标卡片 × 6 (h=120)                               │
│ ┌────┐ ┌────┐ ┌────┐ ┌────────┐ ┌──────────┐ ┌──────┐      │
│ │项目 │ │进行 │ │完成 │ │未闭环   │ │未整改技术  │ │高风险 │     │
│ │总数 │ │中   │ │率   │ │质量问题 │ │更改单     │ │数量   │     │
│ └────┘ └────┘ └────┘ └────────┘ └──────────┘ └──────┘      │
├──────────────────────────────────────────────────────────────┤
│ Row 2: 四域 Tab 图表 (h=380)                                  │
│ ┌─[进度计划]─[质量问题]─[技术状态]─[项目风险]──────────────────┐ │
│ │  ┌────────────────────┐ ┌────────────────────────────┐    │ │
│ │  │  按项目统计 (柱状图)  │ │  按科室统计 (横条图)          │    │ │
│ │  │                    │ │                            │    │ │
│ │  │                    │ │                            │    │ │
│ │  └────────────────────┘ └────────────────────────────┘    │ │
│ └──────────────────────────────────────────────────────────┘ │
├──────────────────────────────────────────────────────────────┤
│ Row 3: 项目进度甘特图 (h=400, 滚动, 仅进行中项目)               │
│ ┌──────────────────────────────────────────────────────────┐ │
│ │ 项目编号 | 责任科室        | 项目经理 | 完成率 | 甘特图     │ │
│ │ PRJ-001 | 一室(15),二室(8)| 张三    | 75%   | ██蓝██     │ │
│ │         |                |        |       | ████绿███  │ │
│ │ PRJ-002 | 三室(12),四室(5)| 李四    | 30%   | ██蓝██     │ │
│ │         |                |        |       | ██红████   │ │
│ │ (蓝=计划, 绿=实际正常, 红=实际延期)                        │ │
│ └──────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

---

## Row 1: KPI 指标卡片

### SQL-OV-KPI: 综合 KPI 查询

```sql
WITH progress AS (
    SELECT
        COUNT(DISTINCT s.project_no) AS project_total,
        COUNT(DISTINCT CASE WHEN s.incomplete_cnt > 0 THEN s.project_no END) AS project_active,
        SUM(s.completed_total_cnt) AS completed_sum,
        SUM(s.due_cnt + s.outside_completed_cnt) AS due_sum
    FROM biz_dws_progress_monthly_v2 s
    WHERE s.plan_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
      AND s.plan_month <= to_char({{dateTo}}::date, 'YYYY-MM')
      AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR s.project_no ILIKE '%' || {{projectNo}} || '%')
      AND ({{deptId}} IS NULL OR {{deptId}} = ''
           OR s.project_no IN (
               SELECT DISTINCT project_no FROM biz_dwd_project_node_v2
               WHERE dept = {{deptId}}
                 AND plan_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
                 AND plan_month <= to_char({{dateTo}}::date, 'YYYY-MM')
           ))
),
quality AS (
    SELECT SUM(q.open_issue_cnt) AS open_quality_issues
    FROM biz_dws_quality_monthly_v2 q
    WHERE q.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
      AND q.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
      AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR q.project_no ILIKE '%' || {{projectNo}} || '%')
      AND ({{deptId}} IS NULL OR {{deptId}} = '' OR q.dept = {{deptId}})
),
tech AS (
    SELECT SUM(t.reform_pending_i_ii) AS pending_reform_cnt
    FROM biz_dws_tech_state_monthly_v2 t
    WHERE t.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
      AND t.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
      AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR t.project_no ILIKE '%' || {{projectNo}} || '%')
      AND ({{deptId}} IS NULL OR {{deptId}} = '' OR t.dept = {{deptId}})
),
risk AS (
    SELECT SUM(r.high_cnt) AS high_risk_cnt
    FROM biz_dws_risk_monthly_v2 r
    WHERE r.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
      AND r.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
      AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR r.project_no ILIKE '%' || {{projectNo}} || '%')
      AND ({{deptId}} IS NULL OR {{deptId}} = '' OR r.dept = {{deptId}})
)
SELECT
    p.project_total                              AS "项目总数",
    p.project_active                             AS "进行中项目",
    CASE WHEN p.due_sum = 0 THEN 0
         ELSE ROUND(p.completed_sum::numeric / p.due_sum::numeric * 100, 1)
    END                                          AS "项目完成率",
    COALESCE(q.open_quality_issues, 0)           AS "未闭环质量问题数",
    COALESCE(t.pending_reform_cnt, 0)            AS "未整改落实技术更改单数",
    COALESCE(r.high_risk_cnt, 0)                 AS "高风险数量"
FROM progress p, quality q, tech t, risk r
```

### KPI 卡片绑定

| # | 卡片标签 | 绑定字段 | 着色规则 |
|---|---------|---------|---------|
| 1 | 项目总数 | `项目总数` | 无（中性蓝） |
| 2 | 进行中项目 | `进行中项目` | 无（中性蓝） |
| 3 | 项目完成率 | `项目完成率` + `%` | ≥80 绿, ≥60 橙, <60 红 |
| 4 | 未闭环质量问题 | `未闭环质量问题数` | =0 绿, ≤5 橙, >5 红 |
| 5 | 未整改技术更改单 | `未整改落实技术更改单数` | =0 绿, ≤3 橙, >3 红 |
| 6 | 高风险数量 | `高风险数量` | =0 绿, ≤2 橙, >2 红 |

---

## Row 2: 四域 Tab 图表

### Tab 1: 进度计划

#### SQL-OV-PROGRESS-BY-PROJECT: 按项目统计

```sql
SELECT
    d.project_no                                 AS "项目编号",
    COUNT(*)                                     AS "节点总数",
    SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END)  AS "已完成",
    SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END)  AS "未完成",
    SUM(CASE WHEN d.completion_status = '正常待完成' THEN 1 ELSE 0 END) AS "正常待完成",
    CASE WHEN COUNT(*) = 0 THEN 0
         ELSE ROUND(SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END)::numeric
                  / COUNT(*)::numeric * 100, 1)
    END                                          AS "完成率"
FROM biz_dwd_project_node_v2 d
WHERE d.plan_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND d.plan_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR d.project_no ILIKE '%' || {{projectNo}} || '%')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR d.dept = {{deptId}})
GROUP BY d.project_no
ORDER BY SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END) DESC
```

> **图表**: 堆叠柱状图，X=项目编号，Y=节点数，堆叠=已完成(绿)/未完成(红)/正常待完成(灰)

#### SQL-OV-PROGRESS-BY-DEPT: 按科室统计

```sql
SELECT
    d.dept                                       AS "责任科室",
    COUNT(*)                                     AS "节点总数",
    SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END) AS "已完成",
    SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END) AS "未完成",
    CASE WHEN COUNT(*) = 0 THEN 0
         ELSE ROUND(SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END)::numeric
                  / COUNT(*)::numeric * 100, 1)
    END                                          AS "完成率"
FROM biz_dwd_project_node_v2 d
WHERE d.plan_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND d.plan_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR d.project_no ILIKE '%' || {{projectNo}} || '%')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR d.dept = {{deptId}})
  AND d.dept IS NOT NULL
GROUP BY d.dept
ORDER BY SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END) DESC
```

> **图表**: 横条图，Y=科室名，X=未完成数量，条形颜色按完成率着色 (≥80绿 / ≥60橙 / <60红)

---

### Tab 2: 质量问题

#### SQL-OV-QUALITY-BY-PROJECT: 按项目统计

```sql
SELECT
    q.project_no                                 AS "项目编号",
    SUM(q.new_issue_cnt)                         AS "新增问题",
    SUM(q.open_issue_cnt)                        AS "现存问题",
    SUM(q.zero_completed_cnt)                    AS "已归零",
    SUM(q.no_zero_plan_cnt)                      AS "未提交归零计划"
FROM biz_dws_quality_monthly_v2 q
WHERE q.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND q.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR q.project_no ILIKE '%' || {{projectNo}} || '%')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR q.dept = {{deptId}})
GROUP BY q.project_no
ORDER BY SUM(q.open_issue_cnt) DESC
```

> **图表**: 分组柱状图，X=项目编号，Y=问题数，分组=现存问题(红)/已归零(绿)/未提交计划(橙)

#### SQL-OV-QUALITY-BY-DEPT: 按科室统计

```sql
SELECT
    q.dept                                       AS "责任科室",
    SUM(q.new_issue_cnt)                         AS "新增问题",
    SUM(q.open_issue_cnt)                        AS "现存问题",
    SUM(q.zero_completed_cnt)                    AS "已归零"
FROM biz_dws_quality_monthly_v2 q
WHERE q.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND q.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR q.project_no ILIKE '%' || {{projectNo}} || '%')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR q.dept = {{deptId}})
  AND q.dept IS NOT NULL
GROUP BY q.dept
ORDER BY SUM(q.open_issue_cnt) DESC
```

> **图表**: 横条图，Y=科室名，X=现存问题数 (红色)

---

### Tab 3: 技术状态

#### SQL-OV-TECHSTATE-BY-PROJECT: 按项目统计

```sql
SELECT
    t.project_no                                 AS "项目编号",
    SUM(t.total_change_cnt)                      AS "变更总数",
    SUM(t.new_cat_i)                             AS "I类",
    SUM(t.new_cat_ii)                            AS "II类",
    SUM(t.new_cat_iii)                           AS "III类",
    SUM(t.order_signed_cnt)                      AS "已签署",
    SUM(t.order_unsigned_cat_i + t.order_unsigned_cat_ii + t.order_unsigned_cat_iii) AS "未签署",
    SUM(t.reform_done_i_ii)                      AS "已整改",
    SUM(t.reform_pending_i_ii)                   AS "未整改"
FROM biz_dws_tech_state_monthly_v2 t
WHERE t.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND t.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR t.project_no ILIKE '%' || {{projectNo}} || '%')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR t.dept = {{deptId}})
GROUP BY t.project_no
ORDER BY SUM(t.reform_pending_i_ii) DESC
```

> **图表**: 分组柱状图，X=项目编号，分组=已整改(绿)/未整改(红)/未签署(橙)

#### SQL-OV-TECHSTATE-BY-DEPT: 按科室统计

```sql
SELECT
    t.dept                                       AS "责任科室",
    SUM(t.total_change_cnt)                      AS "变更总数",
    SUM(t.reform_pending_i_ii)                   AS "未整改",
    SUM(t.order_unsigned_cat_i + t.order_unsigned_cat_ii + t.order_unsigned_cat_iii) AS "未签署"
FROM biz_dws_tech_state_monthly_v2 t
WHERE t.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND t.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR t.project_no ILIKE '%' || {{projectNo}} || '%')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR t.dept = {{deptId}})
  AND t.dept IS NOT NULL
GROUP BY t.dept
ORDER BY SUM(t.reform_pending_i_ii) DESC
```

> **图表**: 横条图，Y=科室名，X=未整改数 (红色)

---

### Tab 4: 项目风险

#### SQL-OV-RISK-BY-PROJECT: 按项目统计

```sql
SELECT
    r.project_no                                 AS "项目编号",
    SUM(r.total_risk_cnt)                        AS "风险总数",
    SUM(r.high_cnt)                              AS "高风险",
    SUM(r.mid_cnt)                               AS "中风险",
    SUM(r.low_cnt)                               AS "低风险",
    SUM(r.open_cnt)                              AS "未释放"
FROM biz_dws_risk_monthly_v2 r
WHERE r.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND r.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR r.project_no ILIKE '%' || {{projectNo}} || '%')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR r.dept = {{deptId}})
GROUP BY r.project_no
ORDER BY SUM(r.high_cnt) DESC
```

> **图表**: 堆叠柱状图，X=项目编号，堆叠=高(红)/中(橙)/低(黄)

#### SQL-OV-RISK-BY-DEPT: 按科室统计

```sql
SELECT
    r.dept                                       AS "责任科室",
    SUM(r.total_risk_cnt)                        AS "风险总数",
    SUM(r.high_cnt)                              AS "高风险",
    SUM(r.mid_cnt)                               AS "中风险",
    SUM(r.low_cnt)                               AS "低风险"
FROM biz_dws_risk_monthly_v2 r
WHERE r.period_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
  AND r.period_month <= to_char({{dateTo}}::date, 'YYYY-MM')
  AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR r.project_no ILIKE '%' || {{projectNo}} || '%')
  AND ({{deptId}} IS NULL OR {{deptId}} = '' OR r.dept = {{deptId}})
  AND r.dept IS NOT NULL
GROUP BY r.dept
ORDER BY SUM(r.high_cnt) DESC
```

> **图表**: 横条图，Y=科室名，X=高风险数 (红色)

---

## Row 3: 项目进度甘特图

### SQL-OV-GANTT: 项目级甘特（仅进行中项目）

```sql
WITH project_summary AS (
    SELECT
        d.project_no,
        MAX(d.project_manager)                       AS project_manager,
        COUNT(*)                                     AS total_cnt,
        SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END) AS completed_cnt,
        SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END) AS incomplete_cnt,
        CASE WHEN COUNT(*) = 0 THEN 0
             ELSE ROUND(SUM(CASE WHEN d.is_completed THEN 1 ELSE 0 END)::numeric
                      / COUNT(*)::numeric * 100, 1)
        END                                          AS completion_rate,
        MIN(d.plan_date)                             AS plan_start,
        MAX(d.plan_date)                             AS plan_end,
        MIN(d.actual_date)                           AS actual_start,
        MAX(CASE WHEN d.is_completed THEN d.actual_date END) AS actual_end,
        -- 是否有延期未完成节点
        SUM(CASE WHEN d.is_incomplete AND d.delay_days > 0 THEN 1 ELSE 0 END) AS delayed_incomplete_cnt,
        MAX(COALESCE(d.delay_days, 0))               AS max_delay_days
    FROM biz_dwd_project_node_v2 d
    WHERE d.plan_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
      AND d.plan_month <= to_char({{dateTo}}::date, 'YYYY-MM')
      AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR d.project_no ILIKE '%' || {{projectNo}} || '%')
      AND ({{deptId}} IS NULL OR {{deptId}} = '' OR d.dept = {{deptId}})
    GROUP BY d.project_no
    -- 仅进行中：有未完成节点的项目
    HAVING SUM(CASE WHEN d.is_incomplete THEN 1 ELSE 0 END) > 0
),
-- 每个项目涉及的科室及其节点负载
dept_load AS (
    SELECT
        d.project_no,
        STRING_AGG(
            d.dept || '(' || d.cnt || ')',
            ', ' ORDER BY d.cnt DESC
        ) AS dept_summary
    FROM (
        SELECT project_no, dept, COUNT(*) AS cnt
        FROM biz_dwd_project_node_v2
        WHERE plan_month >= to_char({{dateFrom}}::date, 'YYYY-MM')
          AND plan_month <= to_char({{dateTo}}::date, 'YYYY-MM')
          AND ({{projectNo}} IS NULL OR {{projectNo}} = '' OR project_no ILIKE '%' || {{projectNo}} || '%')
          AND ({{deptId}} IS NULL OR {{deptId}} = '' OR dept = {{deptId}})
          AND dept IS NOT NULL
        GROUP BY project_no, dept
    ) d
    GROUP BY d.project_no
)
SELECT
    p.project_no                                 AS "项目编号",
    dl.dept_summary                              AS "责任科室",
    p.project_manager                            AS "项目经理",
    p.completion_rate                            AS "完成率",
    p.plan_start                                 AS "计划开始日期",
    p.plan_end                                   AS "计划完成日期",
    p.actual_start                               AS "实际开始日期",
    p.actual_end                                 AS "实际完成日期",
    CASE WHEN p.delayed_incomplete_cnt > 0
         THEN 'red' ELSE 'green'
    END                                          AS "实际进展颜色",
    p.max_delay_days                             AS "最大延期天数"
FROM project_summary p
LEFT JOIN dept_load dl ON dl.project_no = p.project_no
ORDER BY
    CASE WHEN p.delayed_incomplete_cnt > 0 THEN 0 ELSE 1 END,  -- 延期在前
    p.plan_start
```

### 甘特图字段绑定

| 字段 | 用途 |
|------|------|
| `项目编号` | 行标签（第一列） |
| `责任科室` | 行标签（第二列），格式如 `一室(15), 二室(8), 三室(3)` |
| `项目经理` | 行标签（第三列） |
| `完成率` | 行标签（第四列）+ 进度条填充比例 |
| `计划开始日期` | **蓝色条** 起点 |
| `计划完成日期` | **蓝色条** 终点 |
| `实际开始日期` | **实际条** 起点 |
| `实际完成日期` | **实际条** 终点（NULL 则用当前日期动态延伸） |
| `实际进展颜色` | `green` = 正常（**绿色条**）, `red` = 有延期（**红色条**） |

### 甘特图渲染规则 — 三色

```
蓝色条 ████████████████████████  ← 计划区间（plan_start ~ plan_end）
绿色条 ████████████░░░░░░░░░░░  ← 实际进展正常（actual_start ~ actual_end/today）
红色条 ████████████████████████  ← 实际进展延期（有延期未完成节点）
```

1. 每行两根横条：**上方蓝色 = 计划区间**，**下方实色 = 实际进展**
2. 实际条颜色：**无延期 → 绿色**，**有延期未完成节点 → 红色**
3. 进行中项目的实际条终点 = 当前日期（动态延伸）
4. 延期项目排在最前面（红色在前，醒目提示）
5. 滚动展示，每页 ~10 行

### 甘特图"责任科室"说明

> 一个项目通常涉及多个科室。`责任科室` 字段展示该项目下**所有科室及其节点数**，
> 格式为 `一室(15), 二室(8), 三室(3)`，按节点数降序排列。
> 这样可以直观看出每个科室在该项目中的负载分布。

---

## 全局筛选器

| 筛选器 | 控件类型 | 默认值 | 说明 |
|--------|---------|--------|------|
| `{{dateFrom}}` | 日期选择器 | 当年1月1日 | 统计周期开始 |
| `{{dateTo}}` | 日期选择器 | 当前日期 | 统计周期结束 |
| `{{deptId}}` | 下拉选择 | 空(全部) | 科室筛选 |
| `{{projectNo}}` | 文本输入 | 空(全部) | 项目编号/名称模糊匹配 |

> 去掉旧版的 `{{riskLevel}}` — overview 不需要按风险等级筛选

---

## SQL 汇总

| 编号 | 名称 | 用途 | 数据源 |
|------|------|------|--------|
| SQL-OV-KPI | 综合KPI | Row 1 六个卡片 | 四域 DWS 联合 |
| SQL-OV-PROGRESS-BY-PROJECT | 进度-按项目 | Tab1 左图 | DWD project_node |
| SQL-OV-PROGRESS-BY-DEPT | 进度-按科室 | Tab1 右图 | DWD project_node |
| SQL-OV-QUALITY-BY-PROJECT | 质量-按项目 | Tab2 左图 | DWS quality_monthly |
| SQL-OV-QUALITY-BY-DEPT | 质量-按科室 | Tab2 右图 | DWS quality_monthly |
| SQL-OV-TECHSTATE-BY-PROJECT | 技术状态-按项目 | Tab3 左图 | DWS tech_state_monthly |
| SQL-OV-TECHSTATE-BY-DEPT | 技术状态-按科室 | Tab3 右图 | DWS tech_state_monthly |
| SQL-OV-RISK-BY-PROJECT | 风险-按项目 | Tab4 左图 | DWS risk_monthly |
| SQL-OV-RISK-BY-DEPT | 风险-按科室 | Tab4 右图 | DWS risk_monthly |
| SQL-OV-GANTT | 项目甘特图(仅进行中) | Row 3 | DWD project_node |

**共 10 条 SQL，覆盖 1 + 8 + 1 = 10 个组件/组件组。**

---

## 与旧版 S1 的差异

| 维度 | 旧版 S1 (v2) | 新版 Overview (v3) |
|------|-------------|-------------------|
| 组件数 | 18 个分散组件 | 10 个 SQL 组（更聚焦） |
| 叙事逻辑 | 健康度驱动（雷达图+仪表盘） | **进度为主轴 + 异常标红** |
| 核心维度 | 仅项目维度 | **项目 + 科室 双维度** |
| 图表组织 | 固定平铺 | **Tab 切换（4域 × 2维度）** |
| 甘特图 | 无（S2 才有，三级树） | **项目级甘特在 overview 底部，三色（蓝/绿/红）** |
| 健康度指标 | 综合健康度 + 三域雷达 | 去掉（移到二级看板） |
| 无数据组件 | 6 个占位 | 0 个 |
