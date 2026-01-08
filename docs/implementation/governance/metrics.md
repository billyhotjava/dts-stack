## 指标口径与SQL（基于分层模型）

本文把 `docs/implementation/governance/metrics.xlsx` 中的指标落到可执行的 SQL 口径（以 Hive/SparkSQL 风格为例）。  
约定：
- 统计日期参数：`${dt}`（`yyyy-MM-dd`），统计月份：`${stat_month}`（`yyyy-MM`）
- DWS/DWD 表结构以 `docs/implementation/governance/dg.md` 为准
- 若当前ODS缺少关键数据源，SQL 将给出“可落地的替代口径”或“待补采字段/表”的占位实现

---

## 1. 库存管理（INV）

### 1.1 库存资金占用率
定义：`库存资金总价值 / 科研经费总额`

SQL（以日快照为准）：
```sql
WITH inv AS (
  SELECT
    dt,
    SUM(stock_amt) AS inventory_amt
  FROM dws_inv_stock_snapshot_di
  WHERE dt = '${dt}'
),
fund AS (
  SELECT
    dt,
    SUM(approved_budget_amt) AS research_fund_amt
  FROM dws_proj_budget_exec_di
  WHERE dt = '${dt}'
)
SELECT
  inv.dt,
  inv.inventory_amt,
  fund.research_fund_amt,
  inv.inventory_amt / NULLIF(fund.research_fund_amt, 0) AS inventory_fund_occupancy_rate
FROM inv
JOIN fund ON inv.dt = fund.dt;
```

---

### 1.2 项目物料供应及时率
Excel口径：`未因缺货导致实验延迟的课题数 / 总项目数`

当前ODS缺口：缺货事件/延期原因未在 `dg.docx` 中出现。  
可审计替代口径（建议评审确认）：把“缺货导致延迟”替换为“项目内存在采购需求且到货/入库晚于需求日期超过阈值 N 天”。

SQL（替代口径示例，按项目维度）：
```sql
-- N：允许延迟阈值（天），可配置
WITH base AS (
  SELECT
    dt,
    project_id,
    MAX(CASE WHEN delay_days > ${N} THEN 1 ELSE 0 END) AS has_supply_delay
  FROM dwd_pur_wide_order_lifecycle_line
  WHERE dt = '${dt}'
  GROUP BY dt, project_id
)
SELECT
  dt,
  SUM(CASE WHEN has_supply_delay = 0 THEN 1 ELSE 0 END) / NULLIF(COUNT(1), 0) AS project_material_supply_ontime_rate
FROM base
GROUP BY dt;
```

---

### 1.3 库存物资闲置率
定义：`超3/6/9/12月未领用的库存物资价值 / 总库存价值`

SQL（以 3 个月为例；6/9/12 同理）：
```sql
WITH snap AS (
  SELECT
    dt,
    material_id,
    warehouse_id,
    batch_id,
    stock_amt,
    last_out_date
  FROM dws_inv_stock_snapshot_di
  WHERE dt = '${dt}' AND stock_amt > 0
),
agg AS (
  SELECT
    dt,
    SUM(stock_amt) AS total_stock_amt,
    SUM(CASE WHEN last_out_date IS NULL OR last_out_date <= ADD_MONTHS(TO_DATE(dt), -3) THEN stock_amt ELSE 0 END) AS idle_3m_stock_amt
  FROM snap
  GROUP BY dt
)
SELECT
  dt,
  idle_3m_stock_amt / NULLIF(total_stock_amt, 0) AS idle_stock_rate_3m
FROM agg;
```

---

### 1.4 库存资产结构（旭日图数据集）
定义：按“库存总资产 → 一级分类 → 二/三级分类”下钻展示。

