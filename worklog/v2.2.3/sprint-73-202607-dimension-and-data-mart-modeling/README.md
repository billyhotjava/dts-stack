# Sprint-73: 数据集市规划与维度建模产品化

**时间**: 2026-07  
**状态**: IN_PROGRESS（F1 及 F2～F4 代码已完成；F0、真实运行时验收和 F5 发布交接证据待整体构建/容器环境）
**类型**: Warehouse Planning / Dimension Modeling / Product Convergence / Full-stack  
**目标**: 让建模人员能在同一条主线中管理数据集市、登记业务维度、创建并完善维度表，清楚区分业务归属、应用范围、来源、命名、历史处理和数据保留，并在发布后由资产台账接收真实资产。

## 背景与价值

DataWorks 将“概念维度”和“逻辑维度表”分成两个对象：维度定义分析视角，维度表承载字段、标准、码表、分区和物化配置；公共层按数据域组织，应用层按数据集市组织。DTS 已在 Sprint-67 建立 `DimensionDefinition → ModelSpec revision` 引用，但当前仍有以下断点：

1. DTS 没有数据集市管理对象，只有业务分类和下游资产台账；
2. 维度登记仅包含名称、定义、责任人、复用范围，层级虽有契约但 UI 不可维护，也没有维度属性；
3. 新建维度表只选择计划、业务分类、业务维度和模型名称，字段映射、来源、物理命名、装载方式、SCD 和保留期分散在后续页面，用户不知道去哪里完成；
4. 计划级“命名规则”“历史保留”容易被误解成模型名称规则或数据保留期；
5. 概念设计、来源登记、物化资产和资产台账的先后边界不够清楚。

本 Sprint 参考：

- DataWorks《创建概念模型：维度》：<https://help.aliyun.com/zh/dataworks/user-guide/create-a-dimension>
- DataWorks《创建逻辑模型：维度表》：<https://help.aliyun.com/zh/dataworks/user-guide/create-a-dimension-table/>

详细差异见 [`assets/dataworks-reference-review.md`](assets/dataworks-reference-review.md)。

## 架构决策记录 (ADR)

| 决策点 | 选择 | 理由 | 影响 |
|--------|------|------|------|
| ADR-01 数据集市 owner | 新增 `DataMart` 规划对象，不复用资产台账 | 数据集市描述面向应用的建设范围；资产台账描述已经登记/发布的资产 | 数据集市放在数仓规划控制面，资产台账保持下游只读消费 |
| ADR-02 页面入口 | 在既有“业务分类”页增加“数据集市”工作区；计划详情的“业务分类”改为“业务范围”并同时选择分类/集市；不新增一级菜单 | 遵守 DTS 默认不新增页面/菜单的不变量 | 复用 `/governance/subjects` 与 `/modeling/plans/:planId/baseline` |
| ADR-03 分类与集市关系 | 业务分类仍由 `catalog_domain` 唯一拥有；一个数据集市可关联多个业务分类，一个业务分类可服务多个数据集市 | 数据集市常跨域消费，不能硬塞进单棵分类父子树 | 新增 `modeling_data_mart_domain` 多对多关系 |
| ADR-04 维度与维度表 | `DimensionDefinition 1 → N DIMENSION ModelSpec`；同一计划、同一集市范围、同一维度定义默认仅允许一个未归档实现 | 允许跨计划/集市/实现策略复用，同时阻止无意重复建表 | 服务端冲突返回 409，并给出现有维度表入口 |
| ADR-05 概念与来源 | 登记业务维度不要求物理来源；维度表草稿允许无来源；进入实现前必须绑定已确认物理来源、锁定上游模型或受控生成策略 | 来源属于实现证据，不属于分析视角本身 | 修正“概念设计就要选源表”的歧义 |
| ADR-06 分层 | 本 Sprint 保持 `DIMENSION → DWD`，不照搬独立 DIM 层 | 当前 ModelSpec、指标引用和发布门禁均以该矩阵为真值，新增 DIM 层会扩大迁移范围 | 数据集市是应用范围，不冒充物理分层 |
| ADR-07 四类相近术语 | 分成“物理表名规则”“装载方式”“维度历史处理(SCD)”“数据保留期限”；模型修订历史由系统管理，不作为用户策略 | 消除“命名规则指谁”“历史保留是什么”的歧义 | UI、DTO、校验码和帮助文案统一 |
| ADR-08 属性所有权 | 维度定义保存语义属性；ModelSpec 字段保存物理字段，并通过 `dimensionAttributeCode` 显式映射 | 既避免维度目录只是名称台账，也避免复制物理字段真值 | `attributes_json` 为语义快照，字段仍由 ModelSpec revision 拥有 |
| ADR-09 资产交接 | 仅 PUBLISHED 的物理实现注册到统一资产目录；资产台账不得成为建模前置输入 | 保持模型控制面和资产控制面单向可追溯 | 复用 `CatalogAssetType/CatalogAssetKey`，不新建维度表资产台账 |

