# 建模主线与关键对象设计

## 1. 设计结论

表是建模的中心产物，维度是唯一需要独立登记的概念级对象。业务分类负责回答“管什么业务”，分层回答“数据放哪里”，标准回答“字段长什么样”，四类表回答“如何组织数据”，指标回答“数字怎么算”。

业务对象同时描述实体、粒度、来源和模型关系，与 ModelSpec 重复，因此从产品模型中退役。退役不是把页面标题换成“维度目录”，而是把原记录按性质分别送到维度或模型设计。

## 2. Canonical 对象

| 对外对象 | 客户问题 | 唯一事实源 | 是否是建模前置 |
|---|---|---|---|
| 业务分类 | 管什么业务 | `catalog_domain` + WarehousePlan domain binding | 进入模型设计前至少确认一个 |
| 数仓分层 | 数据放在哪一层 | WarehousePlan policy | 进入模型设计前确认 |
| 数据标准 | 字段长什么样 | 标准模块 | 概念维度可后补；DWD 生成/发布前校验 |
| 维度 | 从什么角度分析 | `ModelSpec(modelType=DIMENSION)` | 仅创建维度表时必需 |
| 四类表 | 形成什么数据表 | `ModelSpec` | 建模核心产物 |
| 指标 | 数字怎么算 | 指标模块 | 模型发布后引用；非模型创建前置 |

## 3. 四类表输入规则

| 表类型 | 客户语言 | 创建时必填 | 创建时可选 | 实现/发布前补齐 |
|---|---|---|---|---|
| DIMENSION | 维度表 | 新 UI：planId、domainId、名称、description（维度定义）、维度键说明、目标层、实现方式；Phase-B 再增加 dimensionCode | 属性、层级、来源、generationStrategy | 实现前补 KEY 字段闭合、scdPolicy、来源或 generationStrategy 至少一个；发布前补标准/质量/权限 |
| FACT | 明细表 | planId、domainId、名称、粒度声明、粒度键、至少一个来源、目标层 | 业务活动、维度引用 | 时间语义、标准绑定、质量规则 |
| SUMMARY | 汇总表 | planId、domainId、名称、上游模型、聚合粒度、目标层 | 周期、维度引用、指标引用 | 聚合表达式、刷新策略、质量规则 |
| APPLICATION | 应用表 | planId、domainId、名称、消费场景、上游模型、目标层 | 服务/报表引用、刷新周期 | 输出字段契约、权限、发布目标 |

`processId` 不再出现在通用创建门禁中。兼容期允许 FACT 的 `businessActivityRef` 映射旧 `processId`，但为空时不得阻止保存或进入下一步。

### 3.1 DIMENSION Phase-B 目标契约（尚未实现）

DIMENSION 在同一 ModelSpec 内增加类型专属 `dimensionProfile`，不新增维度主表：

| 字段 | 含义 | 阶段规则 |
|---|---|---|
| dimensionCode | 维度目录中的租户内稳定大写 ASCII 标识 | 创建必填；匹配 `^[A-Z][A-Z0-9_]{0,63}$`，大小写不敏感唯一且创建后不可变 |
| hierarchies | 由 code/name/有序 field levels 组成的层级 | 可选；引用字段必须存在，层级不得重复或成环 |
| scdPolicy | NONE/TYPE1/TYPE2 及 TYPE2 字段引用 | DRAFT 可空，IMPLEMENTATION_READY 必填 |
| reuseScope | PLAN/DOMAIN/TENANT | 默认 PLAN；不扩大 domain/租户权限 |

definition 复用 canonical `description`，新 DIMENSION UI 在创建和编辑草稿时均要求填写；attributes 复用 `fields(role=ATTRIBUTE)`；dimensionKey 复用 `grain.keys + fields(role=KEY)`。`dimensionCode` 只定位 ModelSpec，业务自然键用于标识数据行，代理键由 SQL/dbt 实现生成，`standard_code` 由标准模块持有；四者不得合并成一个“code”。历史 `contractVersion=2` 的空 description 或未闭合 key 仍保持可读和可重放，Phase-B 规则只进入独立实现门禁或经兼容迁移后的新契约，不能原地收紧 v2。

来源规则使用 inclusive OR：DRAFT 允许 `sourceRefs=[]` 且 `generationStrategy=null`；`IMPLEMENTATION_READY` 要求两者至少一个有效，并允许同时存在。组合时 `sourceRefs` 是可追溯输入，`generationStrategy` 描述生成方式，不设优先覆盖。

