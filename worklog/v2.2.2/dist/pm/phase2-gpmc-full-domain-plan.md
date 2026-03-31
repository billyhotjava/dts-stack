# GPMC 全域 dbt 模型实施计划（Phase 2）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐质量、技术状态、风险、成本四个主题域的 ODS→DWD→DWS→ADS 全链路 dbt 模型，使 6 张 GPMC 大屏的所有指标都能从数仓取数，替换当前 mockData。

**Architecture:** 沿用 Phase 1 的分层结构（ODS 全 varchar → DWD 清洗标准化 → DWS 周期汇总 → ADS KPI 输出）。每个新域独立建 ODS 表、DWD 事实表、DWS 汇总表、ADS 指标表。复用已有 macros（nullif_placeholder / parse_date_safe / parse_numeric_safe）。

**Tech Stack:** dbt (SQL) on PostgreSQL 17.6, 复用 Phase 1 macros, 输出到 public schema。

**输出目录:** `worklog/v2.2.2/dist/pm/models-phase2/` — 最终合并到部署包。

---

## 文件结构

### 新建文件

```
models-phase2/
├── pm_ods_sources_phase2.yml          # 8 张新源表定义
├── dwd/
│   ├── dim_quality_status.sql         # 质量状态维度表
│   ├── dim_change_category.sql        # 更改类别维度表 (I/II/III)
│   ├── dim_signature_status.sql       # 文件签署状态维度表
│   ├── biz_dwd_quality_issue.sql      # 质量问题事实表
│   ├── biz_dwd_quality_measure.sql    # 质量跟进措施表
│   ├── biz_dwd_tech_state.sql         # 技术状态事实表
│   ├── biz_dwd_tech_state_measure.sql # 技术状态跟进措施表
│   ├── biz_dwd_risk_info.sql          # 风险事实表
│   ├── biz_dwd_risk_measure.sql       # 风险跟进措施表
│   └── biz_dwd_cost_accounting.sql    # 成本事实表
├── dws/
│   ├── biz_dws_quality_period_summary.sql      # 质量域周期汇总
│   ├── biz_dws_tech_state_period_summary.sql   # 技术状态域周期汇总
│   ├── biz_dws_risk_period_summary.sql         # 风险域周期汇总
│   └── biz_dws_cost_period_summary.sql         # 成本域周期汇总
├── ads/
│   ├── biz_ads_quality_kpi.sql                 # 质量域 KPI (Page 5 of PDF)
│   ├── biz_ads_tech_state_kpi.sql              # 技术状态域 KPI (Page 4 of PDF)
│   ├── biz_ads_risk_kpi.sql                    # 风险域 KPI
│   └── biz_ads_cost_kpi.sql                    # 成本域 KPI
└── phase2_schema.yml                           # 模型验证
```

### 已有文件（不修改，仅合并）

Phase 1 的所有 models/ 和 macros/ 保持不变。

---

## 数据源表定义（对应 PDF pmall.pdf 9 张逻辑表）

### 已有
| # | ODS 表名 | 逻辑名 | 状态 |
|---|---------|--------|------|
| 1 | ods_project_subject_domain | 进度跟进措施表 | ✅ Phase 1 |

### 新增
| # | ODS 表名 | 逻辑名 | 主题域 | 字段来源 |
|---|---------|--------|--------|---------|
| 2 | ods_quality_issue | 质量信息汇总表 | 质量 | PDF P5 + project_ref/质量统计表 |
| 3 | ods_quality_measure | 质量跟进措施表 | 质量 | PDF P1 + project2 |
| 4 | ods_tech_state | 技术状态信息汇总表 | 技术状态 | PDF P4 + project_ref/系统信息字典 |
| 5 | ods_tech_state_measure | 技术状态跟进措施表 | 技术状态 | PDF P1 |
| 6 | ods_risk_info | 风险信息汇总表 | 风险 | PDF P1 + project_ref/系统信息字典 |
| 7 | ods_risk_measure | 风险跟进措施表 | 风险 | PDF P1 + project_ref/风险跟进表 |
| 8 | ods_cost_accounting | 成本核算基本表 | 成本 | T02 最小契约 |
| 9 | ods_project_info | 项目信息表（主数据） | 全局维表 | project1 + project_ref |

---

## 指标覆盖矩阵（对应 6 张大屏 mockData）

### Screen 1: OverviewScreen — 综合态势总览
| 指标 | 来源域 | 模型 |
|------|--------|------|
| 项目总数 / 进行中 / 延期 | 执行 | ✅ Phase 1 ads |
| 年度预算总额 / 执行率 | 成本 | **biz_ads_cost_kpi** |
| 平均进度达成率 | 执行 | ✅ Phase 1 ads |
| 高风险项目占比 | 风险 | **biz_ads_risk_kpi** |
| 项目阶段分布 | 执行 | ✅ Phase 1 |
| 质量问题分类 | 质量 | **biz_ads_quality_kpi** |
| 延期 TOP5 | 执行 | ✅ Phase 1 |
| 风险分类排名 | 风险 | **biz_ads_risk_kpi** |
| 部门成本核算 | 成本 | **biz_ads_cost_kpi** |
| 技术状态变更 | 技术状态 | **biz_ads_tech_state_kpi** |

### Screen 2: ExecutionScreen — 项目执行监控
全部由 Phase 1 已覆盖 ✅

### Screen 3: QualityScreen — 质量信息与跟进
| 指标 | 模型 |
|------|------|
| 新增质量问题 / 现存问题 | **biz_ads_quality_kpi** |
| 归零完成率 / 未提交归零计划 | **biz_ads_quality_kpi** |
| 问题分类分布 | **biz_dws_quality_period_summary** |
| 问题状态概览 (未完成归零/技术归零/管理归零) | **biz_ads_quality_kpi** |
| 质量问题清单 | **biz_dwd_quality_issue** |

