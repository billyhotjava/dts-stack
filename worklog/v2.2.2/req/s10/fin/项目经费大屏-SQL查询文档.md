# 项目经费大屏 — SQL 查询文档

> Decision Twins · 项目经费大屏  
> 本文档对应大屏中所有数据组件的 SQL 查询语句及计算原理

---

## 数据表结构

假设项目经费表名为 `project_fund`，字段定义如下：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `project_id` | VARCHAR | 项目编号，如 `PRJ-001` |
| `project_name` | VARCHAR | 项目名称 |
| `dept_name` | VARCHAR | 所属部门 |
| `pm_name` | VARCHAR | 项目经理 |
| `cycle_start` | DATE | 研制周期开始日期 |
| `cycle_end` | DATE | 研制周期结束日期 |
| `total_fund` | NUMERIC(15,2) | 总经费（万元） |
| `direct_ctrl` | NUMERIC(15,2) | 直接成本控制数（万元） |
| `direct_spent` | NUMERIC(15,2) | 直接成本支出金额（万元） |
| `indirect_spent` | NUMERIC(15,2) | 间接费用支出和收益总额（万元） |

---

## 公共筛选条件

```sql
WHERE 1=1
  AND (:dept_filter   IS NULL OR dept_name = :dept_filter)
  AND (:health_filter IS NULL OR {health_expression} = :health_filter)
```

> `{health_expression}` 为经费健康度的 CASE 表达式，详见第二节。

---

## 一、派生字段计算视图

所有大屏组件共享同一组派生指标，建议创建视图或 CTE 统一计算：

```sql
CREATE VIEW v_project_fund AS
SELECT
    project_id,
    project_name,
    dept_name,
    pm_name,
    TO_CHAR(cycle_start, 'YYYY.MM') || '-' || TO_CHAR(cycle_end, 'YYYY.MM')
        AS cycle,

    -- 研制周期总月数
    EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
    + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start))
        AS total_months,

    -- 已用月数（截至当前日期）
    GREATEST(
        LEAST(
            EXTRACT(YEAR FROM AGE(CURRENT_DATE, cycle_start)) * 12
            + EXTRACT(MONTH FROM AGE(CURRENT_DATE, cycle_start)),
            EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
            + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start))
        ),
        0
    )   AS elapsed_months,

    -- 原始字段
    total_fund,
    direct_ctrl,
    direct_spent,
    indirect_spent,

    -- ═══ 派生字段 ═══

    -- 预留间接费用和收益 = 总经费 - 直接成本控制数
    total_fund - direct_ctrl
        AS reserve_indirect,

    -- 直接成本执行率(%) = 直接成本支出 / 直接成本控制数 × 100
    CASE WHEN direct_ctrl > 0
         THEN ROUND(direct_spent * 100.0 / direct_ctrl, 1)
         ELSE 0 END
        AS direct_rate,

    -- 剩余直接成本 = 直接成本控制数 - 直接成本支出
    direct_ctrl - direct_spent
        AS direct_remain,

    -- 总支出 = 直接成本支出 + 间接费用支出
    direct_spent + indirect_spent
        AS total_spent,

    -- 总经费执行率(%) = 总支出 / 总经费 × 100
    CASE WHEN total_fund > 0
         THEN ROUND((direct_spent + indirect_spent) * 100.0 / total_fund, 1)
         ELSE 0 END
        AS total_rate,

    -- 间接费用执行率(%) = 间接费用支出 / 预留间接费用 × 100
    CASE WHEN (total_fund - direct_ctrl) > 0
         THEN ROUND(indirect_spent * 100.0 / (total_fund - direct_ctrl), 1)
         ELSE 0 END
        AS indirect_rate,

    -- 时间进度(%) = 已用月数 / 总月数 × 100
    CASE WHEN (EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
              + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start))) > 0
         THEN ROUND(
              GREATEST(LEAST(
                  EXTRACT(YEAR FROM AGE(CURRENT_DATE, cycle_start)) * 12
                  + EXTRACT(MONTH FROM AGE(CURRENT_DATE, cycle_start)),
                  EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
                  + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start))
              ), 0)
              * 100.0
              / (EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
                + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start))),
         1) ELSE 0 END
        AS time_rate,

    -- 经费健康度
    CASE
        -- 超支：直接成本执行率 > 100%
        WHEN direct_spent > direct_ctrl THEN 'overrun'
        -- 偏差 = 直接成本执行率 - 时间进度
        -- 正常：|偏差| ≤ 10%
        WHEN ABS(
            CASE WHEN direct_ctrl > 0 THEN direct_spent * 100.0 / direct_ctrl ELSE 0 END
          - CASE WHEN (EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
                      + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start))) > 0
                 THEN GREATEST(LEAST(
                     EXTRACT(YEAR FROM AGE(CURRENT_DATE, cycle_start)) * 12
                     + EXTRACT(MONTH FROM AGE(CURRENT_DATE, cycle_start)),
                     EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
                     + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start))
                 ), 0) * 100.0
                 / (EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
                   + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start)))
                 ELSE 0 END
        ) <= 10 THEN 'good'
        -- 偏快：执行率超前时间进度 > 10%
        WHEN (CASE WHEN direct_ctrl > 0 THEN direct_spent * 100.0 / direct_ctrl ELSE 0 END)
           > (CASE WHEN (EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
                        + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start))) > 0
                   THEN GREATEST(LEAST(
                       EXTRACT(YEAR FROM AGE(CURRENT_DATE, cycle_start)) * 12
                       + EXTRACT(MONTH FROM AGE(CURRENT_DATE, cycle_start)),
                       EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
                       + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start))
                   ), 0) * 100.0
                   / (EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
                     + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start)))
                   ELSE 0 END) + 10
        THEN 'fast'
        -- 偏慢：执行率落后时间进度 > 10%
        ELSE 'slow'
    END AS health

FROM project_fund;
```

