# 辅助余额大屏 — SQL 查询文档

> Decision Twins · 项目辅助余额大屏  
> 本文档对应大屏中所有数据组件的 SQL 查询语句及计算原理

---

## 数据表结构

假设辅助余额表名为 `aux_balance`，字段定义如下：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | SERIAL | 主键 |
| `subject_code` | VARCHAR | 科目编号，如 `5001.01` |
| `subject_name` | VARCHAR | 科目名称，如 `原材料-钢材` |
| `project_id` | VARCHAR | 项目编号，如 `PRJ-001` |
| `project_name` | VARCHAR | 项目名称 |
| `dept_name` | VARCHAR | 部门名称 |
| `contract_name` | VARCHAR | 合同名称（无合同时为 `—`） |
| `balance` | NUMERIC(15,2) | 余额（单位：元） |

---

## 公共筛选条件

大屏顶部有三个筛选器，所有查询共享同一套 WHERE 条件，确保筛选联动：

```sql
-- 筛选条件模板（所有查询的 WHERE 子句复用此逻辑）
WHERE 1=1
  AND (:project_filter IS NULL OR project_name = :project_filter)
  AND (:dept_filter    IS NULL OR dept_name    = :dept_filter)
  AND (:search_text    IS NULL
       OR subject_name  LIKE '%' || :search_text || '%'
       OR subject_code  LIKE '%' || :search_text || '%'
       OR contract_name LIKE '%' || :search_text || '%')
```

参数说明：
- `:project_filter` — 项目下拉筛选值，NULL 表示"全部项目"
- `:dept_filter` — 部门下拉筛选值，NULL 表示"全部部门"  
- `:search_text` — 搜索框输入的关键词，NULL 表示无搜索

> 下文所有 SQL 中的 `{WHERE_CLAUSE}` 均代表上述公共筛选条件。

---

## 一、KPI 指标卡片

大屏顶部展示 4 个 KPI 卡片，数据来源于同一条聚合查询：

### 1.1 KPI 聚合查询

```sql
SELECT
    SUM(balance)                                        AS total_balance,
    COUNT(DISTINCT project_id)                          AS project_count,
    COUNT(DISTINCT subject_code)                        AS subject_count,
    COUNT(DISTINCT CASE
        WHEN contract_name IS NOT NULL
         AND contract_name != '—'
        THEN contract_name
    END)                                                AS contract_count
FROM aux_balance
{WHERE_CLAUSE};
```

### 1.2 各卡片取值说明

| 卡片名称 | 取值字段 | 计算原理 |
|---------|---------|---------|
| **余额合计** | `total_balance` | 对所有满足筛选条件的记录，对 `balance` 字段求和。单位：元，前端转换为万元显示（÷10000） |
| **涉及项目** | `project_count` | 对 `project_id` 做去重计数（`COUNT(DISTINCT)`），得出筛选范围内涉及多少个不同项目 |
| **科目数量** | `subject_count` | 对 `subject_code` 做去重计数，得出涉及多少个不同的会计科目 |
| **合同数量** | `contract_count` | 对 `contract_name` 做去重计数，排除值为 `NULL` 或 `—` 的记录（表示无合同关联的科目） |

---

## 二、按项目分布（横向条形图）

### 2.1 查询语句

```sql
SELECT
    project_name                AS label,
    SUM(balance)                AS value
FROM aux_balance
{WHERE_CLAUSE}
GROUP BY project_name
ORDER BY value DESC;
```

### 2.2 原理说明

- **GROUP BY project_name**：将所有明细按项目聚合
- **SUM(balance)**：累加每个项目下所有科目的余额
- **ORDER BY value DESC**：按余额降序排列，条形图从上到下为金额由大到小
- 前端用每个项目的 `value` 占最大值的比例来计算条形长度

---

## 三、按部门分布（环形图）

### 3.1 查询语句

```sql
SELECT
    dept_name                   AS label,
    SUM(balance)                AS value
FROM aux_balance
{WHERE_CLAUSE}
GROUP BY dept_name
ORDER BY value DESC;
```

### 3.2 原理说明

- **GROUP BY dept_name**：将所有明细按部门聚合
- **SUM(balance)**：累加每个部门下所有科目的余额
- 环形图中每个扇区的弧度 = 该部门 value ÷ 所有部门 value 之和 × 360°
- 中心数字显示所有部门的总金额

---

## 四、按费用类别分布（矩形树图）

### 4.1 查询语句

