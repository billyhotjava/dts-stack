# 个人辅助余额大屏 — SQL 查询文档

> Decision Twins · 个人辅助余额大屏  
> 本文档对应大屏中所有数据组件的 SQL 查询语句及计算原理

---

## 数据表结构

假设个人辅助余额表名为 `aux_balance_personal`，字段定义如下：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | SERIAL | 主键 |
| `subject_code` | VARCHAR | 科目编号，如 `1122.01`、`2211.01` |
| `subject_name` | VARCHAR | 科目名称，如 `备用金`、`工资应付` |
| `dept_name` | VARCHAR | 部门名称 |
| `employee_name` | VARCHAR | 职工名称 |
| `balance` | NUMERIC(15,2) | 余额（单位：元）。正数=借方余额（应收/借款），负数=贷方余额（应付/代扣） |

### 借贷方向说明

| 科目前缀 | 类别 | 余额方向 | 业务含义 |
|---------|------|---------|---------|
| `1122.xx` | 其他应收款 | 正数（借方） | 职工借款、备用金、预付款等 |
| `2211.xx` | 应付职工薪酬 | 负数（贷方） | 工资应付、奖金应付、社保代扣等 |

---

## 公共筛选条件

```sql
WHERE 1=1
  AND (:employee_filter IS NULL OR employee_name = :employee_filter)
  AND (:dept_filter      IS NULL OR dept_name     = :dept_filter)
  AND (:search_text      IS NULL
       OR subject_name  LIKE '%' || :search_text || '%'
       OR subject_code  LIKE '%' || :search_text || '%'
       OR employee_name LIKE '%' || :search_text || '%')
```

> 下文所有 SQL 中的 `{WHERE_CLAUSE}` 均代表上述公共筛选条件。

---

## 一、KPI 指标卡片（5个）

### 1.1 聚合查询

```sql
SELECT
    -- 净余额 = 借方 - 贷方（即全部 balance 之和）
    SUM(balance)                                            AS net_balance,

    -- 借方合计（正数余额之和，代表应收/借款）
    SUM(CASE WHEN balance > 0 THEN balance ELSE 0 END)     AS debit_total,

    -- 贷方合计（负数余额的绝对值之和，代表应付/代扣）
    SUM(CASE WHEN balance < 0 THEN ABS(balance) ELSE 0 END) AS credit_total,

    -- 涉及职工数
    COUNT(DISTINCT employee_name)                           AS employee_count,

    -- 科目数量
    COUNT(DISTINCT subject_code)                            AS subject_count
FROM aux_balance_personal
{WHERE_CLAUSE};
```

### 1.2 各卡片取值说明

| 卡片名称 | 取值字段 | 计算原理 |
|---------|---------|---------|
| **净余额** | `net_balance` | 所有记录的 `balance` 直接求和。正数表示公司整体为净债权（职工欠公司），负数表示净债务（公司欠职工） |
| **借方合计** | `debit_total` | 仅对 `balance > 0` 的记录求和，代表所有职工的应收/借款总额 |
| **贷方合计** | `credit_total` | 仅对 `balance < 0` 的记录取绝对值后求和，代表所有应付职工薪酬总额 |
| **涉及职工** | `employee_count` | 对 `employee_name` 去重计数 |
| **科目数量** | `subject_count` | 对 `subject_code` 去重计数 |

---

## 二、按职工分布（横向条形图）

### 2.1 查询语句

```sql
SELECT
    employee_name              AS label,
    SUM(balance)               AS value
FROM aux_balance_personal
{WHERE_CLAUSE}
GROUP BY employee_name
ORDER BY value DESC;
```

### 2.2 原理说明

- 按职工汇总净余额（借方 - 贷方），正数在上，负数在下
- 正数（借方）用蓝色柱体，代表该职工欠公司的款项
- 负数（贷方）用绿色柱体，代表公司欠该职工的款项

---

## 三、按部门分布（环形图）

### 3.1 查询语句

```sql
SELECT
    dept_name                  AS label,
    SUM(ABS(balance))          AS value
FROM aux_balance_personal
{WHERE_CLAUSE}
GROUP BY dept_name
ORDER BY value DESC;
```

### 3.2 原理说明

- 使用 `ABS(balance)` 取绝对值，因为环形图展示的是各部门的资金往来规模，不区分借贷方向
- 每个扇区占比 = 该部门绝对金额 ÷ 所有部门绝对金额之和

---

## 四、按科目类别分布（百分比条形图）