## 端到端契约链 (Vertical Slice)

| 层 | 契约/落点 | 签名要点 |
|----|-----------|----------|
| UI 入口 | `/governance/subjects?tab=data-marts` | 在现有业务分类页管理数据集市；具备空/加载/错误/成功四态 |
| UI 规划 | `/modeling/plans/:planId/baseline?tab=categories|data-marts` | 同一“规划基线”内先确认业务分类，再纳入覆盖这些分类的 DataMart |
| UI 维度 | `/modeling/dimensions?planId=&domainId=&dataMartId=` | 登记 DOMAIN/DATA_MART 范围的业务维度，维护属性和层级 |
| UI 维度表 | `/modeling/models` → `/modeling/models/:modelSpecId` | 从业务维度创建维度表草稿，在详情页完善字段、来源、命名、装载、SCD、保留期 |
| DataMart API | `GET/POST /api/modeling/data-marts`；`GET/PUT /api/modeling/data-marts/{id}`；`POST .../{id}/confirm|retire` | DTO 见 `assets/contract-design.md`；POST 幂等，PUT 使用 ETag/CAS |
| 计划范围 API | `GET/PUT /api/modeling/warehouse-plans/{planId}/baseline/data-marts` | `{planId,dataMartIds,version}`；仅 CURRENT DataMart 可纳入并使用 version CAS |
| 维度 API | 复用 `/api/modeling/dimension-definitions` | 增加 `scopeType/dataMartId/attributes`，现有记录迁移为 `DOMAIN` |
| ModelSpec API | 复用 `/api/modeling/model-specs` | 增加 `dataMartId` 与维度字段/实现策略契约，不新建第二套模型 API |
| 命名校验 API | `POST /api/modeling/warehouse-plans/{planId}/naming/validate` | `{modelType,layer,physicalName}` → `{valid,normalizedName,issues[]}` |
| Service | `DataMartApplicationService`、现有 `DimensionDefinitionApplicationService`、`ModelSpecApplicationService`、`ModelSpecStageGateService` | 校验可见分类、集市范围、revision pin、唯一活动实现、实现前来源门禁 |
| 数据 | `modeling_data_mart*`、`modeling_warehouse_plan_data_mart`；扩展 `modeling_dimension_definition*`、`modeling_model_spec*` | 精确表/约束见 `assets/contract-design.md` |
| 迁移 | `20260727_01_modeling_data_mart.xml`、`20260727_02_dimension_scope_attributes.xml`、`20260727_03_model_spec_mart_dimension_fields.xml` | expand → backfill → validate；不删除旧列 |
| 发布交接 | 复用 Sprint-69 ReleaseCandidate 与统一资产注册 | 仅成功发布的物理资产进入资产台账，稳定锚点为 `modelSpecId/revision` |

## 现状勘察账本 (Context Ledger)