SQL（输出分类层级 + 金额）：
```sql
SELECT
  s.dt,
  mc1.material_class_id   AS lvl1_class_id,
  mc1.material_class_name AS lvl1_class_name,
  mc2.material_class_id   AS lvl2_class_id,
  mc2.material_class_name AS lvl2_class_name,
  SUM(s.stock_amt) AS stock_amt
FROM dws_inv_stock_snapshot_di s
JOIN dwd_inv_master_material m ON s.material_id = m.material_id
JOIN dwd_inv_master_material_class mc2 ON m.material_class_id = mc2.material_class_id
LEFT JOIN dwd_inv_master_material_class mc1 ON mc2.parent_class_id = mc1.material_class_id
WHERE s.dt = '${dt}'
GROUP BY
  s.dt,
  mc1.material_class_id, mc1.material_class_name,
  mc2.material_class_id, mc2.material_class_name;
```

---

### 1.5 库存健康度诊断（气泡图数据集）
Excel口径：X=平均库存金额，Y=库存周转天数，Size=SKU数，Color=物料分类

SQL（按分类输出）：
```sql
WITH daily AS (
  SELECT
    dt,
    material_class_id,
    SUM(stock_amt) AS stock_amt,
    COUNT(DISTINCT material_id) AS sku_cnt
  FROM (
    SELECT
      s.dt,
      s.material_id,
      s.stock_amt,
      m.material_class_id
    FROM dws_inv_stock_snapshot_di s
    JOIN dwd_inv_master_material m ON s.material_id = m.material_id
    WHERE s.dt BETWEEN CONCAT('${stat_month}', '-01') AND LAST_DAY(CONCAT('${stat_month}', '-01'))
  ) t
  GROUP BY dt, material_class_id
),
avg_inv AS (
  SELECT
    material_class_id,
    AVG(stock_amt) AS avg_stock_amt,
    AVG(sku_cnt) AS avg_sku_cnt
  FROM daily
  GROUP BY material_class_id
),
turnover AS (
  SELECT
    m.material_class_id,
    SUM(f.out_amt) AS out_amt
  FROM dwd_inv_fact_stock_flow f
  JOIN dwd_inv_master_material m ON f.material_id = m.material_id
  WHERE f.dt BETWEEN CONCAT('${stat_month}', '-01') AND LAST_DAY(CONCAT('${stat_month}', '-01'))
  GROUP BY m.material_class_id
)
SELECT
  a.material_class_id,
  a.avg_stock_amt AS x_avg_stock_amt,
  (30 / NULLIF(t.out_amt / NULLIF(a.avg_stock_amt, 0), 0)) AS y_turnover_days,
  a.avg_sku_cnt AS bubble_size_sku_cnt
FROM avg_inv a
LEFT JOIN turnover t ON a.material_class_id = t.material_class_id;
```

---

### 1.6 库存龄分布分析（热力图/堆叠柱状图数据集）
定义：按“库存存放时长分桶（0-30/31-90/91-180/180+）”输出金额或数量。

SQL（按物料分类 + 年龄桶输出金额）：
```sql
SELECT
  s.dt,
  m.material_class_id,
  CASE
    WHEN s.age_days BETWEEN 0 AND 30 THEN '0-30'
    WHEN s.age_days BETWEEN 31 AND 90 THEN '31-90'
    WHEN s.age_days BETWEEN 91 AND 180 THEN '91-180'
    ELSE '180+'
  END AS age_bucket,
  SUM(s.stock_amt) AS stock_amt
FROM dws_inv_stock_snapshot_di s
JOIN dwd_inv_master_material m ON s.material_id = m.material_id
WHERE s.dt = '${dt}' AND s.stock_amt > 0
GROUP BY s.dt, m.material_class_id,
  CASE
    WHEN s.age_days BETWEEN 0 AND 30 THEN '0-30'
    WHEN s.age_days BETWEEN 31 AND 90 THEN '31-90'
    WHEN s.age_days BETWEEN 91 AND 180 THEN '91-180'
    ELSE '180+'
  END;
```

---

### 1.7 库存周转率
Excel口径：`(销售成本 or 出库物料价值) / 平均库存`

