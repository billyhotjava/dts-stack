# F6 指标、资产与 BI 契约草案及勘察账本

日期：2026-09-10；状态：接口与首批范围已冻结，运行验证待 T39。本文件承接指标页面只读 review，不代表运行复现。仓库路径均相对根目录；行号为登记时位置，后续只补变化。

C43–C49 为首轮登记；C50–C55 为同日第二轮只读核验补录，其中 C48 已按核验结果更正——原登记把 BI 映射 owner 指向 dts-metrics 注册类并判定映射链待核实，实际存在的是 platform serving 投影链（见 C48、C54）。

## Context Ledger C43–C55

| 编号 | 当前事实与边界 | 源码证据 |
|---|---|---|
| C43 | 分类菜单与截图一致；共享 MetricsPage；旧工作台为兼容入口 | source/dts-admin/src/main/resources/config/data/portal-menu-seed.json:249；source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx:122 |
| C44 | 派生实现仅保存模型名，提交按同名最新发布修订选取；原子路径有精确版本 | source/dts-platform-webapp/src/pages/data-modeling/prototype/services/indicatorProjectionService.ts:216；同目录 indicatorDefinitionBindingService.ts 的 bindMetricImplementationModel |
| C45 | MODEL_SPEC_FIELD 优先于公式；派生聚合无条件 MAX，适用粒度未在此处证明 | source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorCalculationService.java:115、:368 |
| C46 | 公式按 dependencyCodes 读取当前定义，未按 sourceRefs 的 INDICATOR_VERSION 执行 | 同上 IndicatorCalculationService.java:186 |
| C47 | 计算只传 ID；有时间取最新组合，无时间聚合整表；编辑器无修饰词/周期消费控件 | 同上 IndicatorCalculationService.java:82、:349；source/dts-platform-webapp/src/pages/data-modeling/prototype/MetricDefinitionBindingFields.tsx:105 |
| C48 | 平台→分析的指标投影链**源码链已确认，当前运行状态待基线验证**：定时 worker（默认 30s）→ 同步命令服务 → 载荷工厂 → 只读指标适配器 → analytics；BI 的 AnalyticsMetric 与语义执行是该链的消费端。平台 publish 方法本身只更新状态/授权/快照 | source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/serving/CatalogModelSemanticSyncWorker.java:32；同包 CatalogModelSemanticSyncCommandService.java:28；同包 CatalogModelSemanticPayloadFactory.java:93；source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorService.java:534；source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/semantic/SemanticQueryService.java:171 |
| C49 | 计算历史失败被置空；分层固定公共层；发布反馈不等于 BI 可用 | source/dts-platform-webapp/src/pages/data-modeling/prototype/MetricsPage.tsx:253、:318、:334 |
| C50 | 该投影**只覆盖原子指标**：适配器 SQL 硬编码 `metric_type='ATOMIC'` 且必须命中 `SEMANTIC_MODEL_REVISION` 的 sourceRef；派生/复合指标当前没有到 BI 的通路 | source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/serving/CatalogModelSemanticIndicatorReadAdapter.java:21 |
| C51 | 指标版本**被读出后丢弃**：适配器 SQL 选出 `indicator.version`，但 wire 契约 `MetricPayload` 的十个字段没有版本位 | 同上适配器 :30；source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/serving/CatalogModelSemanticContract.java:33；CatalogModelSemanticPayloadFactory.java:137 |
| C52 | 投影触发源是 ModelSpec 修订的 serving 同步，**不是指标发布**：`IndicatorService.publish` 不入队任何同步，governance 包对同步命令服务零引用 | source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorService.java:534（全仓检索 governance 包无 CatalogModelSemanticSyncCommandService 引用） |
| C53 | 已有持久化交付状态与受控重试：`modeling_catalog_model_serving_projection` 的 `sync_status` 取 `SYNC_PENDING\|SYNCED\|SYNC_FAILED`，带 `sync_attempts`、`last_sync_error`、`next_sync_at` 与 `version`（CAS） | source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/CatalogModelServingProjectionRepository.java:474、:504、:532、:549、:568；CatalogModelSemanticSyncCommandService.java:222 |
| C54 | `dts-metrics` 的 MetricDownstreamRegistrar 在 platform 侧**零调用点**（引用只在 dts-metrics 自身服务与其测试类），属平行原型链，不是本 Feature 的候选 owner | source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricDownstreamRegistrar.java:32（全仓引用检索结果） |
| C55 | 公式依赖解析按 code **全局取首条**：`findFirstByCodeIgnoreCase(code)` 既忽略版本，也不校验部门/租户归属，同 code 可跨归属串绑 | source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorCalculationService.java:205 |