### 派生字段汇总表

| 派生字段 | 计算公式 | 业务含义 |
|---------|---------|---------|
| **预留间接费用和收益** | 总经费 − 直接成本控制数 | 用于间接费用和利润的预留额度 |
| **直接成本执行率** | 直接成本支出 ÷ 直接成本控制数 × 100% | 直接成本消耗程度，>100% 即超支 |
| **剩余直接成本** | 直接成本控制数 − 直接成本支出 | 直接成本剩余可用额度，负数表示超支 |
| **总支出** | 直接成本支出 + 间接费用支出 | 项目实际发生的全部费用 |
| **总经费执行率** | 总支出 ÷ 总经费 × 100% | 全口径经费消耗程度 |
| **间接费用执行率** | 间接费用支出 ÷ 预留间接费用 × 100% | 间接费用消耗程度 |
| **时间进度** | 已用月数 ÷ 总月数 × 100% | 研制周期时间消耗程度 |
| **经费健康度** | 基于直接成本执行率与时间进度的偏差判定 | 见下方判定规则 |

### 经费健康度判定规则

```
偏差 = 直接成本执行率 − 时间进度

┌────────────────────────────────────────────┐
│ 直接成本执行率 > 100%      → 超支(overrun) │  红色
│ |偏差| ≤ 10%               → 正常(good)    │  绿色
│ 偏差 > 10%（花钱比时间快）  → 偏快(fast)    │  橙色
│ 偏差 < -10%（花钱比时间慢） → 偏慢(slow)    │  灰色
└────────────────────────────────────────────┘
```

---

## 二、KPI 指标卡片（6个）

### 2.1 查询语句

```sql
SELECT
    SUM(total_fund)                              AS sum_total_fund,
    SUM(direct_ctrl)                             AS sum_direct_ctrl,
    SUM(direct_spent)                            AS sum_direct_spent,

    -- 整体直接成本执行率
    ROUND(SUM(direct_spent) * 100.0
          / NULLIF(SUM(direct_ctrl), 0), 1)      AS overall_direct_rate,

    -- 整体总经费执行率
    ROUND(SUM(total_spent) * 100.0
          / NULLIF(SUM(total_fund), 0), 1)       AS overall_total_rate,

    -- 超支项目数
    COUNT(*) FILTER (WHERE health = 'overrun')   AS overrun_count
FROM v_project_fund
{WHERE_CLAUSE};
```

### 2.2 各卡片说明

| 卡片 | 字段 | 说明 |
|------|------|------|
| **总经费** | `sum_total_fund` | 全部项目总经费之和 |
| **直接成本控制数** | `sum_direct_ctrl` | 全部项目直接成本控制数之和 |
| **直接成本已支出** | `sum_direct_spent` | 全部项目直接成本支出之和 |
| **直接成本执行率** | `overall_direct_rate` | 汇总后的整体执行率，非各项目执行率的平均值 |
| **总经费执行率** | `overall_total_rate` | 汇总后的整体总执行率 |
| **超支项目** | `overrun_count` | 直接成本执行率 > 100% 的项目数 |

