# 项目看板 vs 项目执行监控 — 指标对比与可复用性分析

> 目标：用项目看板（第一套模型，可用）的数据，填充项目执行监控大屏的指标

---

## 一、7 个 KPI 卡片对比

| # | 执行监控指标 | 执行监控原表 | 项目看板可替代？ | 替代来源表 | 替代字段/SQL | 说明 |
|---|-----------|------------|:---:|----------|------------|------|
| 1 | **整体完成率** 72.4% | `biz_ads_project_kpi_overview`.completion_rate | **✅ 直接可用** | `biz_ads_project_kpi_overview` | `completion_rate` | 同一张表，同一字段 |
| 2 | **里程碑达成率** 68% | `biz_ads_project_milestone_kpi`.milestone_completion_rate | **✅ 可替代** | `biz_ads_major_project_overview` | `milestone_completion_rate` | 项目看板的 major_project_overview 有此字段，粒度为项目级，取 AVG 即可 |
| 3 | **延期任务数** 37 | `biz_ads_project_kpi_overview`.incomplete_cnt | **✅ 直接可用** | `biz_ads_project_kpi_overview` | `incomplete_cnt` | 同一张表，同一字段 |
| 4 | **最大延期天数** 28天 | `biz_ads_project_kpi_overview`（实际缺失此字段） | **✅ 可替代** | `biz_ads_major_project_overview` | `MAX(max_delay_days)` | 项目看板的 major_project_overview 有 max_delay_days |
| 5 | **近期到期** 15 | `biz_ads_project_kpi_overview`.due_cnt | **✅ 直接可用** | `biz_ads_project_kpi_overview` | `due_cnt` | 同一张表，同一字段 |
| 6 | **阻塞链路** 6 | `biz_ads_project_incomplete_risk`.incomplete_high_risk_cnt | **⚠️ 近似可用** | `biz_ads_major_project_overview` | `SUM(overdue_open_nodes)` 或 `SUM(high_risk_nodes)` | 无精确对应，用超期未关闭节点数或高风险节点数近似 |
| 7 | **责任人聚焦** 12 | `biz_ads_project_kpi_overview`.abnormal_pending_cnt | **✅ 直接可用** | `biz_ads_project_kpi_overview` | `abnormal_pending_cnt` | 同一张表，同一字段 |

### KPI 替代 SQL

```sql
-- 一次查询获取全部 7 个 KPI
WITH latest_month AS (
    SELECT *
    FROM biz_ads_project_kpi_overview
    ORDER BY plan_year DESC, plan_month DESC
    LIMIT 1
),
project_agg AS (
    SELECT
        COUNT(*)                                      AS project_count,
        ROUND(AVG(milestone_completion_rate), 4)      AS avg_milestone_rate,
        MAX(max_delay_days)                           AS max_delay_days,
        SUM(overdue_open_nodes)                       AS blocked_nodes,
        SUM(high_risk_nodes)                          AS high_risk_nodes
    FROM biz_ads_major_project_overview
)
SELECT
    -- 1. 整体完成率
    lm.completion_rate,
    -- 2. 里程碑达成率（从 major_project_overview 取平均）
    pa.avg_milestone_rate          AS milestone_rate,
    -- 3. 延期任务数
    lm.incomplete_cnt,
    -- 4. 最大延期天数
    pa.max_delay_days,
    -- 5. 近期到期
    lm.due_cnt,
    -- 6. 阻塞链路（用超期未关闭节点近似）
    pa.blocked_nodes               AS blocked_count,
    -- 7. 责任人聚焦
    lm.abnormal_pending_cnt
FROM latest_month lm, project_agg pa;
```
百分比示例:  lm.completion_rate *100 as new_completion_rate ,
---

## 二、甘特图（任务执行甘特）