### Screen 4: TechStateScreen — 技术状态与跟进
| 指标 | 模型 |
|------|------|
| 技术状态变更数 / 文件签署完成率 | **biz_ads_tech_state_kpi** |
| 未闭环项 / 措施覆盖率 | **biz_ads_tech_state_kpi** |
| 更改类别分布 (I/II/III) | **biz_dws_tech_state_period_summary** |
| 文件签署状态 | **biz_dws_tech_state_period_summary** |
| 技术状态变更清单 | **biz_dwd_tech_state** |

### Screen 5: CostScreen — 成本与预算控制
| 指标 | 模型 |
|------|------|
| 年度预算/执行额/执行率/偏差 | **biz_ads_cost_kpi** |
| 月度支出趋势 | **biz_dws_cost_period_summary** |
| 部门预算执行 | **biz_ads_cost_kpi** (按部门) |

### Screen 6: RiskScreen — 风险与预警中心
| 指标 | 模型 |
|------|------|
| 风险总数/高/中/闭环率 | **biz_ads_risk_kpi** |
| 风险概率×影响矩阵 | **biz_ads_risk_kpi** |
| 风险分类分布 | **biz_dws_risk_period_summary** |
| 风险跟进清单 | **biz_dwd_risk_info** |

---

## Task 1: ODS 源表定义 + 建表 SQL

**Files:**
- Create: `models-phase2/pm_ods_sources_phase2.yml`
- Create: `models-phase2/ods_create_tables.sql` (手动建表 DDL，非 dbt 模型)

- [ ] **Step 1:** 创建 ODS YAML source 定义（9 张新源表）
- [ ] **Step 2:** 创建建表 DDL SQL
- [ ] **Step 3:** 创建 ODS 字段映射说明文档

---

## Task 2: 维度表

**Files:**
- Create: `models-phase2/dwd/dim_quality_status.sql`
- Create: `models-phase2/dwd/dim_change_category.sql`
- Create: `models-phase2/dwd/dim_signature_status.sql`

- [ ] **Step 1:** 质量状态维度表（5 种状态 + 布尔标志）
- [ ] **Step 2:** 更改类别维度表（I/II/III + severity）
- [ ] **Step 3:** 文件签署状态维度表

---

## Task 3: 质量域 DWD

**Files:**
- Create: `models-phase2/dwd/biz_dwd_quality_issue.sql`
- Create: `models-phase2/dwd/biz_dwd_quality_measure.sql`

- [ ] **Step 1:** 质量问题事实表 — 字段清洗 + 状态标准化 + 时间维度
- [ ] **Step 2:** 质量跟进措施表 — 关联到质量问题

---

## Task 4: 技术状态域 DWD

**Files:**
- Create: `models-phase2/dwd/biz_dwd_tech_state.sql`
- Create: `models-phase2/dwd/biz_dwd_tech_state_measure.sql`

- [ ] **Step 1:** 技术状态事实表 — 更改类别/签署状态/闭环状态标准化
- [ ] **Step 2:** 技术状态跟进措施表

---

## Task 5: 风险域 DWD

**Files:**
- Create: `models-phase2/dwd/biz_dwd_risk_info.sql`
- Create: `models-phase2/dwd/biz_dwd_risk_measure.sql`

- [ ] **Step 1:** 风险事实表 — 风险等级/闭环状态标准化
- [ ] **Step 2:** 风险跟进措施表

---

## Task 6: 成本域 DWD

**Files:**
- Create: `models-phase2/dwd/biz_dwd_cost_accounting.sql`

- [ ] **Step 1:** 成本事实表 — 预算/实际金额解析 + 时间维度

---

## Task 7: DWS 周期汇总层

**Files:**
- Create: `models-phase2/dws/biz_dws_quality_period_summary.sql`
- Create: `models-phase2/dws/biz_dws_tech_state_period_summary.sql`
- Create: `models-phase2/dws/biz_dws_risk_period_summary.sql`
- Create: `models-phase2/dws/biz_dws_cost_period_summary.sql`

- [ ] **Step 1:** 质量域汇总 — 按 (year, month, project_no, dept) 聚合
- [ ] **Step 2:** 技术状态域汇总 — 按 (year, month, project_no, dept) 聚合
- [ ] **Step 3:** 风险域汇总 — 按 (year, month, project_no, dept) 聚合
- [ ] **Step 4:** 成本域汇总 — 按 (year, month, project_no, dept) 聚合

---

## Task 8: ADS 指标层

**Files:**
- Create: `models-phase2/ads/biz_ads_quality_kpi.sql`
- Create: `models-phase2/ads/biz_ads_tech_state_kpi.sql`
- Create: `models-phase2/ads/biz_ads_risk_kpi.sql`
- Create: `models-phase2/ads/biz_ads_cost_kpi.sql`

- [ ] **Step 1:** 质量域 KPI — PDF P5 全部指标
- [ ] **Step 2:** 技术状态域 KPI — PDF P4 全部指标
- [ ] **Step 3:** 风险域 KPI — T02 矩阵全部指标
- [ ] **Step 4:** 成本域 KPI — T02 矩阵全部指标

---

## Task 9: Schema 验证 + 文档

**Files:**
- Create: `models-phase2/phase2_schema.yml`
- Create: `models-phase2/README.md`

- [ ] **Step 1:** 编写 dbt schema YAML（主键约束、非空、枚举值）
- [ ] **Step 2:** 编写指标映射说明文档