## 已有契约与 owner

- UI：`/data-modeling/metrics/{atomic|derived|composite|modifiers|periods}?indicatorId={uuid}`；复用 MetricsPage 与现有编辑器。
- 平台：`/api/governance/indicators` 的 CRUD、发布与修订接口；`POST /api/governance/indicators/calculate`；`POST /api/governance/indicators/model-field-drafts` 与已有 rebind。精确 rebind 路径及完整 DTO 在 T32 固定，不猜测。
- 模型字段命令已有 `indicator`、`modelSpecId:UUID`、`modelRevision:int`、`fieldName:string`、`measurementUnitId:UUID|null`、`measurementUnitVersion:int|null`；引用 `MODEL_SPEC_FIELD` 的 target 为 `{id}@{revision}#{field}`。
- 现有计算服务 `calculate(List<UUID>) → CalculationBatch`；历史来自 `gov_indicator_run`，引用来自 `gov_indicator_reference`，定义为 GovIndicatorDefinition。定义/版本表列须 T32 核对。
- 平台→分析投影（C48、C50–C53）：`CatalogModelSemanticSyncWorker` → `CatalogModelSemanticSyncCommandService`（读取/受控重试边界，已接 AuditService）→ `CatalogModelSemanticPayloadFactory` → `CatalogModelSemanticIndicatorReadAdapter`；状态落 `modeling_catalog_model_serving_projection`。这是本 Feature 唯一既有 owner，K65 在其上扩展。
- BI：CardResource/DashboardResource → SemanticQueryService.executeForCard → AnalysisQueryGateway；AnalyticsMetric 为现有消费定义。不得新增绕过权限与审计的直连查询。
- 明确排除：`dts-metrics` 的 MetricDownstreamRegistrar 及其生命周期服务（C54），不作为 owner、不接为新主链、不新增对其调用。

## 目标增量契约（草案，须 T32 冻结后才能编码）