SQL（按月，出库价值取库存出库金额，平均库存取当月日均库存金额）：
```sql
WITH out_m AS (
  SELECT
    '${stat_month}' AS stat_month,
    SUM(out_amt) AS out_amt
  FROM dwd_inv_fact_stock_flow
  WHERE dt BETWEEN CONCAT('${stat_month}', '-01') AND LAST_DAY(CONCAT('${stat_month}', '-01'))
),
avg_inv_m AS (
  SELECT
    '${stat_month}' AS stat_month,
    AVG(total_stock_amt) AS avg_stock_amt
  FROM (
    SELECT
      dt,
      SUM(stock_amt) AS total_stock_amt
    FROM dws_inv_stock_snapshot_di
    WHERE dt BETWEEN CONCAT('${stat_month}', '-01') AND LAST_DAY(CONCAT('${stat_month}', '-01'))
    GROUP BY dt
  ) d
)
SELECT
  o.stat_month,
  o.out_amt / NULLIF(a.avg_stock_amt, 0) AS inventory_turnover_rate
FROM out_m o
JOIN avg_inv_m a ON o.stat_month = a.stat_month;
```

---

### 1.8 紧缺物料数量
Excel口径：`低于安全库存物料数量 + 关键/采购周期长物料数量`

当前ODS缺口：安全库存阈值、关键标记、采购周期阈值需要配置或补采。  
建议依赖 `dwd_inv_master_material_policy`。

SQL（示例：低于安全库存的SKU数 + 关键SKU数）：
```sql
WITH snap AS (
  SELECT dt, material_id, warehouse_id, SUM(stock_qty) AS stock_qty
  FROM dws_inv_stock_snapshot_di
  WHERE dt = '${dt}'
  GROUP BY dt, material_id, warehouse_id
),
pol AS (
  SELECT material_id, warehouse_id, safety_stock_qty, is_critical
  FROM dwd_inv_master_material_policy
)
SELECT
  '${dt}' AS dt,
  COUNT(DISTINCT CASE WHEN s.stock_qty < p.safety_stock_qty THEN s.material_id END)
  + COUNT(DISTINCT CASE WHEN p.is_critical = 1 THEN p.material_id END) AS shortage_material_cnt
FROM snap s
JOIN pol p ON s.material_id = p.material_id AND s.warehouse_id = p.warehouse_id;
```

---

### 1.9 效期风险指数
定义：`临期(X个月内)物资品种数 / 总品种数`

SQL（X=3 示例）：
```sql
WITH base AS (
  SELECT
    dt,
    material_id,
    expiry_date
  FROM dws_inv_stock_snapshot_di
  WHERE dt = '${dt}' AND stock_qty > 0
),
agg AS (
  SELECT
    dt,
    COUNT(DISTINCT material_id) AS total_sku_cnt,
    COUNT(DISTINCT CASE
      WHEN expiry_date IS NOT NULL
       AND TO_DATE(expiry_date) <= ADD_MONTHS(TO_DATE(dt), 3)
      THEN material_id END) AS risk_sku_cnt
  FROM base
  GROUP BY dt
)
SELECT
  dt,
  risk_sku_cnt / NULLIF(total_sku_cnt, 0) AS expiry_risk_index
FROM agg;
```

---

### 1.10 盘点差异率
Excel口径：`(账面总价值 - 盘点总价值) / 账面总价值`

当前ODS缺口：盘点单/盘点结果明细未在 `dg.docx` 中出现。  
占位实现（需先落地盘点事实 `dwd_inv_fact_stocktake_line`）：
```sql
WITH book AS (
  SELECT dt, SUM(stock_amt) AS book_amt
  FROM dws_inv_stock_snapshot_di
  WHERE dt = '${dt}'
  GROUP BY dt
),
take AS (
  SELECT dt, SUM(take_amt) AS take_amt
  FROM dwd_inv_fact_stocktake_line
  WHERE dt = '${dt}'
  GROUP BY dt
)
SELECT
  b.dt,
  (b.book_amt - t.take_amt) / NULLIF(b.book_amt, 0) AS stocktake_diff_rate
FROM book b
JOIN take t ON b.dt = t.dt;
```

---

### 1.11 库存关联分析（趋势数据集）
定义：同一时间轴展示库存金额、周转率等趋势。