```sql
SELECT
    CASE
        WHEN subject_code LIKE '5001%' THEN '原材料/设备'
        WHEN subject_code LIKE '5101%' THEN '外协/服务'
        WHEN subject_code LIKE '5201%' THEN '折旧'
        WHEN subject_code LIKE '5301%' THEN '检测试验'
        WHEN subject_code LIKE '5401%' THEN '设计咨询'
        WHEN subject_code LIKE '5501%' THEN '租赁'
        WHEN subject_code LIKE '5601%' THEN '培训'
        ELSE '其他'
    END                         AS label,
    SUM(balance)                AS value
FROM aux_balance
{WHERE_CLAUSE}
GROUP BY label
ORDER BY value DESC;
```

### 4.2 原理说明

- **CASE WHEN ... LIKE**：通过科目编号前缀将明细归入费用大类。编号 `5001.xx` 代表原材料/设备类，`5101.xx` 代表外协/服务类，以此类推
- **GROUP BY label**：按推导出的类别名聚合
- 矩形树图中每个矩形的宽度 = 该类别 value ÷ 总 value × 100%
- 面积越大表示该费用类别占总余额比重越高

### 4.3 替代方案：使用科目分类维度表

如果系统中维护了科目分类维度表（推荐），可以避免在 SQL 中硬编码分类规则：

```sql
-- 前提：存在 dim_subject_category 表
-- 字段：subject_code_prefix, category_name

SELECT
    COALESCE(sc.category_name, '其他')   AS label,
    SUM(ab.balance)                       AS value
FROM aux_balance ab
LEFT JOIN dim_subject_category sc
    ON ab.subject_code LIKE sc.subject_code_prefix || '%'
{WHERE_CLAUSE}
GROUP BY label
ORDER BY value DESC;
```

维度表示例数据：

| subject_code_prefix | category_name |
|--------------------|---------------|
| 5001 | 原材料/设备 |
| 5101 | 外协/服务 |
| 5201 | 折旧 |
| 5301 | 检测试验 |
| 5401 | 设计咨询 |
| 5501 | 租赁 |
| 5601 | 培训 |

---

## 五、辅助余额明细表

### 5.1 查询语句

```sql
SELECT
    subject_code,
    subject_name,
    project_name,
    dept_name,
    contract_name,
    balance
FROM aux_balance
{WHERE_CLAUSE}
ORDER BY project_name, subject_code;
```

### 5.2 合计行

```sql
-- 表尾合计行（与明细查询共享同一 WHERE 条件）
SELECT
    SUM(balance) AS total_balance
FROM aux_balance
{WHERE_CLAUSE};
```

### 5.3 前端着色规则

明细表中余额列根据金额大小自动变色，便于快速识别大额科目：

| 条件 | 颜色 | 含义 |
|-----|------|------|
| `balance > 3,000,000` | 红色 | 大额余额，需重点关注 |
| `balance > 1,000,000` | 橙色 | 较高余额，建议关注 |
| 其他 | 默认黑色 | 正常范围 |

---

## 六、合同金额 TOP 8（排行榜）

### 6.1 查询语句

```sql
SELECT
    contract_name               AS contract,
    project_name                AS project,
    SUM(balance)                AS balance
FROM aux_balance
{WHERE_CLAUSE}
  AND contract_name IS NOT NULL
  AND contract_name != '—'
GROUP BY contract_name, project_name
ORDER BY balance DESC
LIMIT 8;
```

### 6.2 原理说明

- **过滤无效合同**：排除 `contract_name` 为 NULL 或 `—` 的记录（表示该科目无合同关联）
- **GROUP BY contract_name, project_name**：按合同+项目组合聚合。同一合同可能关联多条科目明细，需要汇总
- **LIMIT 8**：只取前 8 名，用于排行榜展示
- 排行榜前 3 名用深色背景突出显示

---

## 七、查询优化建议

### 7.1 推荐索引

```sql
-- 基础查询索引
CREATE INDEX idx_aux_balance_project ON aux_balance(project_name);
CREATE INDEX idx_aux_balance_dept    ON aux_balance(dept_name);
CREATE INDEX idx_aux_balance_code    ON aux_balance(subject_code);

-- 搜索功能索引（如果使用 PostgreSQL 的 pg_trgm 扩展）
CREATE INDEX idx_aux_balance_search  ON aux_balance
  USING gin (subject_name gin_trgm_ops);
```

