# T13 分析准备：平台源自动接入实施契约

**状态：实施中。** 不把 `6191dbca4` 的内置数仓启动重试当作分析准备完成。当前实现已补结构化 `(tenant_id, platform_data_source_id)` 唯一绑定、受信任平台源注册及指定物理目标/字段核验；尚未把完整 serving 版本化准备证据和所有语义发布入口完全收敛到该结果。

## 当前已实现接口

`PlatformAnalyticsDatabaseRegistrationService` 是 analytics 内部服务，不暴露用户 database API：

```text
ensureDatabase(tenantId: String, platformDataSourceId: UUID) -> AnalyticsDatabase
ensureTarget(tenantId, platformDataSourceId, schemaName, tableName, requiredFields)
  -> TargetRegistration { database, table, verifiedFields }
```

`tenantId` 必须是可信调用链传入的非空字符串（可为 `default`），`platformDataSourceId` 是唯一身份。创建前通过 `PlatformInfraClient` 读取 detail，并经 `JdbcDetailsResolver` 取得瞬时连接详情；名称仅是展示字段。`ensureTarget` 调 `MetadataSyncService.syncTarget`，只核验指定 schema/table 和 `requiredFields`，目标/字段缺失或临时 metadata 故障以 `AnalysisRegistrationException.code` 返回。`0054-01` 仅做 nullable expand 和唯一约束；无 tenant 的旧行不自动按名称归并。若旧行已经带同一平台源 ID 但没有可信 tenant，自动注册返回 `ANALYSIS_LEGACY_SOURCE_UNRESOLVED`，不会另建重复源。

## 已证实的现状与必须收敛的差距

| 事实 | 现有位置 | 对 T13 的约束 |
|---|---|---|
| 分析库关联把 `platformDataSourceId` 放在 `analytics_database.details_json`，查询依赖 `findAll()` 后解析 JSON。 | `DatabaseResource`、`AnalyticsDatabaseBindingResolver` | 不能继续用名称或 JSON 扫描作为自动接入的身份和并发保证。 |
| `AnalyticsDatabase` 及建表 changeSet 没有 tenant 字段，也没有平台源 ID 的结构化唯一约束。 | `domain/AnalyticsDatabase`、`0003_databases.xml` | 当前数据模型不能证明跨租户隔离或“一租户一平台源”的唯一性，完整 T13 必须先走前向迁移。 |
| `MetadataSyncService.syncDatabaseSchema` 通过 JDBC metadata 枚举整个库。 | `MetadataSyncService` | 发布一个模型不得隐式注册或停用全库表/字段；需要指定表、指定字段的同步与验证入口。 |
| 语义发布当前可先创建 `AnalyticsTable`，字段未同步时返回 deferred。 | `SemanticPublishResource` | 占位表或 deferred 字段不是“分析已就绪”；T13 必须在写语义前验证真实目标和全部被引用字段。 |
| `JdbcDetailsResolver` 和 `PlatformInfraClient` 已按平台源 ID 取运行时 JDBC 凭据；`DatabaseResource` 的现有 create/update 负责用户权限。 | `JdbcDetailsResolver`、`PlatformInfraClient`、`DatabaseResource` | 抽取共享应用服务，不把服务调用伪装成用户 `/api/database` 写操作，也不增加该 API 或服务令牌权限。 |

运行现场的 `analytics_database` 为 0 行仅说明 analytics 启动时 platform 不可用；不能据此推断名称缺失、数据源 ID 缺失或历史数据已修复。

## 冻结的共享服务边界

新增 analytics 内部应用服务 `PlatformAnalyticsDatabaseRegistrationService`。`DatabaseResource` 的用户创建/更新入口和 `SemanticPublishResource` 都调用它；资源层保留各自认证，服务本身不接收 HTTP request、用户 token、JDBC URL、密码或数据源名称。

### 输入

```text
EnsureFromPlatformCommand
  tenantId: String               // 平台现有租户语义（含 default）；仅由受信任调用链提供，非浏览器 body
  platformDataSourceId: UUID     // 稳定身份，必填；名称只用于展示
  target: { schemaName, tableName }
  requiredFields: Set<String>    // 发布模型实际引用的物理字段，非空
  modelSpecId: UUID
  modelRevision: long
  operationKey: String           // serving-sync 的版本化幂等身份
```

`tenantId` 是目标模型/发布记录的权威租户，不从 `details_json`、名称或前端推断。`schemaName`、`tableName` 和字段集由发布结果的已解析物理目标产生；空值、空白值、重复字段及不属于该目标的字段均为输入错误。不得把同一数据源下的全部 schema/table 自动加入分析目录。