SQL（输出月度趋势：库存均值 + 周转率）：
```sql
WITH inv_m AS (
  SELECT
    '${stat_month}' AS stat_month,
    AVG(total_stock_amt) AS avg_stock_amt
  FROM (
    SELECT dt, SUM(stock_amt) AS total_stock_amt
    FROM dws_inv_stock_snapshot_di
    WHERE dt BETWEEN CONCAT('${stat_month}', '-01') AND LAST_DAY(CONCAT('${stat_month}', '-01'))
    GROUP BY dt
  ) d
),
turn_m AS (
  SELECT
    '${stat_month}' AS stat_month,
    SUM(out_amt) / NULLIF((SELECT avg_stock_amt FROM inv_m), 0) AS turnover_rate
  FROM dwd_inv_fact_stock_flow
  WHERE dt BETWEEN CONCAT('${stat_month}', '-01') AND LAST_DAY(CONCAT('${stat_month}', '-01'))
)
SELECT
  i.stat_month,
  i.avg_stock_amt,
  t.turnover_rate
FROM inv_m i
JOIN turn_m t ON i.stat_month = t.stat_month;
```

---

### 1.12 效期风险预警（日历/时间轴数据集）
定义：未来1-3个月内每天/每周即将过期的批次数、金额。

SQL（按天输出未来 90 天）：
```sql
SELECT
  s.dt AS stat_dt,
  TO_DATE(s.expiry_date) AS expiry_dt,
  COUNT(DISTINCT s.batch_id) AS batch_cnt,
  SUM(s.stock_amt) AS stock_amt
FROM dws_inv_stock_snapshot_di s
WHERE s.dt = '${dt}'
  AND s.stock_amt > 0
  AND s.expiry_date IS NOT NULL
  AND TO_DATE(s.expiry_date) BETWEEN TO_DATE('${dt}') AND DATE_ADD(TO_DATE('${dt}'), 90)
GROUP BY s.dt, TO_DATE(s.expiry_date)
ORDER BY expiry_dt;
```

---

## 2. 采购管理（PUR）

### 2.1 准时交付率
定义：`按时到货的采购单数 / 总采购单数`

SQL（按月汇总；以订单行判定准时）：
```sql
WITH base AS (
  SELECT
    order_line_id,
    plan_arrive_date,
    arrive_date,
    CASE WHEN arrive_date IS NOT NULL AND TO_DATE(arrive_date) <= TO_DATE(plan_arrive_date) THEN 1 ELSE 0 END AS is_ontime
  FROM dwd_pur_wide_order_lifecycle_line
  WHERE dt BETWEEN CONCAT('${stat_month}', '-01') AND LAST_DAY(CONCAT('${stat_month}', '-01'))
)
SELECT
  '${stat_month}' AS stat_month,
  SUM(is_ontime) / NULLIF(COUNT(1), 0) AS ontime_delivery_rate
FROM base;
```

---

### 2.2 项目平均采购延迟天数
定义：`Σ(项目内每笔订单实际完成日期 - 项目要求日期) ÷ 项目采购订单数`

SQL（以“到货日期”为实际完成日期；可改为入库日期）：
```sql
WITH base AS (
  SELECT
    project_id,
    require_date,
    COALESCE(arrive_date, stock_in_date) AS actual_done_date
  FROM dwd_pur_wide_order_lifecycle_line
  WHERE dt BETWEEN CONCAT('${stat_month}', '-01') AND LAST_DAY(CONCAT('${stat_month}', '-01'))
    AND project_id IS NOT NULL
)
SELECT
  '${stat_month}' AS stat_month,
  project_id,
  AVG(DATEDIFF(TO_DATE(actual_done_date), TO_DATE(require_date))) AS avg_purchase_delay_days
FROM base
WHERE actual_done_date IS NOT NULL AND require_date IS NOT NULL
GROUP BY project_id;
```

---

### 2.3 物料类别的平均采购周期
定义：`Σ(某类物料各订单全流程天数) ÷ 该类物料订单数`

