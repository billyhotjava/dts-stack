# Sprint-104 实施复核（2026-09-07）

基线：开发 HEAD `1ad474167`，开始复核时工作区干净。本记录为当前源码/浏览器观察，不复用旧镜像验收结论。

| Feature | 当前规则与实现 | 缺口及实施责任 | 完成判据 |
|---|---|---|---|
| F1 / T01–T08 | T02–T07 已有实现及定向测试记录 | T08 全场景运行、Chrome95、离线证据尚未闭合；历史测试数不计作本轮通过 | 当前交付版本复验对应 IT |
| F2 / T09–T14 | 四步流程、质量入口、目录 CAS、分析独立恢复已落地 | T14 未完成全量验收；四步与模型页治理要求由 F3 替代 | 保留服务/权限/版本保护，按新边界复验 |
| F3 / T15–T20 | 仅规划，SOURCE 不在 Java/TS/Schema 枚举；创建贴源表按钮禁用 | T15 契约冻结；T16 类型/引用；T17 结构物化；T18 三步；T19 绑定/资产交接；T20 实测 | IT-19–IT-24 真实通过后才能 DONE |

## 当前直接证据及影响

- `ModelWorkbenchCreateMenu.tsx` 的贴源入口为 `kind:null, disabled:true`。不能只启用按钮：后端 `ModelSpecContract.ModelType` 没有 SOURCE。
- `ModelSpecContract.allowsUpstreamModel` 与 `ModelImplementationInputPolicy.allowsImplementationUpstream` 当前均只允许 FACT 引用 FACT；需同时扩展 DWD→SOURCE/ODS，保留同计划可读版本与跨计划发布约束。
- `ModelSpecApplicationService.validateReferenceSet` 已校验准确修订、读取权限和跨计划发布；依赖环由 `validateNoDependencyCycle` 管理。复用这些 owner，不引入另一套依赖台账。
- `ModelBuildIntentResource.decode` 只接受 planId/environment。`ModelBuildIntentService.start` 创建既有单模型候选后调用 `ModelMaterializationStartService.startWithBuild`，后者检查实际来源并创建不可变运行快照。SCHEMA_ONLY 必须进入快照和幂等边界，不能仅隐藏来源错误或复用 DATA_BUILD 成功标签。
- `TargetTableProvisioner` 文件落地分支会 DROP/重建已存在表；绑定模型目标必须在进入该分支前拒绝结构漂移，禁止套用原文件重建语义。
- `ModelDeliveryStatusQueryService.get` 当前聚合质量及 serving。建模结果必须由当前运行证据独立派生，治理读取失败不能抹去物化结果。

## 本轮基线

- Chrome 现有测试账号已通过正常登录进入受保护工作台；凭据不记录。
- `/data-modeling/dimensions/workbench` 已实际加载 6 条模型；点击“新建模型”确认“创建贴源表（尚未接入）”禁用。已有已发布明细显示物化成功、资产登记成功、分析准备失败，三者未混为一个状态。
- 平台、分析、接入、数据库等容器当前运行；此观察不证明其源码 SHA 与开发 HEAD 相同。
- 部署目录跟踪文件无修改，存在 dist、验收数据及 dbt macro 等未跟踪文件；不复制这些文件补齐新交付，正式构建前逐项核对来源。
- GitNexus 索引落后 28 提交，已启动正式 analyze；完成后执行准确符号影响分析再编辑。

## 修复纪律

错误按正常代码修复、Git 提交、部署目录测试/构建、正式配置部署、Chrome 复验处理。禁止手改数据库状态、清理受保护运行、容器热补丁或用假证据关闭 Task。正常业务测试操作与必要的正式前向迁移分别记录，不以数据库数据修补绕过失败。

## K31/K32 第一实施切片（FROZEN，非 T15 全部完成）