> **注意**：整体执行率 = SUM(支出) / SUM(控制数)，而不是 AVG(各项目执行率)，这样大项目的权重更高，更能反映真实情况。

---

## 三、经费结构分布（堆叠柱状图）

### 3.1 查询语句

```sql
SELECT
    project_name,
    direct_spent,
    indirect_spent,
    GREATEST(total_fund - direct_spent - indirect_spent, 0) AS remaining
FROM v_project_fund
{WHERE_CLAUSE}
ORDER BY total_fund DESC;
```

### 3.2 原理说明

每根柱子分三段：
- **直接成本支出**（蓝色）：已发生的直接成本
- **间接费用支出**（紫色）：已发生的间接费用
- **剩余**（浅灰）：总经费 − 直接支出 − 间接支出，即尚未消耗的经费

柱顶标签显示总执行率（`total_rate`）。

---

## 四、执行率 vs 时间进度（对比柱状图）

### 4.1 查询语句

```sql
SELECT
    project_name,
    direct_rate,
    time_rate,
    health
FROM v_project_fund
{WHERE_CLAUSE}
ORDER BY project_name;
```

### 4.2 原理说明

每个项目两根并排柱子：
- **灰色柱**：时间进度（已用月数 ÷ 总月数）
- **彩色柱**：直接成本执行率

颜色规则：
- 执行率 > 100%：红色（超支）
- 执行率 > 时间进度 × 1.1：橙色（花钱偏快）
- 其他：蓝色（正常）

理想状态下两根柱子等高，表示花钱速度与时间进度匹配。

---

## 五、经费健康度（四宫格）

### 5.1 查询语句

```sql
SELECT
    health,
    CASE health
        WHEN 'good'    THEN '正常'
        WHEN 'overrun' THEN '超支'
        WHEN 'fast'    THEN '偏快'
        WHEN 'slow'    THEN '偏慢'
    END AS health_label,
    COUNT(*) AS count
FROM v_project_fund
{WHERE_CLAUSE}
GROUP BY health
ORDER BY
    CASE health
        WHEN 'good'    THEN 1
        WHEN 'overrun' THEN 2
        WHEN 'fast'    THEN 3
        WHEN 'slow'    THEN 4
    END;
```

---

## 六、项目经费明细表

### 6.1 查询语句

```sql
SELECT
    project_id,
    project_name,
    cycle,
    total_fund,
    direct_ctrl,
    reserve_indirect,
    direct_spent,
    direct_rate,
    indirect_spent,
    total_spent,
    total_rate,
    health
FROM v_project_fund
{WHERE_CLAUSE}
ORDER BY project_id;
```

### 6.2 合计行

```sql
SELECT
    SUM(total_fund)       AS total_fund,
    SUM(direct_ctrl)      AS direct_ctrl,
    SUM(reserve_indirect) AS reserve_indirect,
    SUM(direct_spent)     AS direct_spent,
    ROUND(SUM(direct_spent) * 100.0 / NULLIF(SUM(direct_ctrl), 0), 1)   AS direct_rate,
    SUM(indirect_spent)   AS indirect_spent,
    SUM(total_spent)      AS total_spent,
    ROUND(SUM(total_spent) * 100.0 / NULLIF(SUM(total_fund), 0), 1)     AS total_rate
FROM v_project_fund
{WHERE_CLAUSE};
```

> **合计行的执行率**是重新用汇总数据计算的，不是各项目执行率的简单平均。

### 6.3 前端着色规则

| 字段 | 条件 | 颜色 | 含义 |
|-----|------|------|------|
| 直接成本支出 | `direct_spent > direct_ctrl` | 红色加粗 | 已超出控制数 |
| 直接成本执行率 | `> 100%` | 红色 | 超支 |
| 直接成本执行率 | `> 80%` | 橙色 | 接近控制上限 |
| 总执行率 | `> 90%` | 橙色 | 接近经费用尽 |
| 健康度 | 按 health 值 | 绿/红/橙/灰 | 对应正常/超支/偏快/偏慢 |

---

## 七、合并 CTE 查询

