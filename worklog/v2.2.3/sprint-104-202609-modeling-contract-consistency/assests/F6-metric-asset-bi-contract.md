# F6 指标、资产与 BI 契约草案及勘察账本

日期：2026-09-10；状态：DRAFT。本文件承接指标页面只读 review，不代表运行复现。仓库路径均相对根目录；行号为登记时位置，后续只补变化。

C43–C49 为首轮登记；C50–C55 为同日第二轮只读核验补录，其中 C48 已按核验结果更正——原登记把 BI 映射 owner 指向 dts-metrics 注册类并判定映射链待核实，实际存在的是 platform serving 投影链（见 C48、C54）。

## Context Ledger C43–C55

| 编号 | 当前事实与边界 | 源码证据 |
|---|---|---|
| C43 | 分类菜单与截图一致；共享 MetricsPage；旧工作台为兼容入口 | source/dts-admin/src/main/resources/config/data/portal-menu-seed.json:249；source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx:122 |
| C44 | 派生实现仅保存模型名，提交按同名最新发布修订选取；原子路径有精确版本 | source/dts-platform-webapp/src/pages/data-modeling/prototype/services/indicatorProjectionService.ts:216；同目录 indicatorDefinitionBindingService.ts 的 bindMetricImplementationModel |
| C45 | MODEL_SPEC_FIELD 优先于公式；派生聚合无条件 MAX，适用粒度未在此处证明 | source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorCalculationService.java:115、:368 |
| C46 | 公式按 dependencyCodes 读取当前定义，未按 sourceRefs 的 INDICATOR_VERSION 执行 | 同上 IndicatorCalculationService.java:186 |
| C47 | 计算只传 ID；有时间取最新组合，无时间聚合整表；编辑器无修饰词/周期消费控件 | 同上 IndicatorCalculationService.java:82、:349；source/dts-platform-webapp/src/pages/data-modeling/prototype/MetricDefinitionBindingFields.tsx:105 |
| C48 | 平台→分析的指标投影链**已存在且在运行**：定时 worker（默认 30s）→ 同步命令服务 → 载荷工厂 → 只读指标适配器 → analytics；BI 的 AnalyticsMetric 与语义执行是该链的消费端。平台 publish 方法本身只更新状态/授权/快照 | source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/serving/CatalogModelSemanticSyncWorker.java:32；同包 CatalogModelSemanticSyncCommandService.java:28；同包 CatalogModelSemanticPayloadFactory.java:93；source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorService.java:534；source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/semantic/SemanticQueryService.java:171 |
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
| K61 固定引用 | `implementationRef:{modelSpecId:UUID,modelRevision:int,fieldName:string}`；`sourceRefs:[{sourceType:"INDICATOR_VERSION",sourceId:UUID,sourceVersion:string}]` | 现有创建/rebind/修订命令承载；服务端响应精确锚点。旧 MODEL_SPEC_FIELD 可还原则复用，否则阻断并要求显式绑定；不得按名称/编码升到最新版 |
| K62 执行方式 | `executionMode:"FORMULA"\|"PRECOMPUTED"`；`expression:string\|null`；`resultGrain:string[]`；`allowedAggregations:string[]`；运行输出 `indicatorId,indicatorVersion,dependencyVersions,sourceMode,queryId,dataAsOf,value:number\|null,nullReason:string\|null` | 公式模式按版本依赖计算；预计算模式按声明粒度读取。比率/不可加性必须声明或拒绝跨粒度；聚合/null 规则进入预检。定义和运行字段存储位置由 T32 固定 |
| K63 限定规则 | `modifierRefs:[{id:UUID,version:string}]`；谓词 `{fieldRef:string,op:EQ\|IN\|BETWEEN,value:typedScalar\|typedScalar[]}`；时间周期独立固定引用 | 使用现有修饰词/周期 owner，绑定允许字段；查询参数化，不接受任意 SQL。时间累计/滚动计算不能伪装为普通过滤，首批支持清单由 T32 冻结 |
| K64 分析请求 | `indicatorRefs:[{id:UUID,version:string}],timeRange:{fieldRef:string,start:string,endExclusive:string,timezone:string},dimensions:string[],filters:predicate[],limit:int`；返回 `columns,rows,resolvedVersions,queryId,dataAsOf,cacheHit,warnings` | 优先扩展现有语义查询契约及平台适配，不新增并行计算服务；单值旧 calculate 保留但明确 LATEST_PERIOD/ALL_DATA。时间区间、维度兼容和排序确定；精确 HTTP 入口由 T32 冻结 |
| K65 发布投影 | `{indicatorId:UUID,indicatorVersion:string,assetType:string,assetKey:string,semanticModelRef:string,analyticsMetricRef:string}`；状态复用既有 `SYNC_PENDING\|SYNCED\|SYNC_FAILED` 加 `syncAttempts/lastSyncError/nextSyncAt`，页面另需表达「未注册」态 | 在 C48 的 serving 投影链上扩展；状态与重试沿用 C53 的持久化模型和 CAS `version`，不新造第二套状态枚举或注册表。T32 必须冻结两处结构缺口：①派生/复合指标的投影覆盖（C50 当前零通路）；②触发源——指标发布当前不入队同步（C52），须定为「指标发布触发」还是「模型修订同步顺带带出」。定义发布和分析注册分别记状态；GET 零副作用，失败可独立恢复 |
| K66 BI 消费 | 卡片查询绑定 `{indicatorId:UUID,indicatorVersion:string}` 加 K64 参数；返回解析版本及资产追溯；看板全局参数映射到查询参数 | 公共指标唯一 owner 在平台，BI 为版本化投影。**前置阻断**：wire 契约 `MetricPayload` 无版本位（C51），「卡片按固定版本引用」要么扩展该跨服务契约（破坏性，须定兼容与灰度顺序），要么在平台侧解析后再投影；二选一由 T32 冻结，不得留到 T37 实施时临时决定。卡片引用固定版本，升级需展示影响及显式操作；读取结果重新校验租户、权限/密级，缓存不能跨权限上下文复用 |

## T32 必须关闭的开工缺口

1. 固定指标快照读取 owner、草稿/归档/撤销版本政策；旧定义与实现引用冲突如何检测，不能用当前状态一刀切否定历史快照。**具名交付物**：现网已发布指标中缺精确 `MODEL_SPEC_FIELD`（会在 K61 下被阻断重绑）的条数与占比，按原子/派生/复合分列，附查询语句与执行时间；该计数决定阻断策略是否可接受，缺此计数不得冻结 K61。
2. 模式、结果粒度、限定条件与运行版本的真实持久化字段、索引、迁移和并发协议；发布/rebind 失败是否留下草稿及恢复路径。冻结每个写命令的事务/CAS/幂等策略。
3. 在 C48 已确认的 serving 投影链上冻结三件事，不再重复"链路是否存在"的核实：①派生/复合指标的投影方案（C50 当前只投原子）；②版本传递方案——扩展 `MetricPayload` 还是平台侧解析（C51），含跨服务兼容与灰度顺序；③触发源与幂等键（C52 指标发布不入队）。另需明确跨服务部分成功的可观察状态、重试顺序与旧卡片兼容。`dts-metrics` 侧不在核实范围内（C54）。
4. K64 的真实请求路径/DTO、超时取消与权限上下文传递；首批维度、时间与谓词支持清单。
5. 当前目标、外部 Chrome 登录、隔离样本及真实数据画像；不得继承 F5 的“仅浏览器新增验收”约束为 F6 默认，也不擅自改动 F5 验收约定。

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