| ID | 输入/输出字段 | 语义与落点 |
|---|---|---|
| K61 固定引用 | `implementationRef:{modelSpecId:UUID,modelRevision:int,fieldName:string}`；`sourceRefs:[{sourceType:"INDICATOR_VERSION",sourceId:UUID,sourceVersion:string}]` | 现有创建/rebind/修订命令承载；服务端响应精确锚点。模型字段型的旧 MODEL_SPEC_FIELD 可还原则复用，否则阻断并要求显式绑定；公式型只要求完整、合法的固定上游引用，不强制绑定结果模型；不得按名称/编码升到最新版 |
| K62 执行方式 | `executionMode:"FORMULA"\|"PRECOMPUTED"`；`expression:string\|null`；`resultGrain:string[]`；`allowedAggregations:string[]`；运行输出 `indicatorId,indicatorVersion,dependencyVersions,sourceMode,queryId,dataAsOf,value:number\|null,nullReason:string\|null` | 公式模式按版本依赖计算；预计算模式按声明粒度读取。比率/不可加性必须声明或拒绝跨粒度；聚合/null 规则进入预检。定义和运行字段存储位置由 T32 固定 |
| K63 限定规则 | `modifierRefs:[{id:UUID,version:string}]`；谓词 `{fieldRef:string,op:EQ\|IN\|BETWEEN,value:typedScalar\|typedScalar[]}`；时间周期独立固定引用 | 使用现有修饰词/周期 owner，绑定允许字段；查询参数化，不接受任意 SQL。时间累计/滚动计算不能伪装为普通过滤，首批支持清单由 T32 冻结 |
| K64 分析请求 | `indicatorRefs:[{id:UUID,version:string}],timeRange:{fieldRef:string,start:string,endExclusive:string,timezone:string},dimensions:string[],filters:predicate[],limit:int`；返回 `columns,rows,resolvedVersions,queryId,dataAsOf,cacheHit,warnings` | 优先扩展现有语义查询契约及平台适配，不新增并行计算服务；单值旧 calculate 保留但明确 LATEST_PERIOD/ALL_DATA。时间区间、维度兼容和排序确定；精确 HTTP 入口由 T32 冻结 |
| K65 发布投影 | `{indicatorId:UUID,indicatorVersion:string,assetType:string,assetKey:string,semanticModelRef:string,analyticsMetricRef:string}`；状态复用既有 `SYNC_PENDING\|SYNCED\|SYNC_FAILED` 加 `syncAttempts/lastSyncError/nextSyncAt`，页面另需表达「未注册」态 | 在 C48 的 serving 投影链上扩展；状态与重试沿用 C53 的持久化模型和 CAS `version`，不新造第二套状态机或注册 owner；现表是模型级状态，须按下文版本投影协议扩展存储，不得直接当作逐指标版本状态。T32 必须冻结两处结构缺口：①派生/复合指标的投影覆盖（C50 当前零通路）；②触发源——指标发布当前不入队同步（C52），必须保证指标独立发布即产生持久化同步意图；模型修订同步可补偿带出，但不能作为唯一触发条件。定义发布和分析注册分别记状态；GET 零副作用，失败可独立恢复 |
| K66 BI 消费 | 卡片查询绑定 `{indicatorId:UUID,indicatorVersion:string}` 加 K64 参数；返回解析版本及资产追溯；看板全局参数映射到查询参数 | 公共指标唯一 owner 在平台，BI 为版本化投影。**前置阻断**：wire 契约 `MetricPayload` 无版本位（C51），「卡片按固定版本引用」要么扩展该跨服务契约（需验证向后兼容，新增可选字段不必然是破坏性变更），要么在平台侧解析后再投影；二选一由 T32 冻结，不得留到 T37 实施时临时决定。卡片引用固定版本，升级需展示影响及显式操作；读取结果重新校验租户、权限/密级，缓存不能跨权限上下文复用 |

## 多维公式对齐规则（K62–K64 增量）

- 固定指标版本中的 `dimensionBindings` 映射公共维度标识到每个依赖的固定模型字段；`timeBinding` 声明业务时间角色、字段、时区及支持粒度。T32 冻结具体 DTO 和存储。名称相同不能自动视为可关联维度，业务时间角色不兼容时拒绝请求。
- 查询时间与公共维度筛选必须一致传递给各依赖；指标自带修饰词仅作用于该指标，外部筛选与其按 AND 合并，不允许覆盖。无法映射的筛选或维度在执行前拒绝，禁止静默忽略。
- 每个依赖先按同一公共维度键和时间桶聚合，再按完整键对齐后执行公式；聚合结果每键最多一行，出现重复键拒绝。禁止直接 JOIN 原始明细造成扇出。
- 首批以依赖分组键并集对齐，不自动补完整日历。缺失依赖组默认返回 null 与 `MISSING_DEPENDENCY_GROUP`；只有固定指标显式声明可将缺组视为零，且该依赖查询成功，才补零。零分母为 null 与 `ZERO_DENOMINATOR`。缺组、SQL NULL、查询失败分别处理；任一依赖查询失败不得展示为成功计算。
- 不同地区编码未映射、时间桶/时区不兼容时拒绝；NULL 维度键的分组及展示规则由 T32 固定。跨依赖读取须约定一致性等级：首批同源在同一查询或同一读快照完成，不能以依次读取的不同时间点声称一致；无法满足则明确拒绝或列为不支持。
- 结果行数上限在分组对齐/公式计算后应用；不得分别截断分子、分母再计算。超限返回明确错误或已声明的稳定分页协议，不允许静默少算。