### 4.1 查询语句

```sql
SELECT
    CASE
        WHEN subject_code LIKE '1122%' THEN '其他应收-借款'
        WHEN subject_code LIKE '2211%' THEN '应付职工薪酬'
        ELSE '其他'
    END                        AS label,
    SUM(ABS(balance))          AS value
FROM aux_balance_personal
{WHERE_CLAUSE}
GROUP BY label
ORDER BY value DESC;
```

### 4.2 原理说明

- `1122.xx` 科目归入"其他应收-借款"类（备用金、差旅费借款、采购预付款等）
- `2211.xx` 科目归入"应付职工薪酬"类（工资应付、奖金应付、社保代扣等）
- 使用绝对值汇总，百分比 = 该类别金额 ÷ 总金额 × 100%
- 前端显示为水平百分比条，直观对比借款类与薪酬类的规模占比

---

## 五、个人辅助余额明细表

### 5.1 查询语句

```sql
SELECT
    subject_code,
    subject_name,
    dept_name,
    employee_name,
    balance
FROM aux_balance_personal
{WHERE_CLAUSE}
ORDER BY employee_name, subject_code;
```

### 5.2 合计行

```sql
SELECT SUM(balance) AS total_balance
FROM aux_balance_personal
{WHERE_CLAUSE};
```

### 5.3 前端着色规则

| 条件 | 颜色 | 含义 |
|-----|------|------|
| `balance < 0` | 绿色 | 贷方余额（公司应付职工） |
| `balance > 50,000` | 红色 | 大额借款，需重点关注 |
| `balance > 10,000` | 橙色 | 较高借款，建议关注 |
| 其他 | 默认黑色 | 正常范围 |

---

## 六、借款余额 TOP 8（排行榜）

### 6.1 查询语句

```sql
SELECT
    employee_name              AS label,
    dept_name,
    SUM(balance)               AS value
FROM aux_balance_personal
{WHERE_CLAUSE}
GROUP BY employee_name, dept_name
HAVING SUM(balance) > 0          -- 仅取借方净余额为正的职工
ORDER BY value DESC
LIMIT 8;
```

### 6.2 原理说明

- `HAVING SUM(balance) > 0`：仅筛选净余额为正（即借方余额大于贷方余额）的职工
- 这些人是公司的净债务人，借款金额越大越需要关注
- 排行榜前 3 名用深色背景突出，金额 > 10万标红、> 3万标橙

---

## 七、合并 CTE 查询

```sql
WITH filtered AS (
    SELECT * FROM aux_balance_personal
    {WHERE_CLAUSE}
),
kpi AS (
    SELECT
        SUM(balance)                                             AS net_balance,
        SUM(CASE WHEN balance > 0 THEN balance ELSE 0 END)      AS debit_total,
        SUM(CASE WHEN balance < 0 THEN ABS(balance) ELSE 0 END) AS credit_total,
        COUNT(DISTINCT employee_name)                            AS employee_count,
        COUNT(DISTINCT subject_code)                             AS subject_count
    FROM filtered
),
by_employee AS (
    SELECT employee_name AS label, SUM(balance) AS value
    FROM filtered GROUP BY employee_name ORDER BY value DESC
),
by_dept AS (
    SELECT dept_name AS label, SUM(ABS(balance)) AS value
    FROM filtered GROUP BY dept_name ORDER BY value DESC
),
by_category AS (
    SELECT
        CASE WHEN subject_code LIKE '1122%' THEN '其他应收-借款'
             WHEN subject_code LIKE '2211%' THEN '应付职工薪酬'
             ELSE '其他' END AS label,
        SUM(ABS(balance)) AS value
    FROM filtered GROUP BY label ORDER BY value DESC
),
top_borrowers AS (
    SELECT employee_name AS label, dept_name, SUM(balance) AS value
    FROM filtered GROUP BY employee_name, dept_name
    HAVING SUM(balance) > 0 ORDER BY value DESC LIMIT 8
)
SELECT 'kpi' AS section, row_to_json(kpi.*) FROM kpi
UNION ALL SELECT 'by_employee', json_agg(by_employee.*) FROM by_employee
UNION ALL SELECT 'by_dept', json_agg(by_dept.*) FROM by_dept
UNION ALL SELECT 'by_category', json_agg(by_category.*) FROM by_category
UNION ALL SELECT 'top_borrowers', json_agg(top_borrowers.*) FROM top_borrowers;
```
