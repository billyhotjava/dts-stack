# Sprint-13: 自助 BI 与可视化语义层（Phase 1）

**时间**: 2026-04
**状态**: READY
**类型**: Implementation（新架构落地，非破坏——新语义层与老 Metabase fork 并行，本 Sprint 不下线老模块）
**目标**: 在 dbt 产出的 DWS/ADS 之上建一层**薄语义层**，让分析师能通过拖拽组合指标/维度/join 建 Card，让业务用户通过 Dashboard 自助筛选和下钻；工程师继续用 dbt 声明原子指标和 join 关系图作为唯一真源。

## 背景

### 现状问题

1. **客户无法自助分析**：现在所有 ELT/建模都走 dbt，业务方不懂 SQL，拿不到 DWD/DWS 的数据。
2. **老 Metabase fork 功能单薄**：40 张表的 fork 只撑起了"卡片 + 仪表盘"，指标口径散落在 MBQL 里，没有语义层，相同的"营收"能跑出三个数。
3. **指标页面孤岛化**（已调研）：`IndicatorsPage.tsx` 是 1812 行的巨无霸 + 10 个 Modal；另 6 个指标页面写了但未挂菜单；"新建指标"只有弹窗，没独立路由。
4. **Sprint-5 的 dbt 生成引擎方向反了**：现在是"平台生成 dbt 代码"，实际客户工程团队有成熟 dbt 仓库，正确方向应是"平台消费 dbt manifest"。

### 本 Sprint 解决什么

建一个 **dbt-native 的薄语义层 + 受控可视化建模 + 独立 Card Editor 页面**，替换老 `IndicatorsPage` 的 Modal 工作流。参考架构是 **Lightdash**（不是 Cube——复杂度差一个数量级），因为 dbt 已经把最难的部分（清洗 + 多表关联 + 聚合口径）都做完了。

### 三类用户分层（重要）

| 角色 | 能做 | 做什么的入口 |
|---|---|---|
| 数据工程师 | dbt 改 SQL + `schema.yml` 声明原子指标 / join 关系图 | IDE + git PR |
| 数据分析师（power user） | 在白名单内用画布组合 join、建派生指标、组 Card/Dashboard | 新 Card Editor + 模型画布 |
| 业务用户 | 看 Dashboard、筛选、下钻、订阅、导出 | Dashboard 消费界面（已有，本 Sprint 微调） |

**关键不变量**：任意两表能否 join、某列是否可做 measure/dimension——**全部由 dbt `schema.yml` 决定**，前端不允许突破白名单。

## 约束与非目标

### 硬约束

1. **LLM 现场不可部署**（客户安全要求）——本 Sprint 不包含任何 LLM/NL→查询能力，但 schema 预留 `description`、`synonyms`、`sample_queries` 字段，未来直接对接。
2. **dbt 是指标唯一真源**——`GovIndicatorDefinition` 从 dbt manifest **同步生成**（只读快照），不提供 UI 新建入口。
3. **不做多源联邦**——查询只打同一个数据仓库。
4. **不自研 Cube Store**——预聚合直接用 DB 物化视图，由 dbt 的 `materialized: incremental` / 自定义 `materialized: aggregate` 管理。
5. **不做任意表自由 join**（C 档）——画布只能连接 dbt `joins:` 白名单声明过的路径。
6. **不下线老 Metabase fork**——AnalyticsCard / AnalyticsDashboard / AnalyticsCollection / AnalyticsPublicLink 等持久化表继续使用；本 Sprint 只替换"查询编译"路径。

### 非目标

- 老 Card 批量迁移到新语义层（后续 Sprint）
- 派生指标之外的复杂 SQL（窗口函数 partitioning、递归 CTE）
- 行级权限的 policy 策略库（本 Sprint 只支持单条 predicate 表达式）
- Dashboard 参数联动大改造（沿用老 Metabase fork 能力）
- 国产化数据库特殊方言 driver（仅保证现有 PG/Doris 可跑）
- 查询结果导出 PDF（沿用老 Card 导出）

## 架构概览

