# 自有资金大屏 — SQL 查询文档

> Decision Twins · 自有资金大屏  
> 本文档对应大屏中所有数据组件的 SQL 查询语句及计算原理

---

## 数据表结构

假设自有资金表名为 `own_fund`，字段定义如下：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | SERIAL | 主键 |
| `period` | VARCHAR | 年度期间，如 `2026年初`、`2026年预计增加`、`2026年预计使用`、`2026年余额` |
| `period_year` | INT | 年度，如 `2026`、`2027`（从 period 提取，便于筛选） |
| `period_type` | VARCHAR | 期间类型：`opening`（年初）、`increase`（预计增加）、`usage`（预计使用）、`balance`（余额） |
| `career_fund` | NUMERIC(15,2) | 事业基金（万元） |
| `career_note` | VARCHAR | 事业基金备注 |
| `deprec_fund` | NUMERIC(15,2) | 折旧基金（万元） |
| `deprec_note` | VARCHAR | 折旧基金备注 |
| `welfare_fund` | NUMERIC(15,2) | 职工福利基金（万元） |
| `safety_fund` | NUMERIC(15,2) | 安全生产基金（万元） |

### 数据逻辑关系

每个年度有且仅有 4 行数据，逻辑关系如下：

```
年初余额 + 预计增加 - 预计使用 = 年末余额
下一年年初 = 上一年年末余额
```

---

## 一、KPI 指标卡片（6个）

### 1.1 单年度基础数据查询

```sql
-- 获取指定年度的四行数据
SELECT
    period_type,
    career_fund + deprec_fund + welfare_fund + safety_fund AS total
FROM own_fund
WHERE period_year = :selected_year;
```

### 1.2 KPI 计算查询

```sql
WITH year_data AS (
    SELECT
        period_type,
        career_fund,
        deprec_fund,
        welfare_fund,
        safety_fund,
        career_fund + deprec_fund + welfare_fund + safety_fund AS total
    FROM own_fund
    WHERE period_year = :selected_year
)
SELECT
    -- 年初合计
    (SELECT total FROM year_data WHERE period_type = 'opening')    AS opening_total,

    -- 预计增加
    (SELECT total FROM year_data WHERE period_type = 'increase')   AS increase_total,

    -- 预计使用
    (SELECT total FROM year_data WHERE period_type = 'usage')      AS usage_total,

    -- 年末余额
    (SELECT total FROM year_data WHERE period_type = 'balance')    AS balance_total,

    -- 资金使用率
    (SELECT total FROM year_data WHERE period_type = 'usage') * 100.0
    / NULLIF(
        (SELECT total FROM year_data WHERE period_type = 'opening')
      + (SELECT total FROM year_data WHERE period_type = 'increase'),
      0
    )                                                              AS usage_rate,

    -- 余额增长率
    ((SELECT total FROM year_data WHERE period_type = 'balance')
   - (SELECT total FROM year_data WHERE period_type = 'opening')) * 100.0
    / NULLIF(
        (SELECT total FROM year_data WHERE period_type = 'opening'),
      0
    )                                                              AS growth_rate;
```

### 1.3 各卡片说明

| 卡片名称 | 计算公式 | 业务含义 |
|---------|---------|---------|
| **年初合计** | 四类基金年初值之和 | 年度起始时的自有资金总规模 |
| **预计增加** | 四类基金预计增加值之和 | 本年度预计新增的资金（拨付、计提、投资收益等） |
| **预计使用** | 四类基金预计使用值之和 | 本年度预计消耗的资金（各类支出） |
| **年末余额** | 年初 + 预计增加 − 预计使用 | 年度结束时的预计资金规模 |
| **资金使用率** | 预计使用 ÷（年初 + 预计增加）× 100% | 衡量资金消耗强度，越高说明资金使用越充分，但过高（>80%）需警惕资金紧张 |
| **余额增长率** | （年末余额 − 年初）÷ 年初 × 100% | 正值=资金规模扩大，负值=资金净消耗 |

---

## 二、年末余额构成（环形图）

### 2.1 查询语句

```sql
SELECT
    '事业基金'     AS label, career_fund   AS value FROM own_fund
    WHERE period_year = :selected_year AND period_type = 'balance'
UNION ALL SELECT
    '折旧基金',          deprec_fund
    FROM own_fund WHERE period_year = :selected_year AND period_type = 'balance'
UNION ALL SELECT
    '职工福利基金',      welfare_fund
    FROM own_fund WHERE period_year = :selected_year AND period_type = 'balance'
UNION ALL SELECT
    '安全生产基金',      safety_fund
    FROM own_fund WHERE period_year = :selected_year AND period_type = 'balance';
```

更简洁的写法（行转列）：