## 指标版本投影与模型级状态（K65–K66 增量）

现有 `modeling_catalog_model_serving_projection` 按 `(tenant_id,model_spec_id)` 唯一（CatalogModelServingProjectionRepository.java:83），表示模型级同步。复用该 owner 和重试机制，不等于把其单条状态直接作为所有指标版本的状态。

- 逻辑交付身份为 `{tenantId,environment,indicatorId,indicatorVersion}`，须保存逐版本 desired/applied 标识或校验和、消费映射、失败及重试信息。模型级状态与版本级结果分别表达，v2 失败不得把 v1 标成不可用。
- T32 冻结存储方案：优先在既有 owner 内扩展不可变版本映射与交付明细；必要的从属表允许按正式迁移增加，但不能新增平行注册控制面。必须列明唯一键、CAS 粒度、历史保留和清理条件，不能只给 modelSpecId 增列一个“当前指标版本”。
- 公式型没有 implementationRef 时，以固定依赖集合确定既有语义模型/数据集绑定；不能虚构实现模型或随便选第一个上游作为宿主。单源且公共粒度兼容是首批准入条件，无法归属时明确阻断分析准备。投影中需携带可执行公式及固定依赖映射，不能只放版本标签。
- v1、v2 消费映射同时存在；卡片使用精确指标版本解析，同名指标不能覆盖旧映射。平台侧解析方案也必须说明 analytics 如何区分两个版本，不得仅在平台返回版本号。
- 指标发布产生持久化同步意图；冻结事务边界及可靠补偿，防止发布成功后通知丢失。重试绑定精确版本及载荷校验和；乱序响应只能完成对应意图，不得用 v1 的迟到响应覆盖 v2 或整模型状态。
- 验收必须包含 v1 已就绪、v2 失败、旧卡片可用；重试 v2 后两版本并存；响应乱序；模型不变仅指标发布；无实现模型的公式指标可按支持边界进入 BI。

## T32 必须关闭的开工缺口

1. 固定指标快照读取 owner、草稿/归档/撤销版本政策；旧定义与实现引用冲突如何检测，不能用当前状态一刀切否定历史快照。**具名交付物**：现网已发布指标按「模型字段型缺精确引用、公式型依赖完整、公式型依赖缺失、模式无法判定」四组统计数量与占比，再按原子/派生/复合分列，附分母、查询语句与执行时间。公式型依赖完整不得计入强制重绑；模式未知只列待判定，不猜测为损坏。缺该分类统计不得冻结 K61。
2. 模式、结果粒度、限定条件与运行版本的真实持久化字段、索引、迁移和并发协议；发布/rebind 失败是否留下草稿及恢复路径。冻结每个写命令的事务/CAS/幂等策略。
3. 在 C48 已确认的 serving 投影链上冻结三件事，不再重复"链路是否存在"的核实：①派生/复合指标的投影方案（C50 当前只投原子）；②版本传递方案——扩展 `MetricPayload` 还是平台侧解析（C51），含跨服务兼容与灰度顺序；③触发源与幂等键（C52 指标发布不入队）。另需明确跨服务部分成功的可观察状态、重试顺序与旧卡片兼容。`dts-metrics` 侧不在核实范围内（C54）。
4. K64 的真实请求路径/DTO、超时取消与权限上下文传递；首批维度、时间与谓词支持清单。
5. 当前目标、外部 Chrome 登录、隔离样本及真实数据画像；补一次 C48 实际配置开关、worker 领取记录、投影状态变化、关联请求号和 analytics 接收/可查询结果，失败或未启用按基线缺口记录，不重新全仓扫描；不得继承 F5 的“仅浏览器新增验收”约束为 F6 默认，也不擅自改动 F5 验收约定。

