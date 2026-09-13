# Sprint-60: 建模 vNext、标准控制面与 dbt 双模式运行闭环

**时间**: 2026-07
**状态**: IN_PROGRESS
**类型**: Architecture / Standards Control Plane / Frontend + API + Backend + dbt + Airflow
**目标**: 建立一个全新的建模版本，并把标准管理升级为开发与发布控制面：普通用户通过业务建模生成可运行的 dbt 产物，高级开发直接维护 dbt SQL；标准、模型、指标和发布最终统一进入 Addax、dbt、Airflow、PostgreSQL 运行链路。

## 背景

旧版本主要依赖工程师直接编写 dbt SQL，能力完整但普通用户无法理解业务对象、粒度、标准和模型之间的关系。新版本不继续修补旧模型，而是建立 ModelSpec 驱动的建模内核，同时保留旧 dbt 项目作为可导入、可登记和可继续运行的历史资产。

同一 Sprint 的标准控制面轨道进一步把已有标准管理、数据元、公共码表、标准模板、资产字段、低代码、SQL/dbt 建模和指标工作台串成可执行闭环，避免标准停留在治理资料库。

## 核心决策

- 业务对象是业务语义锚点，不等于物理表或 dbt 模型。
- ModelSpec 是建模页面的结构化产物，负责表达对象、粒度、字段标准、维度、指标和目标层级。
- 设计器模式由 ModelSpec 生成 dbt SQL、schema.yml、测试和文档。
- dbt 原生模式以 SQL 和 manifest 为实现事实源，DTS 负责登记、血缘、治理和运行。
- 两种模式必须显式标识，禁止无提示的双向覆盖。
- 新 API 使用 `/api/modeling/*`；旧 `/api/semantic/*` 保留兼容，不新增 `/v2` URL 命名空间。
- Addax 负责 ODS 接入，dbt 负责转换，Airflow 负责编排，PostgreSQL 负责业务数据和建模元数据。
- PJM“项目节点计划闭环”只作为黄金主线与回归夹具，不把 PJM 业务字段硬编码进平台。
- 标准页面维护事实源，开发页面消费标准，发布页面执行标准门禁；标准不绕过权限、版本、评审和审计。
- 数据元约束字段类型、可空性、码表、安全级别和版本漂移；公共码表同时服务来源归一、dbt seed、relationship test 和指标筛选。
- 模型规格是标准、来源、分层、物化形态、字段契约和发布策略的中间事实源；SQL 微调必须回写并反校验这些契约。
- 业务术语是业务对象、指标口径、报表字段和验收说明的命名/定义源；项目空间仍是可选的协作边界。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 建模 vNext 核心契约与 PJM 黄金主线 | 3 | DONE（契约、fixture、旧语义/dbt 只读兼容已覆盖） |
| F2 | 业务对象/模型台账与低代码前端 | 4 | DONE（vNext 台账、缺口上下文、dbt 入口、服务端发布门禁已接入） |
| F3 | 建模 API 契约与兼容入口 | 4 | IN_PROGRESS（REST/权限/旧读取回归已覆盖，OpenAPI 生成物待部署验收） |
| F4 | PostgreSQL 持久化与后端治理服务 | 4 | IN_PROGRESS（持久化、权限、审计、发布门禁已覆盖，正式审批回写待验收） |
| F5 | dbt 产物生成与 SQL 双模式 | 3 | IN_PROGRESS（parse 通过；test 受缺少 3 张源表阻断） |
| F6 | Addax + Airflow + PostgreSQL 运行闭环 | 4 | IN_PROGRESS（适配器、回调、状态机已覆盖，外部服务实投待验收） |
| F7 | TDD、Playwright 与旧资产迁移验收 | 4 | IN_PROGRESS（真实域名 Playwright 通过，真实租户与 Chrome95 待验收） |

## 端到端黄金主线

```text
项目节点计划闭环
  -> 项目节点业务对象
  -> DWD 明细 ModelSpec
  -> dbt SQL / schema.yml / tests
  -> DWS 月度进度汇总
  -> ADS 进度 KPI
  -> Airflow 调度
  -> PostgreSQL 数据集
  -> 报表、数据服务、运行证据
```

## 完成标准