```sql
SELECT label, value FROM (
    SELECT
        career_fund  AS "事业基金",
        deprec_fund  AS "折旧基金",
        welfare_fund AS "职工福利基金",
        safety_fund  AS "安全生产基金"
    FROM own_fund
    WHERE period_year = :selected_year AND period_type = 'balance'
) t
CROSS JOIN LATERAL (
    VALUES ('事业基金', t."事业基金"),
           ('折旧基金', t."折旧基金"),
           ('职工福利基金', t."职工福利基金"),
           ('安全生产基金', t."安全生产基金")
) AS v(label, value)
ORDER BY value DESC;
```

### 2.2 原理说明

- 取指定年度的"余额"行，将四个基金字段拆为四行
- 环形图每个扇区 = 该基金余额 ÷ 四类基金余额合计 × 360°

---

## 三、资金流动瀑布图

### 3.1 查询语句

```sql
SELECT
    period_type,
    career_fund + deprec_fund + welfare_fund + safety_fund AS total
FROM own_fund
WHERE period_year = :selected_year
ORDER BY
    CASE period_type
        WHEN 'opening'  THEN 1
        WHEN 'increase' THEN 2
        WHEN 'usage'    THEN 3
        WHEN 'balance'  THEN 4
    END;
```

### 3.2 原理说明

瀑布图展示资金的"流入—流出"过程：

```
[年初] ──(+增加)──> [年初+增加] ──(-使用)──> [年末余额]
```

- 第一根柱：年初合计（基底为0）
- 第二根柱：预计增加（堆叠在年初之上，绿色）
- 第三根柱：预计使用（从高点向下扣减，红色）
- 第四根柱：年末余额（最终结果）

ECharts 实现时用透明柱体做基底偏移，视觉上形成瀑布递进效果。

---

## 四、年度余额对比（分组柱状图）

### 4.1 查询语句

```sql
SELECT
    period_year,
    career_fund,
    deprec_fund,
    welfare_fund,
    safety_fund
FROM own_fund
WHERE period_type = 'balance'
  AND period_year IN (2026, 2027)
ORDER BY period_year;
```

### 4.2 原理说明

- 取 2026 和 2027 两年的余额行
- 每个基金类型显示两根并排柱子（浅色=2026，深色=2027）
- 直观对比各基金的年度增减变化

---

## 五、单基金瀑布图（4个小图）

### 5.1 查询语句

```sql
-- 以事业基金为例，其他三个基金同理替换字段名
SELECT
    period_type,
    career_fund AS value
FROM own_fund
WHERE period_year = :selected_year
ORDER BY
    CASE period_type
        WHEN 'opening'  THEN 1
        WHEN 'increase' THEN 2
        WHEN 'usage'    THEN 3
        WHEN 'balance'  THEN 4
    END;
```

### 5.2 一次查出全部四类基金

```sql
SELECT
    period_type,
    career_fund  AS career,
    deprec_fund  AS deprec,
    welfare_fund AS welfare,
    safety_fund  AS safety
FROM own_fund
WHERE period_year = :selected_year
ORDER BY
    CASE period_type
        WHEN 'opening'  THEN 1
        WHEN 'increase' THEN 2
        WHEN 'usage'    THEN 3
        WHEN 'balance'  THEN 4
    END;
```

前端取到4行数据后，分别喂给4个小瀑布图组件。

---

## 六、仪表盘（Gauge）

### 6.1 资金使用率仪表盘

```sql
SELECT
    ROUND(
        (SELECT career_fund + deprec_fund + welfare_fund + safety_fund
         FROM own_fund WHERE period_year = :selected_year AND period_type = 'usage')
        * 100.0
        / NULLIF(
            (SELECT career_fund + deprec_fund + welfare_fund + safety_fund
             FROM own_fund WHERE period_year = :selected_year AND period_type = 'opening')
          + (SELECT career_fund + deprec_fund + welfare_fund + safety_fund
             FROM own_fund WHERE period_year = :selected_year AND period_type = 'increase'),
          0),
    1) AS usage_rate;
```

**公式**：预计使用合计 ÷（年初合计 + 预计增加合计）× 100%

**判定**：> 80% 显示橙色警告色，表示资金消耗偏高。

### 6.2 余额增长率仪表盘

```sql
SELECT
    ROUND(
        ((SELECT career_fund + deprec_fund + welfare_fund + safety_fund
          FROM own_fund WHERE period_year = :selected_year AND period_type = 'balance')
       - (SELECT career_fund + deprec_fund + welfare_fund + safety_fund
          FROM own_fund WHERE period_year = :selected_year AND period_type = 'opening'))
        * 100.0
        / NULLIF(
            (SELECT career_fund + deprec_fund + welfare_fund + safety_fund
             FROM own_fund WHERE period_year = :selected_year AND period_type = 'opening'),
          0),
    1) AS growth_rate;
```

