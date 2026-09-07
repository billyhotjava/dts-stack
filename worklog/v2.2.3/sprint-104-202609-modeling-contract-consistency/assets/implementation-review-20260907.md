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
