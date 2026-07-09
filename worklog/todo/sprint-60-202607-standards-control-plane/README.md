# Sprint-60: 标准管理控制面与建模/指标闭环

**时间**: 2026-07
**状态**: IN_PROGRESS
**类型**: Product Journey / Standards Control Plane / Loop Engineering
**目标**: 把标准管理从“治理资料库/指导文档”升级为可执行控制面，贯通业务术语、数据元、公共码表、标准模板、资产字段、低代码开发、SQL/dbt 逻辑建模、指标管理和发布门禁。

## 背景

当前 DTS 已经具备标准管理、数据开发、指标工作台、低代码向导和黄金链路等能力，但标准管理还没有完全成为开发与发布的强约束。

已有事实：

- 数据元已经能被 SQL 建模页读取，用于字段自动匹配、标准门禁和 `schema.yml` 生成。
- 公共码表已经能作为数据元的 `codeSet`，进入 dbt seed / relationships 测试设计。
- 标准包模板已经覆盖业务术语、数据元、码表目录、码值和来源系统映射。
- 低代码开发向导已经把数据准备、业务对象确认、指标设计、模型候选、发布审核、运行证据串成产品旅程。

主要缺口：

- 业务术语还没有强绑定到业务对象、指标口径和指标版本。
- 标准模板还没有成为低代码模型候选、SQL 建模生成、发布审核的强输入。
- 标准定义后缺少“模型规格 -> 物理模型 -> ETL/SQL 骨架 -> 人工微调 -> 标准反校验”的中间层。
- 指标管理当前主要维护编码、名称、公式、单位和业务对象，缺少术语、数据元、口径版本、标准门禁。
- 标准门禁主要落在 SQL/dbt 字段绑定，还没有完整进入指标 ACTIVE、语义模型 APPROVED、报表数据集发布。

## Loop 定义

本 sprint 采用业务端到端 loop，而不是按页面模块拆孤立任务。

```text
标准包导入
  -> 业务术语确认
  -> 数据元 / 公共码表确认
  -> 资产字段落标
  -> ODS/DWD/DWS/ADS 模型规格生成
  -> 物理表 / 视图 / dbt 模型草稿
  -> ETL/SQL 骨架生成与人工微调
  -> 低代码业务对象确认
  -> SQL/dbt 模型字段标准绑定
  -> schema.yml / dbt tests
  -> 指标口径绑定业务术语和标准字段
  -> 发布门禁
  -> 报表 / API / 数据产品消费证据
```

标准管理的产品定位：

```text
不是文档。
不是只给人看的治理说明。
它是开发、建模、指标、发布和验收的控制面。
```

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| F1 | 标准控制面与事实源收敛 | 3 | READY | P0 |
| F2 | 数据元到逻辑建模强约束 | 4 | IN_PROGRESS | P0 |
| F3 | 业务术语到指标口径绑定 | 4 | READY | P0 |
| F4 | 标准模板到低代码和发布门禁 | 4 | READY | P1 |
| F5 | 标准到物理模型生成与 SQL 微调 | 4 | IN_PROGRESS | P0 |

**统计**: READY=17, IN_PROGRESS=2, DONE=0, BLOCKED=0

## 产品原则

- 标准页面负责维护事实源，开发页面负责消费标准，发布页面负责执行门禁。
- 数据元不是字段说明，而是字段类型、可空性、码表、安全级别和版本漂移的约束源。
- 模型规格不是 SQL 草稿，而是标准、来源、分层、物化形态、字段契约和发布策略的中间事实源。
- SQL 微调不是绕开标准，而是在标准生成的 ETL 骨架上做转换逻辑补充，保存时必须反校验模型契约。
- 业务术语不是词典，而是业务对象、指标口径、报表字段、验收说明的命名和定义源。
- 公共码表不是下拉选项清单，而是来源系统编码归一、dbt seed、relationship test、指标筛选值的标准源。
- 标准模板不是范文，而是模型生成、字段补齐、命名规则、审核清单的模板源。
- 低代码用户看到业务语言；高级开发和证据层可以展示 SQL/dbt/ODS/DWD/DWS/ADS。

## 完成标准