| # | 事实 | 证据（文件:行/实测） |
|---|------|----------------------|
| 1 | 数仓规划当前只有“建设规划、业务分类”，维度建模已有“维度目录、模型中心” | `source/dts-admin/src/main/resources/config/data/portal-menu-seed.json:108`、`:115`、`:123`、`:174` |
| 2 | 资产台账是独立下游入口 `/catalog/assets/ledger` | `source/dts-admin/src/main/resources/config/data/portal-menu-seed.json:344` |
| 3 | 业务分类页面使用 `CatalogDomain` 树，字段为名称、编码、负责人、父分类和说明 | `source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.tsx:63`、`:860` |
| 4 | `CatalogDomain` 已有父子层级、生命周期和访问策略，但不是数据集市 | `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/catalog/CatalogDomain.java:18`、`:31`、`:35` |
| 5 | 业务维度契约已有定义、责任人、复用范围、层级和 revision，但没有语义属性或数据集市范围 | `source/dts-platform-webapp/src/pages/modeling/dimensionDefinitionContract.ts:4`、`:16` |
| 6 | 维度登记 UI 目前只编辑业务分类、名称、定义、责任人、复用范围 | `source/dts-platform-webapp/src/pages/modeling/components/DimensionDefinitionCreateDrawer.tsx:231` |
| 7 | 新建 DIMENSION ModelSpec 已要求选择 CURRENT 业务维度，并固定引用 revision | `source/dts-platform-webapp/src/pages/modeling/components/ModelSpecCreateDrawer.tsx:378` |
| 8 | 当前关系是非唯一索引，允许一个维度定义被多张维度表引用 | `source/dts-platform/src/main/resources/config/liquibase/changelog/20260724_01_dimension_definition.xml:208` |
| 9 | 当前 ModelSpec 类型矩阵为 `DIMENSION/FACT→DWD、SUMMARY→DWS、APPLICATION→ADS` | `source/dts-platform-webapp/src/pages/modeling/modelSpecV2Contract.ts:61` |
| 10 | ModelSpec 已有来源、字段标准/码表、SCD、层级等基础契约，可扩展而非另造模型 | `source/dts-platform-webapp/src/pages/modeling/modelSpecV2Contract.ts:103`、`:129`、`:140` |
| 11 | 当前计划策略把 `naming_policy_ref` 与 `history_policy` 放在计划级，尚未表达物理表保留期 | `source/dts-platform/src/main/resources/config/liquibase/changelog/20260718_01_warehouse_plan_canonical.xml:206` |
| 12 | 2026-07-26 当前运行库：6 个分类、503 个资产、2 个计划、1 个 CURRENT 维度定义、1 个 DIMENSION ModelSpec；无 DataMart 表；该 ModelSpec 尚未绑定维度定义 | `assets/domain-profile.md` 实测 |
| 13 | 相关 WarehousePlan 与 DimensionDefinition 迁移已在当前库执行 | `it/baseline.md` P3 |
| 14 | GitNexus 索引比 HEAD 落后 6 个提交，实施前须重新 analyze | 2026-07-26 `gitnexus list_repos` |

**开放问题**：

- 客户生产数据量、数据集市跨分类分布和历史维度表重复率尚未画像，归属 F0/T02；不得以当前本地数据量代替生产容量。
- 当前真实认证/API/Chrome95 验收链未执行，归属 F0/T01；受影响 Feature 保持 DRAFT。

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|------|------|------|------|-----------------|
| G0 | 交付基线 | GAP | `it/baseline.md` | F0/T01 |
| G0 | 领域与数据画像 | GAP | `assets/domain-profile.md` | F0/T02 |
| G0 | 领域不变量自检 | PASS | ADR-01/02/06/09 | - |
| G1 | 契约链贯通 | PASS | 本文档 §端到端契约链、`assets/contract-design.md` | - |
| G1 | 非功能预算 | PASS | `assets/nfr-budget.md` | - |
| G3 | 发布安全 | PENDING | `assets/release-plan.md` 已完成，待真实迁移与回滚演练 | F5/T02 |
| G4 | 可运维性 | PENDING | `assets/runbook.md` 已完成，待容器环境验证 | F5/T02 |
| G4 | DoD 验收 | PENDING | `it/` | F5/T02 |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|----|---------|---------|--------|------|
| F0 | 交付与生产数据基线 | 2 | P0 | READY |
| F1 | 概念、关系与契约收敛 | 2 | P0 | DONE |
| F2 | 数据集市规划闭环 | 3 | P0 | IN_PROGRESS（代码完成，IT 待验证） |
| F3 | 业务维度目录增强 | 3 | P0 | IN_PROGRESS（代码完成，IT 待验证） |
| F4 | 维度表设计体验闭环 | 3 | P0 | IN_PROGRESS（代码完成，IT 待验证） |
| F5 | 实现发布与资产交接 | 2 | P0 | IN_PROGRESS（门禁/文档完成，运行时交接待验证） |

