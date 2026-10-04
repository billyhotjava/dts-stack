# Sprint-41: 语义层整合 Phase 1 — 受控建模逻辑移植

**时间**: 2026-06
**状态**: DONE（F1+F2+F3 全部完成；源表层级/标准码 gating 依赖 SP-2，已文档标注）
**类型**: Architecture Consolidation / Implementation（仅后端 dts-platform）
**实施分支**: 建议从 `v2.2.3` 切 `feat/semantic-consolidation`（不为退役中的 dts-metrics 合 sprint-35b）

## 目标

把 dts-metrics 的两项治理"亮点"——**受控派生指标 DSL** 与 **ELT 分层准入**——移植进 dts-platform 已是权威的 `SemanticModelingService`，让"平台侧权威"整合后不丢失这两项治理能力。绞杀者式：新受控路径与平台现有 permissive 路径并存，按模型级属性 `governanceMode=CONTROLLED` 切换。

## 背景

### 整合大计划（平台侧权威）
经架构评审与 ELT + OpenMetadata 生态分析（见 `assets/`），决策：**dts-platform `SemanticModelingResource` 为唯一权威语义层**（它已是更成熟系统：完整评审工作流 + runs + 业务对象映射 + 可用 BI/血缘注册 + 持久化 + 前端，且已具备 `generateArtifacts`→`DbtFileService` 写 dbt repo 的物化能力）；dts-metrics 的差异化亮点移植进平台，dts-metrics 服务逐步退役。

| 阶段 | 内容 | 状态 |
|------|------|------|
| **SP-1（本 sprint）** | 受控建模逻辑移植（受控 DSL + ELT 分层准入） | READY |
| SP-2 | 语义富化契约（schema.yml meta：dimension/measure/time/grain/标准码角色 + catalog 透出） | 后续 sprint |
| SP-3 | React Flow 可视化图形工作台移植进 dts-platform-webapp | 后续 sprint |
| SP-4 | dts-metrics 服务/webapp 退役与切流（sprint-35b 硬化遗留作废） | 后续 sprint |

### 为什么先做 SP-1
平台现有 `buildMetricExpression`（`SemanticModelingService.java:1628`）已支持 sum/count/count_distinct/avg/max/min/count_if/sum_if/ratio，**但允许 `custom/expression` 原始 SQL**（仅 `safeMetricExpression` 过滤）、缺 `date_trunc/case_when` 结构化函数、无方言感知、无 ELT 分层准入闸。这正是 dts-metrics 受控 DSL（白名单 + 默认拒绝 + 三层防御）与分层准入要补齐的治理缺口。后端边界清晰、不依赖前端，适合首做。

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| F1 | 受控模式基座（governanceMode + 切换骨架 + 存量兼容） | 2 | DONE | P0 |
| F2 | 受控派生指标 DSL（ControlledMetricDslCompiler + 委托） | 3 | DONE | P0 |
| F3 | ELT 分层准入闸（EltLayerGate + 校验集成） | 3 | DONE | P0 |

**依赖**：F2、F3 均依赖 F1 的受控模式切换；F2、F3 之间可并行。

## 进度（2026-06-16，全部 DONE）
- **F1 DONE**：`semantic_model.governance_mode`（CONTROLLED|PERMISSIVE）+ Liquibase 回填 PERMISSIVE + `isControlled` 分流（`c5234a75a` / `01899129f`）。
- **F2 DONE**：`ControlledMetricDslCompiler`（独立可抽取组件，白名单 + 方言 quote + 注入防御，拒原始 SQL）+ `buildMetricExpression` 受控委托（`bef64345d` / `01899129f`）。
- **F3 DONE**：`EltLayerGate`（DWS/ADS 入口、DWD 需 grain、ODS/STG 禁）+ `validateModelForReview` 受控集成 + 错误码（unsafe_expression→422、分层码→400）（`9919cbbd3` / `12560de3d` / `834c755b3`）。
- **验收基线**：`./mvnw -pl dts-platform clean test -Dtest='ControlledMetricDslCompilerTest,EltLayerGateTest,SemanticModelingServiceTest,SemanticModelingResourceTest'` → **36 例全绿**（编译器 15 + 闸 7 + service 12 + resource 2）。
- **架构边界**：SP-1 按"模块化可抽取"落地（受控逻辑在独立 `modeling` 组件，不堆进 2000+ 行 service）；dts-platform 领域解耦排入 **v2.3**。
- **依赖 SP-2 的 followup**：源表层级 gating（禁止从 ODS/STG 源表建模）+ DWD 标准码强制——平台模型当前不携带源表层级/标准码（catalog 按表名 keyed），随 SP-2 语义富化接入；`EltLayerGate` 组件已支持完整规则，等输入即生效。

## 完成标准
- [ ] 模型可标记 `governanceMode=CONTROLLED`；受控模型走严格路径，存量/PERMISSIVE 模型行为字节不变（绞杀者并存）。
- [ ] 受控模式下派生指标仅允许白名单函数、拒绝 raw SQL、注入串被拒、标识符按方言 quote；非白名单/危险表达式 → 422。
- [ ] 受控模式下 ELT 分层准入强制：DWS/ADS 可入、DWD 需 grain+标准码、ODS/STG 禁；违规 → 400（invalid_layer/grain_mismatch/standard_code_required）。
- [ ] 不破坏平台现有 permissive 路径与既有测试。
- [ ] IT 证据：受控 vs permissive 并存 + 与 dts-metrics 黄金 SQL 语义比对，落 `it/evidence/`。

## 非目标
- 不动前端、不动 dts-metrics（继续冻结运行，SP-4 才退役）。
- 不做语义富化契约（SP-2）与可视化工作台移植（SP-3）。
- 不收紧存量 PERMISSIVE 模型（保持兼容；是否批量迁移为后续决策）。

## 相关材料
- SP-1 设计底稿: `assets/sp1-controlled-modeling-design.md`
- 整合路线图与生态分析: `assets/semantic-consolidation-roadmap.md`
- 移植来源（dts-metrics）: `source/dts-metrics/.../service/MetricModelLifecycleService.java`（compileDerivedExpression）、`MetricGraphDraftService.java`（分层诊断）、`MetricVisualAssetResource.parseLayers`
- 移植落点（dts-platform）: `source/dts-platform/.../service/modeling/SemanticModelingService.java`
- 集成测试: `it/README.md`