所有维度复用保存 `{modelSpecId, revision}`。跨计划引用必须命中已发布且当前用户有权访问的 revision；`reuseScope` 只缩小允许范围，不绕过权限和发布状态。

## 4. 分阶段门禁

### 4.1 进入计划

- 无前置条件；用户可从业务目标或现有资产开始；
- 输出稳定 `planId`；
- 创建后唯一主动作是“完善规划基线”。

### 4.2 进入模型设计

- 至少一个业务分类已确认；
- 数仓分层策略已确认；
- 允许先登记概念维度；
- 不要求业务对象，不要求业务活动，不要求全部来源完成业务映射。

### 4.3 进入实现

- ModelSpec 的角色必填字段完整；
- FACT 必须有粒度、时间语义和来源；
- DIMENSION 必须有 dimensionCode、KEY 字段闭合、scdPolicy，并满足有效来源或 generationStrategy 至少一个；两者可组合；
- SUMMARY/APPLICATION 必须有已发布或当前计划内可用的上游模型。

### 4.4 进入发布

- 标准、质量、权限、实现所有权和依赖检查通过；
- 构建/测试产物与当前 ModelSpec revision 一致；
- 不检查业务对象存在性。

## 5. 页面形态

### 5.1 业务分类

复用 `/governance/subjects` 的数据域树和治理统计，但页面对外名称改为“业务分类”。业务过程、候选确认、总线矩阵不再堆在分类首页；业务活动只在明细表设计中作为可选来源说明。

### 5.2 维度目录

目标路由 `/modeling/dimensions`，数据源是 DIMENSION ModelSpec，而不是 SemanticBusinessObject。支持跨计划浏览；创建时必须选择计划和业务分类，若从计划进入则自动带入并锁定上下文。当前目录与入口已落地；`dimensionProfile`、SCD/层级编辑和实现门禁仍是 Phase-B 目标，尚未实现。

Phase-B expand 前必须先用失败测试固定历史 v2 snapshot、checksum/ETag 和 idempotency replay 行为。新增 nullable 字段不得改变旧 revision 的 canonical hash；历史记录缺少 `dimensionProfile` 时只显示“待完善”，不得静默补默认值并假完成。

### 5.3 模型中心

目标路由 `/modeling/models`，按维度表、明细表、汇总表、应用表四类视图展示同一 ModelSpec 台账。新建按钮直接选择表类型，不先打开业务对象抽屉。

### 5.4 高级实现

SQL 与 dbt 是 ModelSpec 的实现方式。页面必须带 `planId/modelSpecId/revision` 进入，生成产物和运行结果回写同一模型，不形成新的模型台账。

## 6. 业务对象处置

| 旧记录特征 | 迁移目标 | 自动迁移条件 |
|---|---|---|
| objectKind=DIMENSION/REFERENCE/MASTER，且有稳定业务键 | DIMENSION ModelSpec | 名称、键和来源不冲突 |
| objectKind=FACT/EVENT/SNAPSHOT，或携带事实粒度 | FACT ModelSpec 的粒度、来源和说明 | 能唯一关联现有/新建 FACT ModelSpec |
| 同时带维度属性和事实指标 | 人工拆分 | 禁止系统猜测后自动确认 |
| 无来源、无键、名称像指标 | 人工清单或归档 | 不创建空模型 |

迁移后建立 `legacyRef`，保证旧深链、审计和产物仍能定位新模型。新写路径停止创建业务对象；读取适配器只用于迁移期。

## 7. 页面设计原则

1. 每页最多一个主动作；次级操作放入行操作、更多菜单或详情 Tab。
2. 首次说明折叠，恢复访问直接定位最近计划和当前阻塞。
3. 空状态必须回答“缺什么、去哪里补、补完回哪里”。
4. 阻塞信息使用稳定错误码，不根据访问页面或 session 推断完成度。
5. 专业菜单可直接浏览；创建动作若缺 planId，只要求选择计划，不重新播放全套向导。
6. 同一页面不得同时显示四阶段、六 Tab、八阶段和九站等多套旅程导航。

## 8. 成功场景

### 8.1 从业务目标开始

创建计划 → 选择业务分类和分层 → 登记组织/时间维度 → 创建明细表并声明一行含义 → 关联标准 → 生成实现 → 发布 → 创建指标。

### 8.2 从现有资产开始

选择存量表/dbt 节点 → 创建计划 → 确认业务分类和分层 → 直接生成四类表候选 → 人工确认粒度/键/用途 → 发布和指标引用。

两个场景共用同一 WarehousePlan、ModelSpec、发布门禁和 StageProjection。