- 使用现有 `modeling_model_spec.model_type varchar(32)` 和修订 JSON，不新增表；`SOURCE` 的唯一目标层为 `ODS`，STG 仍不可作为模型目标。SOURCE 不接受事实形态、业务过程、维度历史、指标引用、生成策略或模型上游。
- 创建请求沿用 `{planId:UUID,domainId:UUID,modelType:"SOURCE",name:string,idempotencyKey:string}` 的轻量草稿入口。完整定义沿用已有字段/类型、空 `sourceRefs` 和空 `dependsOn`；暂存不要求物理来源、接入任务或实现。其他身份、字段格式、ETag 和修订语义不变。
- DWD FACT 的 `dependsOn:[{modelSpecId:UUID,revision:positive integer}]` 允许准确 SOURCE/ODS 修订以及原 FACT/DWD；DWS/ADS 保持已有分层规则，通过 DWD→DWS→ADS 设计全链。不得将 SOURCE 当作已确认物理来源。
- 同计划允许可读未发布上游，跨计划仍要求 PUBLISHED；既有应用服务负责引用存在性、读取权限和依赖环。不存在/不可读引用维持原错误，非法层级用 `MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED`；SOURCE 非 ODS 用 `MODEL_SPEC_TYPE_LAYER_MISMATCH`，专属字段非法用现有结构化类型错误。
- 前后端类型矩阵与 JSON Schema 必须一致。验收先覆盖 SOURCE 创建/更新、SOURCE/STG 拒绝、ODS→DWD 允许、上游修订恢复和旧四类兼容。结构物化、完成聚合和接入绑定仍属 T17–T19，不能用此切片宣布就绪。

## T16 已执行验证

- `8cf1db576`：部署目录 Node 契约测试 44/44；TypeScript 检查暴露概览 SOURCE 标签缺失，随后在 `ac5820821` 修复，待重验。
- `ac5820821`：部署目录 Java 定向回归 146/146，源码与测试编译成功；首次编译暴露旧 dbt 测试错误引用 ExpectedImplementationVersion，已修正 owner 为 ModelLifecycleService。
- `ac5820821`：部署目录 ODS 定义保存与逻辑引用组件 Vitest 11/11，覆盖未物化引用、显式更新修订和跨计划未发布拒绝。
- 尚无包含本轮源码的正式镜像/部署或真实 ODS 创建验收，不标 T16 DONE。

## K33 执行方案冻结（T17 待实现）

结构执行复用已持久化的 GENERATED 输入协议：`inputs:[{generatorType:"SCHEMA_ONLY",config:{}}]`、`fieldMappings:[]`、ownership=DESIGNER_GENERATED、materialization=table、settings.loadStrategy=FULL。不增加新的输入枚举/数据库台账；现有 payload 校验允许具名 generated 配置。模型层 generationStrategy 仍保留原 DIMENSION 专属规则。

SCHEMA_ONLY 只消费当前模型字段、类型、非空与键声明；既有 ModelSpec 校验继续保护逻辑引用，执行依赖快照不要求逻辑上游先有实现。结构模式禁止 filters/casts/joins/聚合/去重、分区与增量策略，不忽略已声明但不支持的内容。

dbt 默认 table materialization 在 pre-hook 前会清理中间/备份关系，不能用它配一个前置检查冒充无覆盖物化。采用正式包内独立 `dts_schema_only` materialization：只执行 CREATE TABLE 和事务提交，不执行 DROP/TRUNCATE/ALTER 原表/源查询；同名已有目标无论结构如何都返回冲突，不占用或覆盖旧表。请求重放继续返回原候选/运行。

`build-intents.buildMode` 缺省 DATA_BUILD；显式 SCHEMA_ONLY 必须匹配当前实现的上述生成器，模式参与请求及运行版本判断，不能将普通运行降级为创建空表。编译产物/实现校验和包含生成器及声明结构，物化结果用原运行/目标核验派生。结构成功不生成质量通过、PUBLISHED 或分析就绪证据。

### K34 与 T18 冻结切片

- delivery-status 追加 modelingResult：当前模型/实现修订与校验值、环境、候选、运行组、目标表、观察时间；完成必须匹配当前不可变候选条目、实现修订、成功运行及 VERIFIED 关系证据。无证据为 UNKNOWN，不用质量/发布/分析状态推断建模完成。
- 正常建模导航仅 definition/implementation/verification；verification 主动作只允许建模动作。完成主操作返回列表，次操作按 modelSpecId/environment 到数据管理。
- delivery 旧深链继续显示历史只读结果和数据导航，禁止在旧页隐式执行发布。现有数据治理 owner 和命令权限保留。