SQL（按月、分类；请购→入库）：
```sql
WITH base AS (
  SELECT
    w.order_line_id,
    m.material_class_id,
    w.apply_date,
    w.stock_in_date
  FROM dwd_pur_wide_order_lifecycle_line w
  JOIN dwd_inv_master_material m ON w.material_id = m.material_id
  WHERE w.dt BETWEEN CONCAT('${stat_month}', '-01') AND LAST_DAY(CONCAT('${stat_month}', '-01'))
    AND w.apply_date IS NOT NULL
    AND w.stock_in_date IS NOT NULL
)
SELECT
  '${stat_month}' AS stat_month,
  material_class_id,
  AVG(DATEDIFF(TO_DATE(stock_in_date), TO_DATE(apply_date))) AS avg_purchase_cycle_days
FROM base
GROUP BY material_class_id;
```

---

### 2.4 紧急采购占比
定义：`紧急采购订单金额 ÷ 总采购金额`

口径建议（可审计）：订单行无请购来源（`pray_line_id` 为空）视为紧急（或叠加紧急放行单标记）。

SQL（按月）：
```sql
WITH base AS (
  SELECT
    order_line_id,
    order_amt,
    CASE WHEN pray_line_id IS NULL OR pray_line_id = '' THEN 1 ELSE 0 END AS is_urgent
  FROM dwd_pur_wide_order_lifecycle_line
  WHERE dt BETWEEN CONCAT('${stat_month}', '-01') AND LAST_DAY(CONCAT('${stat_month}', '-01'))
)
SELECT
  '${stat_month}' AS stat_month,
  SUM(CASE WHEN is_urgent=1 THEN order_amt ELSE 0 END) / NULLIF(SUM(order_amt), 0) AS urgent_purchase_rate
FROM base;
```

---

### 2.5 单一来源采购占比
Excel口径：
- `单一来源物料种类数 / 总采购物料种类数`
- `项目内单一来源采购金额 ÷ 项目总采购金额`

SQL（按月：物料在该月只有1个供应商即视为单一来源）：
```sql
WITH purchases AS (
  SELECT
    project_id,
    material_id,
    supplier_id,
    SUM(order_amt) AS amt
  FROM dwd_pur_fact_order_line
  WHERE dt BETWEEN CONCAT('${stat_month}', '-01') AND LAST_DAY(CONCAT('${stat_month}', '-01'))
  GROUP BY project_id, material_id, supplier_id
),
mat_sup_cnt AS (
  SELECT
    material_id,
    COUNT(DISTINCT supplier_id) AS sup_cnt
  FROM purchases
  GROUP BY material_id
),
tag AS (
  SELECT p.*, CASE WHEN c.sup_cnt = 1 THEN 1 ELSE 0 END AS is_single_source
  FROM purchases p
  JOIN mat_sup_cnt c ON p.material_id = c.material_id
)
SELECT
  '${stat_month}' AS stat_month,
  COUNT(DISTINCT CASE WHEN is_single_source=1 THEN material_id END) / NULLIF(COUNT(DISTINCT material_id), 0) AS single_source_sku_rate,
  project_id,
  SUM(CASE WHEN is_single_source=1 THEN amt ELSE 0 END) / NULLIF(SUM(amt), 0) AS project_single_source_amt_rate
FROM tag
GROUP BY project_id;
```

---

### 2.6 采购到货周期监控（甘特图数据集）
展示字段：采购申请日期、订单发出日期、计划到货日期、实际到货日期（及延误天数）。

SQL：
```sql
SELECT
  project_id,
  material_id,
  apply_date,
  order_date,
  plan_arrive_date,
  arrive_date,
  DATEDIFF(TO_DATE(arrive_date), TO_DATE(plan_arrive_date)) AS arrive_delay_days
FROM dwd_pur_wide_order_lifecycle_line
WHERE dt BETWEEN CONCAT('${stat_month}', '-01') AND LAST_DAY(CONCAT('${stat_month}', '-01'));
```

---