**公式**：（年末余额合计 − 年初合计）÷ 年初合计 × 100%

**判定**：正值=绿色（资金增长），负值=红色（资金缩减）。

---

## 七、自有资金明细表

### 7.1 查询语句

```sql
SELECT
    period,
    period_type,
    career_fund,
    career_note,
    deprec_fund,
    deprec_note,
    welfare_fund,
    safety_fund,
    career_fund + deprec_fund + welfare_fund + safety_fund AS total
FROM own_fund
ORDER BY
    period_year,
    CASE period_type
        WHEN 'opening'  THEN 1
        WHEN 'increase' THEN 2
        WHEN 'usage'    THEN 3
        WHEN 'balance'  THEN 4
    END;
```

### 7.2 前端显示规则

| 行类型 | 背景色 | 金额前缀 | 字重 |
|-------|--------|---------|------|
| `opening`（年初） | 默认白色 | 无 | 正常 |
| `increase`（预计增加） | 默认白色 | 无 | 正常 |
| `usage`（预计使用） | 默认白色 | `-` 前缀，红色字体 | 正常 |
| `balance`（余额） | 浅蓝高亮 | 无 | 加粗，合计列蓝色 |

---

## 八、数据校验 SQL

用于校验数据一致性，可在 dbt tests 或数据质量检查中使用：

### 8.1 余额 = 年初 + 增加 - 使用

```sql
-- 校验每个年度的余额行是否等于 年初 + 增加 - 使用
WITH checks AS (
    SELECT
        period_year,
        SUM(CASE WHEN period_type = 'opening'  THEN career_fund ELSE 0 END)
      + SUM(CASE WHEN period_type = 'increase' THEN career_fund ELSE 0 END)
      - SUM(CASE WHEN period_type = 'usage'    THEN career_fund ELSE 0 END)
        AS calc_career_balance,
        SUM(CASE WHEN period_type = 'balance'  THEN career_fund ELSE 0 END)
        AS actual_career_balance
    FROM own_fund
    GROUP BY period_year
)
SELECT *
FROM checks
WHERE calc_career_balance != actual_career_balance;
-- 结果应为空，否则数据不一致
```

### 8.2 年度衔接：下年年初 = 上年余额

```sql
-- 校验 2027年初 = 2026年余额
SELECT
    a.career_fund  AS balance_2026_career,
    b.career_fund  AS opening_2027_career,
    a.career_fund - b.career_fund AS diff
FROM own_fund a, own_fund b
WHERE a.period = '2026年余额'
  AND b.period = '2027年初'
  AND a.career_fund != b.career_fund;
-- 结果应为空
```

---

## 九、合并 CTE 查询（一次请求获取全部数据）

```sql
WITH all_data AS (
    SELECT *, career_fund + deprec_fund + welfare_fund + safety_fund AS total
    FROM own_fund
),
year_data AS (
    SELECT * FROM all_data WHERE period_year = :selected_year
),
kpi AS (
    SELECT
        MAX(CASE WHEN period_type = 'opening'  THEN total END) AS opening_total,
        MAX(CASE WHEN period_type = 'increase' THEN total END) AS increase_total,
        MAX(CASE WHEN period_type = 'usage'    THEN total END) AS usage_total,
        MAX(CASE WHEN period_type = 'balance'  THEN total END) AS balance_total
    FROM year_data
),
kpi_derived AS (
    SELECT
        *,
        ROUND(usage_total * 100.0 / NULLIF(opening_total + increase_total, 0), 1) AS usage_rate,
        ROUND((balance_total - opening_total) * 100.0 / NULLIF(opening_total, 0), 1) AS growth_rate
    FROM kpi
),
donut AS (
    SELECT career_fund, deprec_fund, welfare_fund, safety_fund
    FROM year_data WHERE period_type = 'balance'
),
compare AS (
    SELECT period_year, career_fund, deprec_fund, welfare_fund, safety_fund
    FROM all_data WHERE period_type = 'balance' AND period_year IN (2026, 2027)
)
SELECT 'kpi'     AS section, row_to_json(kpi_derived.*)  AS data FROM kpi_derived
UNION ALL
SELECT 'donut',   row_to_json(donut.*)                          FROM donut
UNION ALL
SELECT 'waterfall', json_agg(year_data.* ORDER BY
    CASE period_type WHEN 'opening' THEN 1 WHEN 'increase' THEN 2
    WHEN 'usage' THEN 3 WHEN 'balance' THEN 4 END)             FROM year_data
UNION ALL
SELECT 'compare', json_agg(compare.* ORDER BY period_year)      FROM compare;
```