```
┌──────────────────────────────────────────────────────────────┐
│  dbt repo（工程师工作流）                                     │
│  models/                                                     │
│    ├─ ads_*.sql / dws_*.sql                                  │
│    └─ schema.yml                                             │
│        meta.dts:                                             │
│          metrics: [...]   ← 原子指标                         │
│          dimensions: [...]                                   │
│          joins: [...]     ← 白名单关系图                     │
│          exposed_to_modeler: true                            │
│          security_level: INTERNAL                            │
└──────────────────────┬───────────────────────────────────────┘
                       │ dbt compile → manifest.json
                       │ ManifestIngestor 周期性同步
                       ▼
┌──────────────────────────────────────────────────────────────┐
│  dts-platform/src/.../service/semantic/  (F2 + F3 + F4)     │
│  ├─ ManifestIngestor    写 GovIndicatorDefinition 快照       │
│  ├─ JoinGraphRegistry   join 白名单 + 可达性查询             │
│  ├─ MetaResource        /metric/meta                         │
│  ├─ QueryCompiler       measures+dims+filters → SQL          │
│  │   ├─ SingleModelCompiler                                  │
│  │   ├─ MultiModelCompiler（沿 join 路径）                  │
│  │   └─ FanoutDetector + SymmetricAggregate                 │
│  ├─ ExpressionParser    派生指标 DSL → SQL 片段              │
│  ├─ SecurityInjector    密级/行级 WHERE 注入                 │
│  ├─ VirtualDatasetService  用户画布持久化                    │
│  └─ ResultCache (Redis)                                      │
└──────────────────────┬───────────────────────────────────────┘
                       │ JDBC
                       ▼
               DWS/ADS（现有 dbt 产出）
                       │
                       │ REST + Arrow IPC
                       ▼
┌──────────────────────────────────────────────────────────────┐
│  platform-webapp 新 Card Editor  (F5)                        │
│  /bi/card/new | /bi/card/:id/edit                            │
│  ├─ 指标树（来自 dbt 同步的快照，只读）                      │
│  ├─ 维度树                                                  │
│  ├─ 模型画布（ReactFlow，受控 join）                         │
│  ├─ 派生指标编辑器（公式引用，不是 SQL 编辑器）              │
│  ├─ SQL 实时预览（只读）                                     │
│  └─ 图形选择 + Arrow 结果渲染                                │
└──────────────────────────────────────────────────────────────┘
```

## 任务引用约定

本 Sprint 各 Feature 内部同样从 `T01` 重新编号。

- **Feature 内引用**：允许写 `T01`
- **跨 Feature / 总览 / IT / 日报引用**：必须写成 `F#/T#`
- 例如：`F1/T01`、`F6/T03`

## Feature 列表

| ID | Feature | Task 数 | 状态 | 依赖 |
|----|---------|---------|------|------|
| F1 | 接口合约与 DSL 规范 | 4 | READY | — |
| F2 | 语义层后端核心 | 5 | READY | F1 |
| F3 | Join 与虚拟数据集 | 4 | READY | F1, F2 |
| F4 | 派生指标引擎 | 3 | READY | F1, F2 |
| F5 | 前端建模与 Card Editor | 5 | READY | F1, F2, F3, F4 |
| F6 | 治理护栏与提升通道 | 4 | READY | F3, F5 |

**共 25 tasks。**

## 阶段节奏（本 Sprint 内）

```
阶段 1（3 天）─ F1 全部 → 合约锁定评审通过
阶段 2（5 天）─ F2 + F3-T01 → 单表查询端到端跑通，3 个指标 × 2 个维度 可返回结果
阶段 3（4 天）─ F3 剩余 + F4 → 两表 join 查询跑通，FanoutDetector 对 1:N 自动改写，派生指标 3 个样本
阶段 4（5 天）─ F5 全部 → 前端 Card Editor 独立路由 + 画布 + 派生指标编辑器能创建 Card 并保存
阶段 5（3 天）─ F6 全部 → 护栏/密级/熔断/PR 生成上线
阶段 6（2 天）─ IT 全链路验证 + 文档
```

## 完成标准