- [ ] 标准管理页能明确显示哪些标准已经被资产、模型、指标、报表引用。
- [ ] 数据元引用关系覆盖资产字段、SQL/dbt 模型字段、语义维度和指标依赖。
- [ ] 标准包、数据元、码表和模板可以生成 ODS/DWD/DWS/ADS 模型规格草稿。
- [ ] 模型规格可以预览物理 DDL、dbt model、schema.yml 和 ETL/SQL 骨架。
- [ ] SQL 人工微调后会回写依赖、血缘和标准契约校验结果。
- [ ] SQL 建模标准门禁不仅可检查，还能作为发布动作的阻断条件。
- [ ] `schema.yml` 生成包含字段标准、码表、密级、版本、绑定来源和必要 dbt tests。
- [ ] 业务对象和指标支持绑定业务术语及版本。
- [ ] 指标 ACTIVE / 语义模型 APPROVED / 报表数据集发布进入标准门禁。
- [ ] 低代码向导的“业务对象确认”和“指标设计”阶段能展示标准缺口和下一步修复入口。
- [ ] 标准模板能在低代码候选模型和 SQL 建模中被选择、应用和审计。
- [ ] source-contract、后端 targeted tests、前端构建和必要浏览器 smoke 通过。

## 非目标

- 不重写现有标准管理、SQL 建模、指标工作台页面。
- 不把所有标准能力塞进一个新页面。
- 不让标准包导入绕过现有权限、版本、评审和审计。
- 不在本 sprint 做完整 AI 自动建模；先做标准驱动的模型规格、DDL/ETL 骨架和人工微调闭环。
- 不把 OpenMetadata 作为唯一真源；DTS 平台内的控制面仍要有可验证状态。

## 风险

- 标准字段过早强制可能阻塞现场快速接入，需要区分 DWD/DWS/ADS 与草稿态。
- 指标与业务术语绑定如果只做 UI 字段，仍会退化成文档；必须进入发布门禁。
- 标准模板如果没有被模型生成和审核消费，会继续停留在模板资料。
- 如果直接从标准生成最终 SQL，现场会因为复杂转换、去重、增量和例外逻辑失控；必须保留人工微调与反校验。
- `schema.yml` 写入必须保持 dbt 项目路径安全和现有模型兼容。
- 当前工作区已有 Sprint-58/59 相关未提交修改，实施时需先确认分支和差异归属。

## 相关事实源

- 标准管理页面：`source/dts-platform-webapp/src/pages/governance/GlossaryPage.tsx`
- 数据元页面：`source/dts-platform-webapp/src/pages/governance/ElementsPage.tsx`
- 标准模板页面：`source/dts-platform-webapp/src/pages/governance/TemplatesPage.tsx`
- 标准包页面：`source/dts-platform-webapp/src/pages/foundation/StandardPackagePage.tsx`
- SQL 建模页：`source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- 指标工作台：`source/dts-platform-webapp/src/pages/modeling/MetricWorkbenchPage.tsx`
- 指标管理页：`source/dts-platform-webapp/src/pages/modeling/SemanticMetricsPage.tsx`
- 低代码开发向导：`source/dts-platform-webapp/src/pages/modeling/LowCodeDevelopmentPage.tsx`
- 标准 API：`source/dts-platform-webapp/src/api/platformApi.ts`
- 语义 API 类型：`source/dts-platform-webapp/src/api/semanticModelingApi.ts`
- 后端建模服务：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- 资产字段标准映射：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/catalog/CatalogSchemaResource.java`

## 2026-07-09 进展

- 已新增 `standard_binding_draft` 后端快照：数据元页生成字段落标结果时会保存可审计草稿，低代码建模和 SQL 建模通过 `standardDraftId` 读取。
- 已保留浏览器会话草稿兜底：后端快照不可用时，用户仍可继续完成低代码/SQL 建模体验。
- 已接通 SQL 建模消费：标准草稿可创建模型草稿、应用到当前模型标准绑定，并填入可微调 SQL 骨架。
- 已验证：`StandardBindingDraftServiceTest`、`dataDevelopmentWorkbench.source-contract.test.ts`、`pnpm build`、`git diff --check`、GitNexus `detect_changes`。

## 参考材料

- 边界说明：`assets/standards-control-plane-boundary.md`
- 标准到物理模型竞品参照：`assets/standards-to-physical-model-competitor-notes.md`
- 集成测试计划：`it/README.md`
