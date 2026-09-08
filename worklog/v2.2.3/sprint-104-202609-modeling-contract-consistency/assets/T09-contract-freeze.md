# T09 契约核验与冻结记录

**状态：IN_PROGRESS；本次冻结已证实的接口和帮助映射，剩余缺口见末表。** 本记录补充统一契约，区分当前能力与拟实施变更，不是运行验收报告。

## 核验范围与基线

- 2026-09-06：开发 HEAD `ac3c1c961`，部署 HEAD `5e00e914f`。本轮只读查看；未 pull、构建或重启。
- Docker 当前显示 deploy-dts-platform-1、deploy-dts-analytics-1 healthy，deploy-dts-platform-webapp-1 running；不据此宣称登录、质量执行、发布或 Chrome95 验收通过。
- GitNexus query 对发布相关概念未返回流程/符号，未报告 stale；随后按账本路径定向核对源码，未将空图结果当作无调用/低风险证明。
- 新增 `delivery-contract-fixtures.json` 是共享预期数据，状态 SPEC_ONLY；尚未接入前后端执行器，不记为测试通过。

## 冻结 A：第三步与第四步命令

令 `C=/api/modeling/plans/{planId}/release-candidates/{candidateId}`。UUID 由既有模型/候选上下文取得，禁止浏览器自行拼一个不属于该模型的 candidateId。

| 页面动作 | 当前接口/输入 | 当前结果和前置条件 |
|---|---|---|
| W3 开始/重试质量检查 | POST `C/quality`；body `{reason:string}`；If-Match `"release-candidate:{candidateId}:{version}"`，Idempotency-Key:string，部门上下文 X-Active-Dept 沿用原链路 | 候选内置 RUN_QUALITY 允许 BUILT/QUALITY_FAILED；服务仍校验租户、计划、漂移等条件；返回 ApiResponse<CommandResult> 及候选 ETag |
| W3 补跑治理质量 | POST `C/governance-quality/runs`；无 body；同版本/幂等头及部门上下文 | 当前允许 BUILT/QUALITY_RUNNING；只运行已绑定、可重跑的 MISSING/FAILED/ERROR/EXPIRED 项，排除资产/版本/绑定不匹配；新执行201，重放200 |
| W4 确认发布 | POST `C/publish`；body `{reason:string}`；同版本/幂等头 | 当前 PUBLISH 动作来自 QUALITY_PASSED/REVIEW_PENDING/APPROVED，实际命令再核验门禁；受理/推进不等于发布最终成功 |
| W4 发布部分失败恢复 | POST `C/publication/retry`；body `{reason:string}`；同版本/幂等头 | 候选 PARTIAL 的 RETRY_PUBLICATION；与分析同步重试是不同命令 |
| W4 分析重试 | POST `/api/modeling/model-specs/{id}/serving-sync/retry`；If-Match `"model-serving-sync:{id}:{version}"` | 不调用 C/publish，不创建物化任务 |

治理重跑响应：`{candidateId:UUID,replayed:boolean,runs:Array<{ruleId:UUID,ruleVersionId:UUID,bindingId:UUID,runId:UUID,status:string}>}`，外层保持 ApiResponse。命令模型继续复用现有 CommandResult，不另定义一个简化“成功=true”响应。

关键错误已核对：缺 If-Match 为428 `MODEL_RELEASE_CANDIDATE_IF_MATCH_REQUIRED`；缺幂等键为400 `MODEL_RELEASE_CANDIDATE_IDEMPOTENCY_KEY_REQUIRED`；候选版本冲突为409；治理无可重跑绑定为422 `MODEL_SPEC_GOVERNANCE_QUALITY_BINDING_REQUIRED`；治理重跑状态不允许为409 `MODEL_SPEC_GOVERNANCE_QUALITY_RERUN_STATE_INVALID`。

**部分成功分支**：`C/quality` 先运行候选质量命令，再调用治理重跑；后者失败可返回409 `MODEL_SPEC_GOVERNANCE_QUALITY_START_FAILED`。前端收到该错误必须重读候选和运行记录，不能把已有状态转换当作未发生。仍在 QUALITY_RUNNING 时，按当前条件走治理补跑入口，不直接重新提交构建或生成新幂等键重做全部步骤。完整部分失败/重放覆盖归 T11。

