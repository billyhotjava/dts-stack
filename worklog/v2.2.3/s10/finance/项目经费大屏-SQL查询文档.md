# 项目经费大屏 — SQL 查询文档

> Decision Twins · 项目经费大屏
> 本文档对应大屏中所有数据组件的 SQL 查询语句及计算原理

---

## 数据表结构

项目经费表名为 `project_fund`，字段定义如下：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `project_id` | VARCHAR | 项目编号，如 `PRJ-001` |
| `cycle` | VARCHAR | 研制周期，如 `2026.01-2027.06` |
| `total_fund` | NUMERIC(15,2) | 总经费（万元） |
| `direct_ctrl` | NUMERIC(15,2) | 直接成本控制数（万元） |
| `reserve_indirect` | NUMERIC(15,2) | 预留间接费用和收益（万元） |
| `direct_rate` | NUMERIC(5,1) | 直接成本执行率（%） |
| `indirect_spent` | NUMERIC(15,2) | 间接费用支出和收益总额（万元） |

> **注意**：与旧版相比，新表没有项目名称、部门、项目经理、起止日期、直接成本支出金额字段。
> `预留间接费用和收益` 和 `直接成本执行率` 现在是原始字段（旧版为派生字段）。
> `直接成本支出金额` 需通过 `直接成本控制数 × 直接成本执行率 / 100` 反推。

---

## 一、派生字段计算视图

所有大屏组件共享同一组派生指标，建议创建视图或 CTE 统一计算：

```sql
CREATE VIEW v_project_fund AS
SELECT
    project_id,
    cycle,

    -- 原始字段
    total_fund,
    direct_ctrl,
    reserve_indirect,
    direct_rate,
    indirect_spent,

    -- ═══ 派生字段 ═══

    -- 直接成本支出 = 直接成本控制数 × 直接成本执行率 / 100
    ROUND(direct_ctrl * direct_rate / 100, 2)
        AS direct_spent,

    -- 总支出 = 直接成本支出 + 间接费用支出
    ROUND(direct_ctrl * direct_rate / 100, 2) + indirect_spent
        AS total_spent,

    -- 总经费执行率(%) = 总支出 / 总经费 × 100
    CASE WHEN total_fund > 0
         THEN ROUND(
             (ROUND(direct_ctrl * direct_rate / 100, 2) + indirect_spent)
             * 100.0 / total_fund, 1)
         ELSE 0 END
        AS total_rate,

    -- 间接费用执行率(%) = 间接费用支出 / 预留间接费用 × 100
    CASE WHEN reserve_indirect > 0
         THEN ROUND(indirect_spent * 100.0 / reserve_indirect, 1)
         ELSE 0 END
        AS indirect_rate

FROM project_fund;
```

### 派生字段汇总表

| 派生字段 | 计算公式 | 业务含义 |
|---------|---------|---------|
| **直接成本支出** | 直接成本控制数 × 直接成本执行率 / 100 | 已发生的直接成本（反推） |
| **总支出** | 直接成本支出 + 间接费用支出和收益总额 | 项目实际发生的全部费用 |
| **总经费执行率** | 总支出 / 总经费 × 100% | 全口径经费消耗程度 |
| **间接费用执行率** | 间接费用支出 / 预留间接费用和收益 × 100% | 间接费用消耗程度 |

---

## 二、KPI 指标卡片（5个）

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
          / NULLIF(SUM(total_fund), 0), 1)       AS overall_total_rate
FROM v_project_fund;
```

### 2.2 各卡片说明

| 卡片 | 字段 | 说明 |
|------|------|------|
| **总经费** | `sum_total_fund` | 全部项目总经费之和 |
| **直接成本控制数** | `sum_direct_ctrl` | 全部项目直接成本控制数之和 |
| **直接成本已支出** | `sum_direct_spent` | 全部项目直接成本支出之和（派生） |
| **直接成本执行率** | `overall_direct_rate` | 汇总后的整体执行率 |
| **总经费执行率** | `overall_total_rate` | 汇总后的整体总执行率 |

> **注意**：整体执行率 = SUM(支出) / SUM(控制数)，而不是 AVG(各项目执行率)，这样大项目的权重更高，更能反映真实情况。

---

## 三、经费结构分布（堆叠柱状图）

### 3.1 查询语句

```sql
SELECT
    project_id,
    direct_spent,
    indirect_spent,
    GREATEST(total_fund - direct_spent - indirect_spent, 0) AS remaining