| 执行监控组件 | 原表 | 项目看板可替代？ | 替代方案 |
|------------|------|:---:|---------|
| 甘特图：项目→子项目→任务节点 | `biz_ads_major_project_tree_snapshot` | **⚠️ 部分可用** | `biz_ads_major_project_overview` 有项目级汇总（total_nodes, completed_nodes, completion_rate），但**缺少子项目和任务节点明细** |

### 甘特图可获取的数据

```sql
-- 项目级甘特（有数据）
SELECT
    major_project_id,
    major_project_name,
    total_nodes,
    completed_nodes,
    completion_rate,
    avg_delay_days,
    max_delay_days
FROM biz_ads_major_project_overview
ORDER BY completion_rate ASC;
```
实际上：
SELECT                                                                                  
      node_id,                                                                                                                                                    
      node_task AS name,                      
      node_type,                                                                                                                                                  
      major_project_id,                                                                                                                                           
      major_project_name,                                                                                                                                         
      subproject_name,                                                                                                                                            
      plan_date,                                                                                                                                                  
      actual_date,                                                                                                                                                
      completion_status,                                          
      risk_level,                                                                                                                                                 
      delay_days,
      owner,                                                                                                                                                      
      dept                                                        
  FROM biz_dwd_project_node_enriched
  WHERE plan_date IS NOT NULL
  ORDER BY major_project_id, plan_date


**缺失部分：** 子项目分组、单个任务节点的计划/实际日期、责任人、延期天数。这些在 `biz_ads_major_project_tree_snapshot` 中，项目看板没有等价数据。

---

## 三、阶段分布（柱状图）

| 执行监控组件 | 原表 | 项目看板可替代？ | 替代方案 |
|------------|------|:---:|---------|
| 阶段分布：策划中/执行中/验收中/已完成/已暂停 | `biz_ads_project_kpi_overview` | **✅ 直接可用** | 同一张表 |

```sql
-- 从最新月份取各阶段数量
SELECT
    pending_normal_cnt   AS "策划中+执行中",
    due_cnt              AS "验收中(到期)",
    completed_total_cnt  AS "已完成",
    incomplete_cnt       AS "已暂停/未完成"
FROM biz_ads_project_kpi_overview
ORDER BY plan_year DESC, plan_month DESC
LIMIT 1;
```

---

## 四、责任科室负载（柱状图）

| 执行监控组件 | 原表 | 项目看板可替代？ | 替代方案 |
|------------|------|:---:|---------|
| 按科室的在办/延期任务数 | `biz_ads_project_kpi_overview`（无 dept 维度） | **⚠️ 需换表** | `biz_ads_major_project_overview` 按项目有节点数，但无科室维度 |

**现状：** 两套模型的 ADS 层都没有直接的「科室×任务数」粒度。要实现截图中按工程建设/数字化/研发等科室分组，需要查询 DWD 层 `biz_dwd_project_node` 的 `dept` 字段，该表项目看板的模型链路中存在且可用。

```sql
-- 从 DWD 层按科室聚合（可用）
SELECT
    dept                                           AS dept_name,
    COUNT(*)                                       AS total_tasks,
    SUM(CASE WHEN is_incomplete THEN 1 ELSE 0 END) AS overdue_tasks
FROM biz_dwd_project_node
WHERE plan_year = EXTRACT(YEAR FROM CURRENT_DATE)
GROUP BY dept
ORDER BY total_tasks DESC;
```
翻译版本
SELECT
    dept                                           AS dept_name,
    COUNT(*)                                       AS 总任务,
    SUM(CASE WHEN is_incomplete THEN 1 ELSE 0 END) AS 逾期任务
FROM biz_dwd_project_node
WHERE plan_year = EXTRACT(YEAR FROM CURRENT_DATE)
GROUP BY dept
ORDER BY 总任务 DESC;
---

## 五、延期项目 TOP 表