### 2.7 月度采购趋势（柱状图数据集）
定义：按月输出采购金额（需选定“订单金额”或“入库金额”口径）。

SQL（订单金额口径）：
```sql
SELECT
  SUBSTR(dt, 1, 7) AS stat_month,
  SUM(order_amt) AS purchase_amt
FROM dwd_pur_fact_order_line
WHERE dt BETWEEN '${start_dt}' AND '${end_dt}'
GROUP BY SUBSTR(dt, 1, 7)
ORDER BY stat_month;
```

---

### 2.8 供应商风险分析（气泡图）
Excel描述：X=采购金额/占比，Y=风险评分（交付/质量/独家性等综合）。

当前ODS缺口：风险评分数据源未在 `dg.docx` 范围内提供。  
落地建议：新增 `dws_pur_supplier_risk_score_mn(supplier_id, stat_month, risk_score, ...)`，由质检/不良/交付/独家性等主题汇总计算。

---

### 2.9 项目采购监控（列表）
建议输出：项目下订单行关键状态（物料、申请日期、需求日期、当前环节、已延迟天数等）。

SQL（示例）：
```sql
SELECT
  project_id,
  order_line_id,
  material_id,
  apply_date,
  require_date,
  order_date,
  plan_arrive_date,
  arrive_date,
  stock_in_date,
  CASE
    WHEN stock_in_date IS NOT NULL THEN '已入库'
    WHEN arrive_date IS NOT NULL THEN '已到货'
    WHEN order_date IS NOT NULL THEN '已下单'
    ELSE '已请购'
  END AS current_stage,
  GREATEST(DATEDIFF(TO_DATE(COALESCE(stock_in_date, arrive_date, '${dt}')), TO_DATE(require_date)), 0) AS delayed_days
FROM dwd_pur_wide_order_lifecycle_line
WHERE dt = '${dt}';
```

---

## 3. 项目财务（PROJ-FIN）

### 3.1 整体预算执行率
定义：`Σ(项目实际支出) / Σ(项目批复预算) × 100%`

SQL（以日快照）：
```sql
SELECT
  dt,
  SUM(exec_amt) / NULLIF(SUM(approved_budget_amt), 0) AS overall_budget_exec_rate
FROM dws_proj_budget_exec_di
WHERE dt = '${dt}'
GROUP BY dt;
```

---

### 3.2 整体支出合规率
定义：`合规支出笔数 / 总支出笔数 × 100%`

当前ODS缺口：支出/凭证及合规判定未在 `dg.docx` 中提供。  
占位实现（需补采财务事实与规则结果表）：
```sql
SELECT
  dt,
  SUM(CASE WHEN is_compliant=1 THEN 1 ELSE 0 END) / NULLIF(COUNT(1), 0) AS overall_expense_compliance_rate
FROM dwd_fin_fact_expense_line
WHERE dt BETWEEN '${start_dt}' AND '${end_dt}'
GROUP BY dt;
```

---

### 3.3 人均项目经费
定义：`年度新增项目总经费 / 科研人员总数`

当前ODS缺口：科研人员总数（HR人员快照）未在 `dg.docx` 中提供。  
占位实现（需补采 `dws_hr_headcount_yr` 或同类数据集）：
```sql
WITH fund AS (
  SELECT
    '${stat_year}' AS stat_year,
    SUM(approved_budget_amt) AS new_project_fund_amt
  FROM dws_proj_budget_exec_di
  WHERE dt = CONCAT('${stat_year}', '-12-31')
    AND is_new_in_year = 1
),
hc AS (
  SELECT '${stat_year}' AS stat_year, headcount
  FROM dws_hr_headcount_yr
  WHERE stat_year = '${stat_year}'
)
SELECT
  fund.stat_year,
  fund.new_project_fund_amt / NULLIF(hc.headcount, 0) AS fund_per_researcher
FROM fund
JOIN hc ON fund.stat_year = hc.stat_year;
```

---

### 3.4 经费结余率（平均）
定义：`Σ(项目结余资金) / Σ(项目总经费) × 100%`