## 非功能预算与可执行检查（建议值，T32 基线后冻结）

| 约束 | 建议预算 | 检查/责任 |
|---|---|---|
| 依赖展开 | 深度≤32、唯一版本节点≤100；超限明确拒绝 | T35 构造边界图，检查去重和错误；禁止无限递归 |
| 单次查询 | ≤10 个指标、≤5 个分组维度、≤1000 结果行，查询超时30秒并取消 | T36 超限/超时场景；限制必须在服务端执行 |
| 页面列表 | 分页沿用现有约定；不得逐行展开依赖/查询结果 | T38 请求计数，切页/快速切换检查过期响应 |
| 响应基线 | 记录真实行数、5并发冷/热查询 P95，目标≤5秒；超标需限定或优化后冻结 | T32/T39 保留请求与数据库耗时，不用缓存命中冒充冷查询 |
| 一致性 | 重复注册不新增映射；并发发布不混版本 | T37 幂等/故障注入与并发检查 |
| 安全 | 服务端权限、密级、租户；缓存含版本/参数/权限上下文，撤权后不可读 | T37/T39 不同角色及撤权验收 |
| 审计 | 指标发布、投影注册/重试、口径升级各记审计事件，沿用 serving 链已接的 AuditService 与 AuditStage，不新建审计通道 | T37 核对事件落库，T39 留证；缺审计不算注册闭环 |
| 归属隔离 | 依赖解析按 ID+版本，不按 code 全局取首条（C55） | T35 构造同 code 跨部门样本，验证不串绑 |

## 发布、迁移与运维

保留既有记录与 API，采用扩展兼容迁移；不自动回填最新版本。新旧服务滚动/回退的允许组合必须 T39 记录，不可回退的数据变化给出前向恢复办法。运行日志包含请求号、指标版本、数据源引用、查询耗时/超时、注册阶段与可重试原因，不记录凭据或敏感明细。

开发目录只编辑/静态检查/review/commit/push；所有编译测试、正式构建/交付、Compose 部署在 deploy 经 ff-only 后完成。禁止容器补丁。当前工作只写规划文档。


## 2026-09-10 编码冻结补录

### 接口与持久化

- 创建、更新、修订、回滚沿用原接口。新增 `implementationRef`、`executionMode`、`analysisConfig`；后者明确包含 `dimensionBindings`、`timeBinding`、`resultGrain`、`allowedAggregations`、`modifierRefs`、`predicates`、`periodRef`、`periodMode`、`missingGroupsAsZero`。字段随快照、回滚及验证签名一起保存。
- 查询入口为 `POST /api/governance/indicators/query`，请求 `indicatorRefs,timeRange,dimensions,filters,limit,scope`。scope 为 RANGE（默认）、LATEST_PERIOD、ALL_DATA；有业务时间映射时 RANGE 必须提供区间。旧 calculate 保留，显式执行最新周期，无时间维度则全量；存量单时间键原子版本可以从固定模型还原，不重写历史快照，多时间键要求修订。
- 返回 `columns,rows,resolvedVersions,queryId,dataAsOf,cacheHit,warnings`。dataAsOf 是本次请求开始时刻，表示当前数据查询，**不是历史数据版本或物理数据快照保留承诺**。指标版本固定口径，不固定数据内容。
- `analysisConfig` 落当前定义 JSONB；版本表仍用 snapshot_json。运行记录新增 indicator_version、dependency_versions、query_id、source_mode、null_reason、data_as_of。原子/公式共享同一查询规划器，先按公共键聚合再对齐；所有叶子在一条 SQL 中执行。
- 新增 serving owner 下 `modeling_catalog_indicator_serving_projection`，主键 `(tenant_id,indicator_id,indicator_version)`。每个部署数据库代表一个环境，不接受客户端传入环境；当前平台唯一治理租户为 default。版本快照作为 desired 定义；mapping 作为 applied 映射，CAS version 在领取、完成、重试时递增。已成功 v1 不会因 v2 失败而重写状态。
- 发布/发布回滚版本在原事务内写入同步意图。原 `CatalogModelSemanticSyncWorker -> CatalogModelSemanticSyncService` 领取模型及指标版本意图；`CommandService` 是原模型状态读取/重试入口，并非 worker 执行 owner。本次指标状态接口沿 IndicatorService 扩展，同步仍由原 serving worker 负责。
- `GET /api/governance/indicators/{id}/versions/{version}/analysis-status` 零副作用；`POST .../analysis-retry` 要求 `{expectedVersion:long}`，仅失败状态可重试；并发不匹配返回冲突。
- MetricPayload 增加可空 indicatorId、indicatorVersion、assetType、assetKey、analysisConfig。BI 名称为 `indicator_{uuid去连字符}_{version}`，旧 MetricPayload 构造器保留。公式依固定上游选择同源宿主，不虚构结果模型。
- BI 查询每次通过受信任服务入口 `POST /api/internal/indicators/plan` 取得计划；该入口只允许 service:dts-analytics，并逐项检查用户对当前指标、历史密级及源模型资产的 READ 权限。analytics 仍通过 AnalysisQueryGateway 执行，禁用此类查询的结果缓存。
- 卡片保存显式 indicatorRefs，同时保留版本化 measures 供既有编辑器回显；两者必须一致。升级确认提示对所有复用该卡片的看板生效。卡片列表支持 indicatorId/indicatorVersion 过滤，仍按原权限过滤，指标页面展示可见使用记录。