- [ ] 普通用户无需编写 SQL 即可完成业务对象、粒度、标准、模型和发布申请。
- [ ] 高级开发可以直接编辑 dbt SQL，并在 DTS 中查看模型登记、字段、血缘、测试和运行状态。
- [x] 设计器模式和 dbt 原生模式都生成或绑定可执行 dbt 产物。
- [ ] Addax → PostgreSQL ODS → dbt → Airflow → PostgreSQL DWD/DWS/ADS 有可追踪运行证据。
- [x] 旧 `/api/semantic/*` 和旧 dbt 项目不会被破坏，可导入为新版本资产。
- [ ] PJM 黄金主线通过前端、API、后端、dbt 编译和 Playwright 验收。
- [ ] 新增代码遵循 TDD：先 RED，再 GREEN，再重构；单元、集成和 E2E 覆盖率目标不低于 80%。

## 参考资产

- 架构说明：`assets/modeling-vnext-architecture.md`
- PJM 黄金主线：`assets/pjm-golden-path.md`
- API 矩阵：`assets/modeling-api-contract-matrix.md`
- 集成验收：`it/README.md`

## 标准控制面轨道（由 todo 标准控制面 Sprint 迁移合并）

### 标准控制面 Loop

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

### 标准控制面 Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| S-F1 | 标准控制面与事实源收敛 | 3 | READY | P0 |
| S-F2 | 数据元到逻辑建模强约束 | 4 | IN_PROGRESS | P0 |
| S-F3 | 业务术语到指标口径绑定 | 4 | READY | P0 |
| S-F4 | 标准模板到低代码和发布门禁 | 4 | READY | P1 |
| S-F5 | 标准到物理模型生成与 SQL 微调 | 4 | IN_PROGRESS | P0 |

### 标准控制面产品原则

- 标准页面负责维护事实源，开发页面负责消费标准，发布页面负责执行门禁。
- 数据元不是字段说明，而是字段类型、可空性、码表、安全级别和版本漂移的约束源。
- 模型规格不是 SQL 草稿，而是标准、来源、分层、物化形态、字段契约和发布策略的中间事实源。
- SQL 微调不是绕开标准，而是在标准生成的 ETL 骨架上做转换逻辑补充，保存时必须反校验模型契约。
- 业务术语不是词典，而是业务对象、指标口径、报表字段、验收说明的命名和定义源。
- 公共码表不是下拉选项清单，而是来源系统编码归一、dbt seed、relationship test、指标筛选值的标准源。
- 标准模板不是范文，而是模型生成、字段补齐、命名规则、审核清单的模板源。
- 低代码用户看到业务语言；高级开发和证据层可以展示 SQL/dbt/ODS/DWD/DWS/ADS。

### 标准控制面完成标准

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

### 标准控制面非目标、风险与事实源

- 不重写现有标准管理、SQL 建模、指标工作台页面，不把所有标准能力塞进一个新页面。
- 不让标准包导入绕过现有权限、版本、评审和审计；不在本 Sprint 做完整 AI 自动建模。
- 标准字段过早强制可能阻塞现场快速接入，需要区分 DWD/DWS/ADS 与草稿态。
- 指标与业务术语绑定必须进入发布门禁，不能退化成 UI 备注。
- `schema.yml` 写入必须保持 dbt 项目路径安全和现有模型兼容。
- 相关事实源包括 `GlossaryPage.tsx`、`ElementsPage.tsx`、`TemplatesPage.tsx`、`StandardPackagePage.tsx`、`SqlModelingPage.tsx`、`MetricWorkbenchPage.tsx`、`SemanticMetricsPage.tsx`、`LowCodeDevelopmentPage.tsx`、`platformApi.ts`、`semanticModelingApi.ts` 和 `ModelingSqlModelService.java`。

### 标准控制面既有进展

- 已新增 `standard_binding_draft` 后端快照，数据元页生成字段落标结果时会保存可审计草稿，低代码建模和 SQL 建模通过 `standardDraftId` 读取。
- 已保留浏览器会话草稿兜底；后端快照不可用时，用户仍可继续完成低代码/SQL 建模体验。
- 已接通 SQL 建模消费：标准草稿可创建模型草稿、应用到当前模型标准绑定，并填入可微调 SQL 骨架。
- 已验证 `StandardBindingDraftServiceTest`、`dataDevelopmentWorkbench.source-contract.test.ts`、`pnpm build`、`git diff --check` 和 GitNexus `detect_changes`。