### K35/K36 冻结切片

- 接入使用既有 destinationConfig JSON 的 modelTarget 对象保存版本绑定；字段为 schemaVersion=1、modelSpecId/modelRevision/modelChecksum、implementationRevision/implementationChecksum、environment/candidateId/runGroupId、dataSourceId/databaseName/schemaName/tableName 和声明 columns。不新增关系表、不复制 ModelSpec。
- 目标由服务读取当前 ODS/SOURCE 物化证据和执行目标目录身份解析；客户端不得以自由表名替代。执行前经既有受信服务通道核对当前绑定，并在目标连接上只读核验字段。绑定目标禁止建表、加列、DROP、TRUNCATE 和自定义前后置 SQL；现有全量采集仅向该模型表追加，界面明确展示此差异。
- 文件、数据库沿用当前字段及技术列生成；缺列、类型或技术列不兼容即拒绝。API 仅支持既有 raw_record/技术列结构，不新增展平能力。
- 数据入口复用 catalog/search 的 modelSpecId/environment/candidateId 上下文及既有质量、Catalog CAS、发布和分析恢复组件；服务返回 dataPrimaryAction，旧建模 delivery 深链仅回看。人工治理字段仍由原 Catalog owner 保存。

### 9f73ea4ab 正式检查与边界修正

- 部署目录 Java：平台 54 项、接入 78 项通过，包含现有文件/API/Addax 执行与服务认证回归。前端 tsc 通过，接入/数据目录 31 项通过。
- 追加检查发现旧 finalizer 在物化事务内调用 CandidateQualityAssetRegistrationService。现移至数据模块显式登记命令，复用原唯一 Catalog identity owner；登记失败不改物化运行状态。
- dts_schema_only 运行结果探测增加普通表映射；旧宏/视图分支保持。该函数 GitNexus risk=HIGH（已告知），须执行物化运行完整定向回归。
- 以上新增修正尚待正式测试、构建与页面验收。


## F3 coherent change set 聚焦复核（2026-09-07）

单主代理审查，未逐文件启动 reviewer。检查编译/运行探测、目标身份/执行前字段核验、接入 DDL 路径、数据登记 owner、页面版本/未保存保护，以及正式交付资源链。

- 已修正：物化事务与数据登记耦合；自定义结构 materialization 被探测为未知类型；SOURCE 无实现重开未选择生成器；接入技术字段不能显式声明；API 原始记录缺少 jsonb 类型；贴源表列表中文类型和 API 字段类型选项遗漏。
- SOURCE 仅允许明确列出的既有 _dts_* 字段；不放开任意保留字段。生成器字段名称经 compiler identifier 白名单，类型经封闭类型表，拒绝任意 SQL 类型片段。
- 绑定目标在 provision 分支首先完成平台当前版本校验和目标连接只读结构检查，然后跳过全部 DDL；Addax 重新生成作业、拒绝 pre/postSql、禁止 FULL 的 TRUNCATE，删除发给插件的 modelTarget 元数据；API 使用原写入语义。
- 绑定服务校验租户/模型计划维护权；内部核验限 dts-ingestion 服务身份。数据登记复用原候选证据与唯一资产 owner，失败不修改建模运行成功状态。模型/候选漂移和旧深链仍须真实页面复验。
- 18533ca3e：Java 57/57、前端 21/21。aa4d60357：门禁 43/43、Addax 41/41、模型服务 62/62、tsc 通过。2ad00dc52：编译/结构/运行 50/50。262859a23：字段编辑与列表 9/9。
- 2ad00dc52 正式构建成功、包内三个镜像 SHA256 与 manifest 一致、结构宏一致；受控三服务启动健康/HTTP200。回退至记录旧镜像后三服务恢复、Chrome 重新登录进入工作台；等待脚本曾 60 秒超时，最终实际恢复检查通过。尚未生成 SOURCE 业务数据。
- 262859a23 正式交付构建进行中；真实四层旅程、接入写入、Chrome95 与离线运行证据仍未通过，不关闭 T20/T08/T14。