### 输出

```text
AnalysisRegistrationResult
  analyticsDatabaseId: long
  analyticsTableId: long
  platformDataSourceId: UUID
  target: { schemaName, tableName }
  verifiedFields: Set<String>
  reusedDatabase: boolean
  reusedTable: boolean
  state: READY | NOT_APPLICABLE
```

只有事务已提交、目标 JDBC metadata 可见、目标表存在且 `requiredFields` 全部对应活动 `AnalyticsField` 时才能返回 `READY`。连接已创建、全库同步已发起、占位 `AnalyticsTable`、或字段 annotation deferred 均不得返回成功。

## 实施序列与复用点

1. `CatalogModelSemanticSyncService` 的 serving 任务在发布成功后构造上述命令；继续沿用 T09 的版本/任务状态与 `POST .../{id}/serving-sync/retry` 的 If-Match 边界。分析重试不得触发物化、质量或发布命令。
2. 注册服务先以 `(tenantId, platformDataSourceId)` 查询结构化关联。没有关联时，使用 `PlatformInfraClient.fetchDataSourceDetail(platformDataSourceId)` 校验该受信任源可用；名称不得参与匹配。`JdbcDetailsResolver` 负责将已验证的平台 detail 转成瞬时 JDBC 详情，凭据不写入 `AnalyticsDatabase` 或日志。
3. 注册服务写入/重用 analysis database 后，调用 `MetadataSyncService` 的**新增目标范围方法**，只读取该 schema/table 的 JDBC metadata，并只 upsert 此表及其字段。现有 `syncDatabaseSchema(long)` 保留给用户全库同步，不能被语义发布调用。
4. 目标范围方法返回已发现的真实字段；服务以大小写规则与数据库 driver 约定核对 `requiredFields`，缺一个即失败，不执行 `SemanticPublishResource.resolveOrCreateTable` 的占位创建路径。
5. `SemanticPublishResource` 删除 `dataSourceName` 的模糊/默认库回退：T13 发布要求 `platformDataSourceId` 和准备结果的 database/table 身份。准备成功后才 upsert semantic model、指标、维度和关联。
6. 完成后 serving 状态记录 `READY` 并保存 model revision、operation key、analytics database/table ID 和验证字段摘要；重放同一版本只读取既有成功步骤。新版本启动新任务，旧版本迟到完成不得覆盖当前状态。

## 并发、存量和迁移决策

完整 T13 **必须新增前向 Liquibase changeSet**，不能修改 `0003-01`。目标结构至少包括：

- `analytics_database.tenant_id` 与 `analytics_database.platform_data_source_id`；
- 唯一约束 `(tenant_id, platform_data_source_id)`；
- repository 的精确查询 `findByTenantIdAndPlatformDataSourceId`，替换自动链路的 `findAll()` + JSON 解析；
- 如 serving 状态尚不能表达版本化准备过程，新增独立的版本化 preparation/evidence 记录，不把状态塞回 `details_json`。

迁移先做只读预检：解析所有存量 `details_json`，按平台源 ID 列出无 ID、非法 ID 和重复记录。现有表没有 tenant，不能自动补写 tenant，也不能按数据库名称合并。每个存量记录须由迁移输入明确归属租户；无法归属者标为 legacy-unresolved 并排除自动注册。重复记录仅在同租户、同平台源且人工确认 child 表/字段/语义归并方案后合并；否则迁移失败并停止上线。迁移完成前，完整 T13 不可标 READY。

并发由数据库唯一约束作为最终裁决：同一 `(tenantId, platformDataSourceId)` 的两个请求中，获胜者创建并提交，冲突者捕获唯一约束冲突后精确重读并继续目标核验。不得以 JVM 锁、名称判断或“先 findAll 再 save”代替约束。相同 `operationKey` 必须重放已完成步骤；不同模型版本可共享已验证 database，但不得共享或覆盖彼此的 serving 成功记录。

## 错误和重试协议

