# 遗留问题：ADS 层缺少 `project_no` 维度，大屏项目维度 KPI 无法迁移到 ADS

**登记日期**：2026-04-17
**关联 review**：`worklog/v2.2.3/s10/pjm/v3/screen-instances/*` 大屏模型迁移 ADS 层工作

## 背景

v3 架构中，ADS 层（`biz_ads_progress_kpi_v2` / `biz_ads_quality_kpi_v2` / `biz_ads_risk_kpi_v2` / `biz_ads_tech_state_kpi_v2` 及对应 `*_derived_v2`、`biz_ads_composite_derived_v2`）按**月粒度**预计算指标，不保留 `project_no` 维度。

大屏中部分 KPI 依赖 `COUNT(DISTINCT project_no)` 或按项目号粒度筛选（如 `[[AND s.project_no ILIKE '%' || {{projectNo}} || '%']]`），这些 KPI 无法从现有 ADS 层算出。

## 具体受影响 KPI（已识别）

| 大屏 | KPI | 当前 SQL | ADS 能否提供 |
|---|---|---|---|
| `gpmc-overview-v3` | 项目总数 | `COUNT(DISTINCT s.project_no) FROM biz_dws_progress_monthly_v2` | ❌ 无项目维度 |
| `gpmc-overview-v3` | 进行中项目 | `COUNT(DISTINCT CASE WHEN s.incomplete_cnt > 0 THEN s.project_no END)` | ❌ 无项目维度 |
| `gpmc-*-board-v3` | 按项目聚合的柱状/表格 | `GROUP BY s.project_no` | ❌ 无项目维度 |
| 所有大屏 | 按项目号模糊搜索 | `[[AND ... project_no ILIKE ...]]` | ❌ 无项目维度 |

## 临时方案（本次改造采用）

**KPI 分流**：
- **按月聚合的标量 KPI**（未闭环质量问题数 / 高风险数量 / 未整改技术更改单数 / 项目完成率等）→ 迁移到 ADS 层，按 `plan_month` / `period_month` 过滤后再聚合
- **按 `project_no` 维度的 KPI**（项目总数 / 进行中项目 / 项目排名表 / 项目模糊搜索）→ **保留查 DWS/DWD**，不做迁移

即一张大屏可能同时引用 ADS（轻量 KPI）和 DWS（项目维度明细），混合分层查询。

## 长期选项（三选一，待定）

### 选项 A：扩展 ADS 层加 `project_no` 维度（粒度变细）

- 新增 `biz_ads_progress_kpi_by_project_v2`（`plan_month × project_no`）
- 好处：大屏 KPI 完全走 ADS，分层纯净
- 坏处：
  - ADS 表行数 ≈ `project_count × month_count`（几十倍于当前）
  - 需新增 dbt 模型、维护成本上升
  - `completion_rate` 等比率字段在项目级别要重新定义（分母可能为 0）

### 选项 B：ADS 不变，大屏混合查询（当前方案）

- 标量 KPI 查 ADS，项目维度 KPI 查 DWS
- 好处：ADS 层稳定，改造最小
- 坏处：大屏 SQL 两种数据源共存，维护复杂度介于两者之间
- 适合：项目数不多、DWS 扫描成本可接受的场景

### 选项 C：引入语义层（Metric Store）

- 用语义建模层（dbt Metrics / Cube / MetricFlow）定义一次，查询时自动下钻到 DWS 或 ADS
- 好处：业务指标一次定义，大屏只消费 metric 名
- 坏处：引入新组件，学习成本高，当前阶段不适用

## 2026-04-17 补充实证

尝试对 drill-risk-v3 做 pilot 时，发现更广泛的结论：

- **全量扫描 10 个大屏，104 个 SQL 组件，0 个是"不查 DWD + 无 dept/project_no 过滤"的纯 ADS 候选**
- 即使是 number-card 级的简单 KPI（如 drill-risk 的风险总数/高/中/低/已释放/未释放），SQL 里都带 `[[AND dept = {{deptId}}]]` + `[[AND project_no ILIKE ...]]` 可选过滤
- ADS `*_kpi` 没有 `dept` 和 `project_no` 列 → 迁移后，即便参数为空 SQL 等价，但一旦用户在 filter 组件选了部门/项目，**过滤就失效**，展示的数字不再对应用户选择
- 等于把"带条件的数据质量"降级成"无条件的数据质量" — 仍然算数据质量风险

### 运行时缓存已存在

`dts-analytics` 的 `QueryCacheService`（Caffeine，TTL 默认，按 `db_id + query + user_id` 哈希）对 `/dataset` API 结果做内存缓存：

- 同一用户在同一大屏上，多个组件若发完全相同的请求体（database、native.query、参数），第 1 次查完入缓存，后续命中
- 因此 overview/drill-risk 里"5 份相同 SQL"的实际 DB 压力 ≈ 1 次查询 + 4 次缓存命中，**不是 5 次全量扫表**
- 这削弱了"消除重复"作为改造动机的紧迫性

### 改造前置条件（任何一项满足才能推进）

1. 扩展 ADS 表到 `dept × project_no × period_month` 粒度（选项 A，dbt 工作量中等）
2. 引入 Card 共享机制 + 扩展 `deploy-screens.sh`（选项 B，运维+部署工作量中等）
3. 放弃 `[[AND dept = {{deptId}}]]` / `project_no` 的维度过滤能力（业务需求降级，需业务侧确认）

### 当前阶段的唯一安全动作

**不改大屏 JSON，保持现状**。下一次迭代前优先补 ADS 层维度（选项 A），之后再统一迁移。

## 建议决策节点

在以下任一条件触发时重新评估本遗留：

1. DWS 表行数超过 **500 万**，大屏查询延迟 > 2s
2. 出现 ≥ 3 张新大屏都需要相同的项目维度 KPI
3. 有业务需求要做"项目 vs 项目"对比仪表板

## 关联文件

- 大屏 JSON：`worklog/v2.2.3/s10/pjm/v3/screen-instances/*.json`
- ADS 模型：`worklog/v2.2.3/s10/pjm/v3/models/ads/biz_ads_*_kpi_v2.sql`
- DWS 模型：`worklog/v2.2.3/s10/pjm/v3/models/dws/biz_dws_*_monthly_v2.sql`
- dbt review 遗留项（12 项）：参见 memory `feedback_dbt_pjm_v3_pending.md`