SQL（以“结余=批复+调整-执行”为示例口径）：
```sql
SELECT
  dt,
  SUM(approved_budget_amt - exec_amt) / NULLIF(SUM(approved_budget_amt), 0) AS budget_balance_rate
FROM dws_proj_budget_exec_di
WHERE dt = '${dt}'
GROUP BY dt;
```

---

### 3.5 单位成果经费强度
定义：`项目总经费 / 核心成果数量`

当前ODS缺口：核心成果事实未在 `dg.docx` 中提供。  
占位实现（需补采成果表，如论文/专利/验收成果）：
```sql
SELECT
  project_id,
  total_fund_amt / NULLIF(core_outcome_cnt, 0) AS fund_intensity_per_outcome
FROM dws_proj_outcome_summary_yr
WHERE stat_year = '${stat_year}';
```

---

### 3.6 预算调整率
定义：`预算调整额 / 原批复预算 × 100%`

SQL：
```sql
SELECT
  dt,
  SUM(budget_adjust_amt) / NULLIF(SUM(budget_amt), 0) AS budget_adjust_rate
FROM dws_proj_budget_exec_di
WHERE dt = '${dt}'
GROUP BY dt;
```

---

### 3.7 预算执行率分析（趋势数据集）
定义：按月输出预算执行率。

SQL（按月，整体）：
```sql
SELECT
  stat_month,
  SUM(exec_amt) / NULLIF(SUM(approved_budget_amt), 0) AS budget_exec_rate
FROM dws_proj_budget_exec_mn
WHERE stat_month BETWEEN '${start_month}' AND '${end_month}'
GROUP BY stat_month
ORDER BY stat_month;
```

---

### 3.8 支出合规率（趋势数据集）
同 3.2，需财务合规数据源，略。

---

## 4. 项目执行（PROJ-EXEC）

### 4.1 项目及时完成率
定义：`按时结题项目数 / 总结题项目数 × 100%`

SQL（以“已完工且实际完成≤计划完成”为按时）：
```sql
WITH done AS (
  SELECT
    project_id,
    plan_finish_date,
    actu_finish_date
  FROM dwd_proj_master_project
  WHERE actu_finish_date IS NOT NULL
)
SELECT
  '${dt}' AS dt,
  SUM(CASE WHEN TO_DATE(actu_finish_date) <= TO_DATE(plan_finish_date) THEN 1 ELSE 0 END) / NULLIF(COUNT(1), 0) AS project_ontime_finish_rate
FROM done;
```

---

### 4.2 项目总数 / 在执行项目数量 / 完工项目数量 / 延期项目数量
SQL（延期=未完工且已过计划完成）：
```sql
SELECT
  '${dt}' AS dt,
  COUNT(1) AS total_project_cnt,
  SUM(CASE WHEN actu_finish_date IS NULL THEN 1 ELSE 0 END) AS in_progress_project_cnt,
  SUM(CASE WHEN actu_finish_date IS NOT NULL THEN 1 ELSE 0 END) AS finished_project_cnt,
  SUM(CASE WHEN actu_finish_date IS NULL AND plan_finish_date IS NOT NULL AND TO_DATE('${dt}') > TO_DATE(plan_finish_date) THEN 1 ELSE 0 END) AS delayed_project_cnt
FROM dwd_proj_master_project;
```

---

### 4.3 重点项目进展（列表/甘特图数据集）
建议口径：以 `planpriority`（计划优先级）或业务侧“重点标记”筛选。

SQL（示例：高优先级项目输出关键日期与延迟天数）：
```sql
SELECT
  project_id,
  project_code,
  project_name,
  plan_start_date,
  plan_finish_date,
  actu_start_date,
  actu_finish_date,
  CASE
    WHEN actu_finish_date IS NOT NULL THEN DATEDIFF(TO_DATE(actu_finish_date), TO_DATE(plan_finish_date))
    ELSE DATEDIFF(TO_DATE('${dt}'), TO_DATE(plan_finish_date))
  END AS delay_days
FROM dwd_proj_master_project
WHERE planpriority IN (1, 2);
```