### 7.2 合并查询减少数据库往返

大屏加载时可以将 KPI + 三个图表的聚合查询合并为一次 CTE 查询：

```sql
WITH filtered AS (
    SELECT *
    FROM aux_balance
    {WHERE_CLAUSE}
),
-- KPI 指标
kpi AS (
    SELECT
        SUM(balance)                   AS total_balance,
        COUNT(DISTINCT project_id)     AS project_count,
        COUNT(DISTINCT subject_code)   AS subject_count,
        COUNT(DISTINCT CASE
            WHEN contract_name IS NOT NULL AND contract_name != '—'
            THEN contract_name END)    AS contract_count
    FROM filtered
),
-- 按项目分布
by_project AS (
    SELECT project_name AS label, SUM(balance) AS value
    FROM filtered
    GROUP BY project_name
    ORDER BY value DESC
),
-- 按部门分布
by_dept AS (
    SELECT dept_name AS label, SUM(balance) AS value
    FROM filtered
    GROUP BY dept_name
    ORDER BY value DESC
),
-- 按费用类别分布
by_category AS (
    SELECT
        CASE
            WHEN subject_code LIKE '5001%' THEN '原材料/设备'
            WHEN subject_code LIKE '5101%' THEN '外协/服务'
            WHEN subject_code LIKE '5201%' THEN '折旧'
            WHEN subject_code LIKE '5301%' THEN '检测试验'
            WHEN subject_code LIKE '5401%' THEN '设计咨询'
            WHEN subject_code LIKE '5501%' THEN '租赁'
            WHEN subject_code LIKE '5601%' THEN '培训'
            ELSE '其他'
        END AS label,
        SUM(balance) AS value
    FROM filtered
    GROUP BY label
    ORDER BY value DESC
),
-- 合同 TOP 8
top_contracts AS (
    SELECT
        contract_name AS contract,
        project_name  AS project,
        SUM(balance)  AS balance
    FROM filtered
    WHERE contract_name IS NOT NULL AND contract_name != '—'
    GROUP BY contract_name, project_name
    ORDER BY balance DESC
    LIMIT 8
)
-- 前端根据需要从各 CTE 中取数据
-- 实际使用时可拆分为多个 SELECT，或用 JSON 聚合一次返回
SELECT 'kpi' AS section, row_to_json(kpi.*) AS data FROM kpi
UNION ALL
SELECT 'by_project', json_agg(by_project.*) FROM by_project
UNION ALL
SELECT 'by_dept', json_agg(by_dept.*) FROM by_dept
UNION ALL
SELECT 'by_category', json_agg(by_category.*) FROM by_category
UNION ALL
SELECT 'top_contracts', json_agg(top_contracts.*) FROM top_contracts;
```

> 注意：以上合并查询适用于 PostgreSQL。如果使用 MySQL，CTE 语法类似但 `row_to_json` / `json_agg` 需替换为 `JSON_OBJECT` / `JSON_ARRAYAGG`。

---

## 八、dbt Model 建议

如果使用 dbt 管理数据转换，建议按如下分层组织：

```
models/
├── staging/
│   └── stg_aux_balance.sql          -- 清洗原始数据，标准化字段名
├── intermediate/
│   └── int_aux_balance_categorized.sql  -- 添加费用类别字段
└── marts/
    ├── mart_aux_balance_by_project.sql  -- 按项目聚合
    ├── mart_aux_balance_by_dept.sql     -- 按部门聚合
    ├── mart_aux_balance_by_category.sql -- 按费用类别聚合
    └── mart_aux_balance_kpi.sql         -- KPI 汇总指标
```

`int_aux_balance_categorized.sql` 示例：

```sql
-- intermediate/int_aux_balance_categorized.sql
SELECT
    *,
    CASE
        WHEN subject_code LIKE '5001%' THEN '原材料/设备'
        WHEN subject_code LIKE '5101%' THEN '外协/服务'
        WHEN subject_code LIKE '5201%' THEN '折旧'
        WHEN subject_code LIKE '5301%' THEN '检测试验'
        WHEN subject_code LIKE '5401%' THEN '设计咨询'
        WHEN subject_code LIKE '5501%' THEN '租赁'
        WHEN subject_code LIKE '5601%' THEN '培训'
        ELSE '其他'
    END AS expense_category
FROM {{ ref('stg_aux_balance') }}
```

这样费用分类逻辑只维护一处，所有下游 mart 直接引用 `expense_category` 字段。