- [ ] `schema.yml meta.dts` 规范定稿，3 张示例 model 已改造（写入 `it/sample-schema-yml/`）
- [ ] `GovIndicatorDefinition` 通过 ManifestIngestor 同步，前端指标树能渲染
- [ ] 单表 group-by 查询端到端跑通：`/metric/query` 返回正确结果（手工 SQL 校验）
- [ ] 两表 join 查询端到端跑通，其中至少 1 例涉及 1:N fanout，FanoutDetector 自动改写为 CTE 预聚合
- [ ] 派生指标能在 Card Editor 用"公式 + 引用"方式创建，SQL 预览可见
- [ ] 新 Card Editor 有独立路由 `/bi/card/new`，**不再是 Modal**
- [ ] 虚拟数据集能保存，读取时自动编译 SQL
- [ ] 密级超限查询被拦截（422），密级标签在画布上正确传导
- [ ] 慢查询熔断：超过 30 秒默认超时，被标记并记录
- [ ] "提升到 dbt" 按钮能生成 PR diff（提交到审批流，不自动 merge）
- [ ] 老 `IndicatorsPage.tsx` 的"新建指标"Modal 被禁用，替换为跳转到新 Card Editor 的按钮（过渡期）
- [ ] IT 证据：3 个端到端场景录屏 + SQL 校验 + 性能数据存 `it/evidence/`

## 范围外（留给后续 Sprint）

- 老 Card 到新语义层的批量迁移工具（Sprint-14+）
- Dashboard 参数联动改造 + 业务用户消费界面优化（Sprint-14+）
- 物化视图自动命中路由（本 Sprint 只做 Redis 结果缓存）
- Git-Ops：dbt 仓库 PR 自动化 review robot
- LLM / NL→指标（现场安全要求不允许，长期计划）
- Arrow IPC 流式分页（本 Sprint 只做一次性 Arrow IPC）
- 国产化数据库深度适配
- 指标版本化回放（现有 `GovIndicatorVersion` 保留，本 Sprint 不改造）

## 与已有 Sprint 的关系

| 相关 Sprint | 关系 |
|---|---|
| Sprint-5（指标驱动建模体系，DONE） | 本 Sprint **反向重构**——把"平台生成 dbt 代码"改为"平台消费 dbt manifest"。Sprint-5 的 `DbtIndicatorGenerator` 保留为旁路工具，不作为主数据流。 |
| Sprint-8（指标中心重构，DONE） | Sprint-8 已建 `IndicatorCenterPage` 外壳 + 主题域树，本 Sprint 继承该外壳，替换右侧内容为新 Card Editor 入口。 |
| Sprint-7（数据目录） | 依赖 Sprint-7 的 `DatasetPicker` 和元数据同步能力。 |
| Sprint-12（BI 大屏响应式） | 独立；大屏 `useCardDataSource` 后续 Sprint 再切到新语义层 API。 |

## 术语约定

- **原子指标**（Atomic Metric）：dbt 声明的 `measure`，对应物理列 + 聚合函数。不可在 UI 创建。
- **派生指标**（Derived Metric）：在原子指标之上做 ratio / growth / filtered subset / window function 的计算。分析师可在 UI 创建。
- **原生 SQL Card**（Escape Hatch）：绕过语义层直接写 SQL 的卡片。本 Sprint 保留老路径，但新 Card 不允许创建。
- **虚拟数据集**（Virtual Dataset）：用户在画布上组合的 "base model + joins + derived metrics"，持久化为 JSON；查询时才编译成 SQL。
- **提升**（Promote）：把虚拟数据集固化为 dbt model + schema.yml PR。

## 参考

- [Lightdash](https://www.lightdash.com/) — 参考架构（dbt-native semantic layer + explore）
- [Looker Symmetric Aggregates](https://cloud.google.com/looker/docs/best-practices/understanding-symmetric-aggregates)
- [Cube `joins`](https://cube.dev/docs/product/data-modeling/reference/joins) — join 关系声明 schema 参考
- [dbt metrics v2 spec](https://docs.getdbt.com/docs/build/metrics-overview) — 可能作为本项目 meta.dts 的兼容格式
- [Apache Arrow IPC](https://arrow.apache.org/docs/format/Columnar.html) — 前后端列式传输协议
- [ReactFlow](https://reactflow.dev/) — 模型画布前端库候选