```sql
WITH base AS (
    SELECT * FROM v_project_fund
    {WHERE_CLAUSE}
),
kpi AS (
    SELECT
        SUM(total_fund)     AS sum_total_fund,
        SUM(direct_ctrl)    AS sum_direct_ctrl,
        SUM(direct_spent)   AS sum_direct_spent,
        SUM(total_spent)    AS sum_total_spent,
        SUM(indirect_spent) AS sum_indirect_spent,
        ROUND(SUM(direct_spent) * 100.0 / NULLIF(SUM(direct_ctrl), 0), 1) AS overall_direct_rate,
        ROUND(SUM(total_spent) * 100.0 / NULLIF(SUM(total_fund), 0), 1)   AS overall_total_rate,
        COUNT(*) FILTER (WHERE health = 'overrun') AS overrun_count
    FROM base
),
stacked AS (
    SELECT project_name, direct_spent, indirect_spent,
           GREATEST(total_fund - direct_spent - indirect_spent, 0) AS remaining,
           total_rate
    FROM base ORDER BY total_fund DESC
),
compare AS (
    SELECT project_name, direct_rate, time_rate, health
    FROM base ORDER BY project_name
),
health_summary AS (
    SELECT health, COUNT(*) AS count
    FROM base GROUP BY health
)
SELECT 'kpi'       AS section, row_to_json(kpi.*)               FROM kpi
UNION ALL
SELECT 'stacked',   json_agg(stacked.*)                         FROM stacked
UNION ALL
SELECT 'compare',   json_agg(compare.*)                         FROM compare
UNION ALL
SELECT 'health',    json_agg(health_summary.*)                  FROM health_summary
UNION ALL
SELECT 'detail',    json_agg(base.* ORDER BY project_id)        FROM base;
```

---

## 八、dbt Model 建议

```
models/
├── staging/
│   └── stg_project_fund.sql            -- 清洗原始字段，标准化日期格式
├── intermediate/
│   └── int_project_fund_derived.sql    -- 计算全部派生字段（对应 v_project_fund 视图）
└── marts/
    ├── mart_project_fund_kpi.sql       -- KPI 汇总
    ├── mart_project_fund_structure.sql -- 经费结构（堆叠图数据）
    └── mart_project_fund_health.sql   -- 健康度汇总
```

`int_project_fund_derived.sql` 核心逻辑：

```sql
-- intermediate/int_project_fund_derived.sql
WITH source AS (
    SELECT * FROM {{ ref('stg_project_fund') }}
)
SELECT
    *,
    total_fund - direct_ctrl AS reserve_indirect,
    ROUND(direct_spent * 100.0 / NULLIF(direct_ctrl, 0), 1) AS direct_rate,
    direct_ctrl - direct_spent AS direct_remain,
    direct_spent + indirect_spent AS total_spent,
    ROUND((direct_spent + indirect_spent) * 100.0 / NULLIF(total_fund, 0), 1) AS total_rate,
    -- 时间进度
    ROUND(
        GREATEST(LEAST(
            (EXTRACT(YEAR FROM AGE(CURRENT_DATE, cycle_start)) * 12
             + EXTRACT(MONTH FROM AGE(CURRENT_DATE, cycle_start))),
            (EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
             + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start)))
        ), 0) * 100.0
        / NULLIF(EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
                + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start)), 0),
    1) AS time_rate,
    -- 健康度
    CASE
        WHEN direct_spent > direct_ctrl THEN 'overrun'
        WHEN ABS(
            direct_spent * 100.0 / NULLIF(direct_ctrl, 0)
          - GREATEST(LEAST(
                (EXTRACT(YEAR FROM AGE(CURRENT_DATE, cycle_start)) * 12
                 + EXTRACT(MONTH FROM AGE(CURRENT_DATE, cycle_start))),
                (EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
                 + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start)))
            ), 0) * 100.0
            / NULLIF(EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
                    + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start)), 0)
        ) <= 10 THEN 'good'
        WHEN direct_spent * 100.0 / NULLIF(direct_ctrl, 0) >
             GREATEST(LEAST(
                 (EXTRACT(YEAR FROM AGE(CURRENT_DATE, cycle_start)) * 12
                  + EXTRACT(MONTH FROM AGE(CURRENT_DATE, cycle_start))),
                 (EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
                  + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start)))
             ), 0) * 100.0
             / NULLIF(EXTRACT(YEAR FROM AGE(cycle_end, cycle_start)) * 12
                     + EXTRACT(MONTH FROM AGE(cycle_end, cycle_start)), 0) + 10
        THEN 'fast'
        ELSE 'slow'
    END AS health
FROM source
```