| 执行监控组件 | 列 | 项目看板可替代？ | 替代来源 |
|------------|---|:---:|---------|
| 项目 | `major_project_name` | ✅ | `biz_ads_major_project_overview` |
| 延期天数 | `max_delay_days` | ✅ | `biz_ads_major_project_overview` |
| 责任科室 | 来自 tree_snapshot | **⚠️** | `biz_dwd_project_node_enriched`.`major_project_owner_dept` |
| 风险等级 | 来自 tree_snapshot | **⚠️** | 用 `high_risk_nodes > 0` 判定"高"，否则"中/低" |

```sql
SELECT
    major_project_name                          AS "项目",
    max_delay_days                              AS "延期天数",
    -- 责任科室需要 JOIN enriched 层，或近似用项目名称代替
    CASE
        WHEN high_risk_nodes > 0 THEN '高'
        WHEN overdue_open_nodes > 0 THEN '中'
        ELSE '低'
    END                                         AS "风险等级"
FROM biz_ads_major_project_overview
WHERE max_delay_days > 0
ORDER BY max_delay_days DESC
LIMIT 10;
```

---

## 六、任务阻塞链路与责任人分析（底部表格）

| 执行监控列 | 原表 | 项目看板可替代？ | 替代方案 |
|----------|------|:---:|---------|
| 任务/节点 | `biz_ads_project_incomplete_risk` | **❌ 无直接等价** | 需从 `biz_dwd_project_node` 查明细 |
| 阻塞原因 | 同上 | **❌** | `biz_dwd_project_node`.`incomplete_reason` |
| 责任人 | 同上 | **❌** | `biz_dwd_project_node`.`owner` |
| 责任科室 | 同上 | **❌** | `biz_dwd_project_node`.`dept` |
| 建议动作 | 同上 | **❌** | 无数据来源，需人工或规则引擎 |

**替代 SQL（从 DWD 层直取，该表在第一套模型中可用）：**

```sql
SELECT
    node_task                 AS "任务/节点",
    incomplete_reason         AS "阻塞原因",
    owner                     AS "责任人",
    dept                      AS "责任科室",
    delay_days                AS "延期天数",
    risk_level                AS "风险等级"
FROM biz_dwd_project_node
WHERE is_incomplete = true
  AND plan_year = EXTRACT(YEAR FROM CURRENT_DATE)
ORDER BY delay_days DESC NULLS LAST
LIMIT 20;
```

---

## 七、汇总：可复用性矩阵

| 执行监控组件 | 可用性 | 来源层 | 替代表 |
|------------|:---:|--------|-------|
| 整体完成率 | ✅ | ADS | `biz_ads_project_kpi_overview` |
| 里程碑达成率 | ✅ | ADS | `biz_ads_major_project_overview` |
| 延期任务数 | ✅ | ADS | `biz_ads_project_kpi_overview` |
| 最大延期天数 | ✅ | ADS | `biz_ads_major_project_overview` |
| 近期到期 | ✅ | ADS | `biz_ads_project_kpi_overview` |
| 阻塞链路数 | ⚠️ | ADS | `biz_ads_major_project_overview`(近似) |
| 责任人聚焦 | ✅ | ADS | `biz_ads_project_kpi_overview` |
| 甘特图（项目级） | ⚠️ | ADS | `biz_ads_major_project_overview`(仅项目级) |
| 甘特图（任务级） | ❌ | — | 需 tree_snapshot 或查 DWD |
| 阶段分布 | ✅ | ADS | `biz_ads_project_kpi_overview` |
| 责任科室负载 | ⚠️ | DWD | `biz_dwd_project_node`(按 dept 聚合) |
| 延期项目 TOP | ✅ | ADS | `biz_ads_major_project_overview` |
| 阻塞链路明细 | ⚠️ | DWD | `biz_dwd_project_node`(直取未完成) |

**结论：**
- **✅ 7/13 个组件** 可直接从项目看板的 ADS 层获取
- **⚠️ 4/13 个组件** 可从 DWD 层（`biz_dwd_project_node`）补充获取，该表在第一套模型中可用
- **❌ 2/13 个组件**（任务级甘特、建议动作）无法直接获取，需 tree_snapshot 模型修复或人工补充