### 首批能力与预算

- 仅支持可用 PostgreSQL 数据源、同源单语句快照；跨源拒绝。固定模型必须有匹配修订和校验和的当前服务数据；不借用最新模型补齐缺失历史版本。
- 公共维度必须显式映射；NULL 键按 IS NOT DISTINCT FROM 合并。时间字段采用模型原生粒度，日/月/年通过已建模字段分组；不执行任意时间桶转换。业务角色、时区、粒度不兼容拒绝。
- 修饰词沿原指标定义 owner（category=MODIFIER），周期 category=TIME_PERIOD，均固定已发布版本。首批周期支持 RANGE，界面提供本月/本年明确区间；累计、滚动、同比环比不伪装为筛选，旧窗口/SQL筛选须显式修订。
- EQ、IN、BETWEEN 仅接收标量或标量数组；所有值走 JDBC bind。参数最多2048个，单条件100个值，外部筛选32项，合并限定128项。
- 每请求16个指标、8个分组维度、100个唯一版本、32层依赖、256个展开节点；最终结果1–10000行，计划200000字符上限。叶子不截断，最终 limit+1 检测超限并拒绝。
- 预计算结果按完整声明粒度查询，重复结果拒绝；首批不允许跨粒度重新聚合比率。公式缺组默认 null、显式缺组补零、SQL NULL 和零分母分别表达；查询失败不伪装为零。
- 网关复用 JdbcSqlExecutor 的连接、查询超时和凭据解析；新增 executeBound 严格数据源定位，不回退默认库。不支持参数化的旧 adapter 明确拒绝。
- 表/接口语法、时区转换、权限矩阵、CAS 乱序、两版本卡片结果仍须正式测试；上述为编码契约，不是通过证据。

### 当前环境只读盘点

2026-09-10 10:35:06+08，当前 `dts-stack-dts-pg-1` 中 gov_indicator_definition=0、gov_indicator_version=0。因此四组现存指标均为0，无法用当前业务数据构造双版本证据。早先 deploy-* 的1条指标记录属于另一环境，不能移作本环境验收。部署目录当前缺失，统一测试时按正式 Git 路径准备独立构建目录。

补充：看板映射到业务时间字段时支持 YYYY-MM-DD 单日或双日期区间，按指标时区转换为半开区间，与卡片既有范围取交集；无交集明确报错。维度条件继续 AND 合并。