FROM v_project_fund
ORDER BY total_fund DESC;
```

### 3.2 原理说明

每根柱子分三段：
- **直接成本支出**（蓝色）：反推的直接成本 = 控制数 × 执行率 / 100
- **间接费用支出**（紫色）：已发生的间接费用
- **剩余**（浅灰）：总经费 - 直接支出 - 间接支出，即尚未消耗的经费

X 轴使用项目编号作为标签。

---

## 四、仪表盘（Gauge）

### 4.1 直接成本执行率仪表盘

```sql
SELECT
    ROUND(SUM(direct_spent) * 100.0
          / NULLIF(SUM(direct_ctrl), 0), 1) AS overall_direct_rate
FROM v_project_fund;
```

### 4.2 总经费执行率仪表盘

```sql
SELECT
    ROUND(SUM(total_spent) * 100.0
          / NULLIF(SUM(total_fund), 0), 1) AS overall_total_rate
FROM v_project_fund;
```

---

## 五、项目经费明细表

### 5.1 查询语句

```sql
SELECT
    project_id,
    cycle,
    total_fund,
    direct_ctrl,
    reserve_indirect,
    direct_rate,
    indirect_spent,
    total_spent,
    total_rate
FROM v_project_fund
ORDER BY project_id;
```

### 5.2 合计行

```sql
SELECT
    SUM(total_fund)            AS total_fund,
    SUM(direct_ctrl)           AS direct_ctrl,
    SUM(reserve_indirect)      AS reserve_indirect,
    ROUND(SUM(direct_spent) * 100.0 / NULLIF(SUM(direct_ctrl), 0), 1)   AS direct_rate,
    SUM(indirect_spent)        AS indirect_spent,
    SUM(total_spent)           AS total_spent,
    ROUND(SUM(total_spent) * 100.0 / NULLIF(SUM(total_fund), 0), 1)     AS total_rate
FROM v_project_fund;
```

> **合计行的执行率**是重新用汇总数据计算的，不是各项目执行率的简单平均。

### 5.3 前端着色规则

| 字段 | 条件 | 颜色 | 含义 |
|-----|------|------|------|
| 直接成本执行率 | `> 100%` | 红色 | 超支 |
| 直接成本执行率 | `> 80%` | 橙色 | 接近控制上限 |
| 总执行率 | `> 90%` | 橙色 | 接近经费用尽 |

---

## 六、合并 CTE 查询

```sql
WITH base AS (
    SELECT * FROM v_project_fund
),
kpi AS (
    SELECT
        SUM(total_fund)     AS sum_total_fund,
        SUM(direct_ctrl)    AS sum_direct_ctrl,
        SUM(direct_spent)   AS sum_direct_spent,
        SUM(total_spent)    AS sum_total_spent,
        SUM(indirect_spent) AS sum_indirect_spent,
        ROUND(SUM(direct_spent) * 100.0 / NULLIF(SUM(direct_ctrl), 0), 1) AS overall_direct_rate,
        ROUND(SUM(total_spent) * 100.0 / NULLIF(SUM(total_fund), 0), 1)   AS overall_total_rate
    FROM base
),
stacked AS (
    SELECT project_id, direct_spent, indirect_spent,
           GREATEST(total_fund - direct_spent - indirect_spent, 0) AS remaining
    FROM base ORDER BY total_fund DESC
)
SELECT 'kpi'       AS section, row_to_json(kpi.*)               FROM kpi
UNION ALL
SELECT 'stacked',   json_agg(stacked.*)                         FROM stacked
UNION ALL
SELECT 'detail',    json_agg(base.* ORDER BY project_id)        FROM base;
```

---

## 七、dbt Model 建议

```
models/
├── staging/
│   └── stg_project_fund.sql            -- 清洗原始字段，标准化周期格式
├── intermediate/
│   └── int_project_fund_derived.sql    -- 计算全部派生字段（对应 v_project_fund 视图）
└── marts/
    ├── mart_project_fund_kpi.sql       -- KPI 汇总
    └── mart_project_fund_structure.sql -- 经费结构（堆叠图数据）
```

`int_project_fund_derived.sql` 核心逻辑：

```sql
-- intermediate/int_project_fund_derived.sql
WITH source AS (
    SELECT * FROM {{ ref('stg_project_fund') }}
)
SELECT
    project_id,
    cycle,
    total_fund,
    direct_ctrl,
    reserve_indirect,
    direct_rate,
    indirect_spent,

    -- 派生字段
    ROUND(direct_ctrl * direct_rate / 100, 2) AS direct_spent,
    ROUND(direct_ctrl * direct_rate / 100, 2) + indirect_spent AS total_spent,
    ROUND(
        (ROUND(direct_ctrl * direct_rate / 100, 2) + indirect_spent)
        * 100.0 / NULLIF(total_fund, 0),
    1) AS total_rate,
    ROUND(indirect_spent * 100.0 / NULLIF(reserve_indirect, 0), 1) AS indirect_rate
FROM source
```