**Feature 统计**: READY=1, IN_PROGRESS=4, DONE=1, BLOCKED=0
**依赖顺序**: F0/F1 → F2 → F3 → F4 → F5。F2 数据持久化与 F3 UI 壳层可在 G0 通过后按冻结契约并行。

## 本轮实现与验证（2026-07-26）

| 项目 | 结果 | 说明 |
|------|------|------|
| DataMart、计划范围、维度范围/属性、ModelSpec 实现策略 | CODE_COMPLETE | 后端契约、仓储、服务、REST、前端入口和迁移已落地 |
| 前端定向契约测试 | PASS（11/11） | 数据集市、维度登记、维度表创建与三阶段详情链 |
| 后端 Sprint-73 定向测试 | PASS（12/12） | DataMart 契约、三份 Liquibase 迁移、维度/ModelSpec 契约 |
| Java 生产代码编译 | PASS | 定向 Maven 测试过程中完成 1201 个生产源文件编译 |
| 前端静态检查 | PASS | 本轮 20 个 TypeScript/TSX 文件通过 Biome |
| 整体前端编译、容器构建、真实 PostgreSQL/API/Chrome95 | PENDING | 按交付安排在全部小问题合并后统一执行 |

已知基线问题：

- 仓库原有 `DimensionDefinitionApplicationServiceTest` 编译产物包含未解析类型字节码，单独纳入该旧测试时会在 Surefire 启动阶段失败。
- 扩展执行旧 `ModelSpecStageGateServiceTest` 时，仍有两个既有夹具偏差：来源失效用例没有构造持久化实现记录，权限校验用例对一次 `evaluateAll` 只期望一次调用；本轮新增测试与生产代码编译不受影响。

## 追溯矩阵 (Traceability)

| 需求点 | Feature | 关键 Task | 验收证据位置 |
|--------|---------|-----------|--------------|
| 数据集市不是资产台账，且能独立管理 | F1/F2 | F1/T01、F2/T01、F2/T02 | `it/IT-01-data-mart.md` |
| 创建维度时能选择业务范围/数据集市 | F2/F3 | F2/T02、F3/T01、F3/T02 | `it/IT-02-dimension-scope.md` |
| 维度与维度表关系清晰并阻止误重复 | F1/F4 | F1/T01、F4/T01 | `it/IT-03-dimension-table-create.md` |
| 来源不阻断概念设计，实现前必须补齐 | F4/F5 | F4/T02、F5/T01 | `it/IT-04-source-gate.md` |
| 命名、装载、SCD、保留期各自可解释可校验 | F4 | F4/T03 | `it/IT-05-policy-semantics.md` |
| 发布后进入资产台账且可回溯模型 revision | F5 | F5/T01、F5/T02 | `it/IT-06-publish-ledger.md` |

## 完成标准

- [ ] 用户在现有业务分类页面创建并确认数据集市，能关联多个可见业务分类。
- [ ] 建设计划在同一“业务范围”区确认业务分类与数据集市，后续页面不重新猜测范围。
- [ ] 用户登记 DOMAIN 或 DATA_MART 范围的业务维度，能维护语义属性和层级。
- [ ] 用户从维度创建维度表；重复实现收到 409 和现有模型修复链接，而不是笼统报错。
- [ ] 维度表草稿可无来源保存；进入实现前必须绑定已确认来源、上游模型或受控生成策略。
- [ ] 物理表名、装载方式、SCD 和保留期分别校验并在 UI 解释。
- [ ] PUBLISHED 实现按统一资产键进入资产台账，DRAFT/DESIGNING 不进入。
- [ ] 契约测试、迁移测试、真实 PostgreSQL、认证 API、Chrome95 四态和端到端证据全部归档到 `it/`。

## 非目标

- 不把资产台账改造成数据集市管理页面。
- 不新增一级菜单或第二套模型/发布/资产标识。
- 不新增独立 DIM 物理层，不改变 Sprint-67 的模型类型→分层矩阵。
- 不在维度登记阶段强制选择源表或自动把连接中的全部表加入计划。
- 不支持任意自定义数据集市层级 DSL；首期只提供平面集市目录与多业务分类关联。
- 不物理删除 `history_policy`、旧维度记录或未绑定维度定义的存量 ModelSpec。