`publish-intents` 是保留兼容的旧入口，不作为新向导“下一步”通用按钮。当前 ModelPublicationIntentService 在 BUILT 记录 PUBLICATION_REQUESTED 并推进 QUALITY_RUNNING；质量协调器只推进 QUALITY_PASSED/FAILED，审查协调器在适用旧策略下推进 REVIEW_PENDING。已核对路径没有把质量完成当作最终 PUBLISHED。W3 采用独立 quality 入口，W4 明确调用 publish；IT-15 仍须实际验证未确认发布时发布次数为0。

证据：`ModelReleaseCandidateResource.java:385,434,521,543,631`；`CandidateGovernanceQualityRerunService.java:24`；`ModelPublicationIntentService.java:147`；`ModelPublicationQualityReconciler.java:114`；`ModelPublicationReviewReconciler.java:100`。均在 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/` 对应 service/modeling 或 web/rest。

## 冻结 B：质量规则是资产级规则，不是任意可重新绑定的全局规则

当前规则 owner 为治理服务：每个规则 `datasetId` 对应一个资产，发布版本有一条匹配该资产的 GovRuleBinding。`QualityRuleService.validateDatasetBindingContract` 明确拒绝多资产、规则资产与绑定资产不同、非 DATASET 作用域。

| 操作 | 当前 API | 请求/结果 |
|---|---|---|
| 查看规则 | GET `/api/governance/quality/rules`；GET `.../{id}` | 现有部门/资产可见性过滤；列表未核实支持 datasetId 服务端筛选，不能只靠前端过滤承担授权 |
| 查看版本 | GET `.../{id}/versions`、GET `.../{id}/versions/{version}` | 区分业务版本号 integer 与 ruleVersionId UUID |
| 新建/修改规则 | POST `/api/governance/quality/rules`、PUT `.../{id}` | QualityRuleUpsertRequest；修改创建新版本，不是局部“添加绑定”操作 |
| 发布规则版本 | POST `.../{id}/versions/{version}/status` | `{status:string,notes:string}`；目标 PUBLISHED 仍按当前有效性/权限校验 |

Upsert 已核实字段：`code/name/type/category/description/owner/ownerDept/severity/dataLevel/executor/frequencyCron:string`，`template/enabled/publishNow:boolean`，`datasetId:UUID`，`definition:object`，`bindings:Array<{datasetId:UUID,datasetAlias:string,scopeType:string,fieldRefs:string[],filterExpression:string,scheduleOverride:string}>`。字段是否必填、definition 具体规则类型与 SQL 合法性沿用既有验证，不能以 DTO 字段存在认定全部可任填。

**对 T11 的修订决策**：W3 展示当前输出资产的有效规则及版本、检查结果，提供“配置质量规则”进入当前资产规则管理；选择一条已有同资产规则是查看/执行，不额外写一个候选绑定。没有规则则在当前步骤复用新建表单，datasetId 固定为当前输出；发布规则后再次读取候选治理证据。借用其他资产规则只能取模板内容创建当前资产的新规则，不移动原绑定、不改写原发布版本，也不伪造独立绑定接口。是否提供“只选择部分规则作为门禁”不属于本次新增能力，继续遵守治理 owner 的全部适用规则政策。

证据：`web/rest/GovernanceResource.java:167,195,213,265,310`；`service/governance/QualityRuleService.java:327,556,692`；`service/governance/request/QualityRuleUpsertRequest.java`。规则编辑 CAS/重复版本竞争当前还未冻结，不能标 T11 READY。

## 冻结 C：四步帮助映射与兼容

复用 HELP_TOPICS 和 HelpCenter。拟定新增主题 ID 如下，随前端包离线交付：

| step | topic ID | 完整帮助深链 |
|---|---|---|
| definition | model-definition | `/settings/help?topic=model-definition` |
| implementation | model-implementation | `/settings/help?topic=model-implementation` |
| verification | model-verification | `/settings/help?topic=model-verification` |
| delivery | model-delivery | `/settings/help?topic=model-delivery` |

新向导仅在精确 workbench 路径识别 step；缺省/非法 step 回到既有 model-center 主题。其他页面维持最长路径前缀匹配，`/settings/help?topic=...` 的显式有效 topic 优先。不得让任意业务页 query.topic 覆盖其帮助或让未知参数崩溃。

拟定复用签名 `resolveHelpTopic(pathname:string, requestedTopicId?:string|null, wizardStep?:string|null):HelpTopic`，第三参可选，旧调用兼容。HelpCenter 传入 location.search 解析的 step，仅帮助页传 requestedTopicId；输出主题 ID 的共享 fixtures 供函数及右上角入口交互测试复用。

时间字段说明归 model-implementation，随该字段实际落点 W2；W1 帮助链接相关主题，不复制一段容易漂移的规则。打开/关闭 Sheet 不写模型、不丢输入；完整手册导航遵守既有离开保护。

## 已核对的分析运行预算

当前配置 `dts.analytics.enabled=true`，连接/读取默认超时5秒/20秒；worker 同时受 `dts.modeling.catalog.semantic-sync-enabled=true` 控制，默认每30秒调度。批量50、租约2分钟；首次失败后的自动延迟为1、5、15、30、60分钟，即最多5次自动重试，之后停止自动重试。以上为源码默认值，未读取实际密钥/环境配置，也不是现网性能实测。

目标策略：明确 enabled=false 显示未启用；启用但缺 URL/token 显示配置失败，不显示未启用。分析启用而同步 worker 被关时显示交付已暂停及原因。暂时失败沿用有界预算；缺关联先补齐关联，认证/非法目标不无限重试。T13 需保证单次任务不超过租约或实现有界续租，否则会被重复领取；尚未证明持久化准备任务/幂等方案，不能将现有预算直接视为方案已实现。

证据：`config/DtsAnalyticsProperties.java`；`service/modeling/serving/CatalogModelSemanticSyncWorker.java:14,31`；`CatalogModelSemanticSyncService.java:25,216`。

## 资产字段 owner 的已定范围与剩余开工门槛

W4 简化维护第一版只包含 `owner:string|null`、`description:string|null`；复用目录 owner，修改不重物化。source/schema/table、名称/模型业务域/分层不在该表单编辑；ownerDept、classification、tags 等高级治理仍从已有治理页面维护并按上下文返回。帮助解释字段含义，页面保留当前保存/冲突结果。

原2026-09-06基线的 PUT `/api/catalog/datasets/{id}` 是多字段赋值，CatalogDataset 当时未声明 @Version（后续 T12 已新增）；不能安全地把旧对象读出、合并两个字段后全量 PUT。目标需要同一治理 owner 的局部更新及并发保护，不在模型服务另写资产表。

| 未冻结项 | 具体风险/所需交付 | 归属 |
|---|---|---|
| 资产局部更新并发 | 精确 PATCH/ETag 或版本列方案；既有全量 PUT、自动投影和其他写入者均须遵守兼容策略，不能只保护新按钮 | T09/T12 |
| 规则更新并发 | Upsert 会创建版本；重复点击/两窗口竞争、已发布规则重用的响应和版本保护需固定 | T09/T11 |
| 多输出聚合 | quality/asset 需要数组和逐项错误；modelRevision、实现、环境、candidate 与规则版本证据须完整锁定 | T09/T10 |
| 分析关联唯一性 | 租户/平台源 ID 唯一键与旧数据重复预检、元数据准备及租约；必要前向迁移 | T13 开工契约 |
| 真实验证基线 | 登录/Chrome95、实际治理/分析数据源、失效样例及当前接口错误码实测 | T09/T14 |

上述为2026-09-06开工时的 GAP 记录；当前 T09–T14 均 IN_PROGRESS，已实施契约分别见 T10/T12/T13 与2026-09-08审查整改契约。已完成核验项可复用，不重复源码勘察；下一次只针对末表缺口推进。