| 错误码 | 含义 | 是否可由 serving-sync 重试 |
|---|---|---|
| `ANALYSIS_PREPARATION_NOT_APPLICABLE` | 分析功能关闭，按 T09 显示 NOT_APPLICABLE | 否 |
| `ANALYSIS_PLATFORM_UNAVAILABLE` | platform I/O、超时或明确 5xx | 是，受现有有界任务重试策略约束 |
| `ANALYSIS_PLATFORM_SOURCE_INVALID` | 源 ID 不存在、不可用、无 JDBC detail 或不受信任 | 否，修正源配置后新任务 |
| `ANALYSIS_LEGACY_SOURCE_UNRESOLVED` | 存量无租户记录可证明同源，但不能证明归属 | 否，需人工归属后重试 |
| `ANALYSIS_REGISTRATION_CONFLICT` | 存量歧义、租户/源唯一性或并发重读后不一致 | 否，需修复数据或重新读取 |
| `ANALYSIS_TARGET_NOT_FOUND` | 指定 schema/table 未实际发现 | 否，修复物化/目标后重试 |
| `ANALYSIS_REQUIRED_FIELD_MISSING` | 目标存在但缺少发布实际引用字段 | 否，修复目标或模型后重试 |
| `ANALYSIS_METADATA_TRANSIENT_FAILURE` | JDBC 建连或 metadata 查询的可确认临时失败 | 是，受限重试 |
| `ANALYSIS_SEMANTIC_PERSIST_FAILURE` | 准备完成后语义持久化失败 | 由当前版本化任务重放；不得重建源或全库同步 |

analytics 到 platform 的 HTTP 400 必须先解析为平台的结构化业务错误；泛化的 400 不可直接归类为永久，也不可无限重试。日志记录 operation/model revision、平台源 ID、目标和错误码，禁止记录 JDBC password、secret 或完整详情 JSON。

`6191dbca4` 的启动重试保持仅用于内置源：应用就绪后每 30 秒最多 6 次，成功注册即停止。它不得被复用于模型发布的重试计数，也不代表目标 metadata/字段已验证。

## 最小修改面与验证入口

| 修改面 | 最小职责 |
|---|---|
| `DatabaseResource` | 调用抽出的共享注册服务；保留用户 DataAdmin 认证，不对服务身份开放通用 database API。 |
| `PlatformAnalyticsDatabaseRegistrationService`（新增） | 源 ID 关联、幂等/冲突处理、目标范围同步和 READY 判定。 |
| `JdbcDetailsResolver` | 提供仅由平台 ID/detail 构建瞬时 JDBC 详情的复用入口，不返回或持久化 secret。 |
| `PlatformInfraClient` | 保持 runtime-detail 调用；补充可分类的异常映射，不扩大 token 权限。 |
| `MetadataSyncService` | 新增 `syncAndRequireTarget` 形式的精确表/字段读取；不改变现有全库手动同步含义。 |
| `AnalyticsDatabase` / repository / Liquibase | 前向增加租户和平台源结构化列、唯一约束、精确查询及存量预检/处置。 |
| `SemanticPublishResource` | 仅消费成功准备结果；删除按 dataSourceName/default database 寻址和字段 deferred 成功路径。 |
| platform serving 服务与状态 DTO | 传入权威租户/目标/字段/版本，保存步骤证据，并使用既有 retry endpoint。 |

最少测试入口：

1. analytics `PlatformAnalyticsDatabaseRegistrationService` 单元测试：同源重放、唯一冲突重读、错误分类、无名称回退、secret 不落库；
2. analytics repository/Liquibase 集成测试：新库约束、升级库的无归属/重复预检和拒绝策略；
3. `MetadataSyncService` 测试：只读取指定目标，目标/字段缺失不创建占位记录，不改变其他表活动状态；
4. `SemanticPublishResourceTest` 与 `SemanticPublishAuthorizationContractTest`：无 `platformDataSourceId`、未 READY、字段缺失、跨租户均拒绝；
5. platform `AnalyticsSemanticPublishClientTest`、`CatalogModelSemanticSyncCommandServiceTest`、`CatalogModelSemanticSyncServiceTest` 和 `CatalogModelSemanticSyncResourceTest`：版本化重放、If-Match 冲突、临时依赖恢复、旧版本迟到完成、分析重试不增加构建/发布次数；
6. 部署目录的正式集成走查：同一租户发布→精确目标/字段存在→语义可查询；再验证依赖中断后的失败/重试。该步骤才可声明运行时分析可用。

## 开工与停止条件

可以开始编码的前提是：迁移与存量处置被评审确认；platform 到 analytics 的受信任 tenant 传递已明确；发布结果能提供精确 schema/table/字段集；serving 状态可记录版本化证据；上述错误码已并入 T09 fixture。任一前提缺失时，T13 仅保持 DRAFT，不以启动重试、连接存在或 HTTP 成功替代验收。
