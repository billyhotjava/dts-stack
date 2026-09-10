# F8 质量规则运行、报错与反馈契约及勘察账本

日期：2026-09-10；状态：DRAFT。来源：2026-09-10 两次真实运行失败（`727cbc0c-…` DATASET_SCOPE_BLOCKED、`a06452ba-…` RESULT_ID_REQUIRED）及随后的只读源码核验。仓库路径相对根目录；行号为登记时位置。

本账本是 F8 下游 Task 的唯一事实来源，Task 引用条目编号，不重复扫描。

## Context Ledger C56–C70

| 编号 | 已核实事实 | 源码证据 |
|---|---|---|
| C56 | 保存规则时对 SQL **只校验非空**，不解析、不查函数、不查作用域、不查结果集形状 | source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/QualityRuleService.java:661 |
| C57 | 运行时有两道预检：正则挡 DML（`WRITE_BLOCKED`）→ AST 作用域与函数白名单（`DATASET_SCOPE_BLOCKED`），实际在目标连接和总行数查询之后；T48 前移 | QualityDatasetStatementExecutor.java:106、:122；service/sql/SqlValidationService.java:19 |
| C58 | 函数白名单原为 count/round/nullif/sum/trim，匹配的是**函数名拼写**而非能力；`btrim` 与已放行的 `trim` 在 PostgreSQL 中是同一函数 | QualitySqlScopeValidator.java:51（已由提交 4c58ce207 补入 btrim/ltrim/rtrim 并拆分拒绝原因） |
| C59 | `SqlValidationService.validate` 会产出追加 `LIMIT 1000` 的 `rewritten`，但执行器**丢弃 rewritten、仍执行原 sql**，该限流实际未生效 | SqlValidationService.java:41-46；QualityDatasetStatementExecutor.java:106-121 |
| C60 | 统计失败行时硬取名为 `id` 的列：`SELECT quality_statement.id AS row_id FROM (<用户SQL>) quality_statement`；表无 `id` 即抛 SQLException → `RESULT_ID_REQUIRED` | QualityDatasetStatementExecutor.java:228-233、:174-180 |
| C61 | 该统计**只在规则已查出违规行时执行**（`failingStatements` 非空），因此报错意味着检测成功、统计失败 | QualityDatasetStatementExecutor.java:145-152、:171 |
| C62 | 现网两张演示表均无 `id` 列：DWD 主键 `task_snapshot_id`，DWS 粒度为 `project_code + snapshot_date` | worklog/v2.2.3/it/prjdemo/README.md 阶段六/阶段七字段表 |
| C63 | `dominantFailureCategory` 返回**首个非 QUALITY_VIOLATION** 分类，基础设施错误因此**压制**同一次运行里已得出的业务结论 | QualityRunOutcomeSemantics.java:55-78 |
| C64 | 运行失败一律 `createIssueForFailedRun` 生成待认领问题工单，不区分数据质量问题与平台自身故障 | QualityRunService.java:598-600、:629-631 |
| C65 | `classifyExecutionError` 按**英文报错子串**匹配（does not exist / timeout / syntax error…），依赖驱动文案，非结构化 | QualityRunOutcomeSemantics.java:80-105 |
| C66 | 页面文案映射只覆盖 8 个分类；`RESULT_ID_REQUIRED`、`EXECUTION_ERROR`、`UNKNOWN`、`DISPATCH_REJECTED` 均落 default 兜底句 | QualityRunQueryService.java:229-238 |
| C67 | 规则编辑页是纯 SQL 文本框；提示只讲 schema.table 与只读，**未声明函数清单与 id 要求**；占位示例 `SELECT * FROM table_name WHERE column_name IS NULL` 在无 id 列的表上必然踩 C60 | source/dts-platform-webapp/src/features/data-quality/RuleEditorPage.tsx:494-505 |
| C68 | 后端**已存在**试跑接口 `POST /api/governance/quality/runs/dry-run`（强制 dryRun=true、triggerType=DRY_RUN、试跑不建工单），但规则编辑页从未调用，前端只有 DRY_RUN 的展示文案 | web/rest/GovernanceResource.java:470-498；QualityRunService.java:635；webapp 全仓无调用点 |
| C69 | 失败样本读取 `id/fail_column/actual_value/fail_reason` 时**有兜底**（id 缺失退化为行号），因此样本落库不受 C60 影响 | QualityDatasetStatementExecutor.java:264-270 |
| C70 | 作用域校验器现已返回结构化拒绝原因 `ScopeCheck{allowed,reasonCode,detail}`，含 UNSUPPORTED_FUNCTION / UNSUPPORTED_CAST_TYPE / UNSUPPORTED_SYNTAX / OUT_OF_SCOPE_TABLE / UNSUPPORTED_SOURCE_TYPE / INVALID_BOUND_TABLE / UNPARSEABLE_SQL / NO_TABLE_REFERENCE，可直接供页面使用 | QualitySqlScopeValidator.java:69-95（提交 4c58ce207） |

**T44 冻结**：现网画像、存量策略、兼容决定及验收矩阵见 [实施冻结记录](F8-implementation-contract-20260910.md)。只对新保存/重新发布内容校验，历史规则、运行与工单保留；旧试跑接口兼容且隔离正式证据。

## 目标契约（2026-09-10 已冻结）

| ID | 契约 | 语义与落点 |
|---|---|---|
| K67 静态校验 | `POST /api/governance/quality/rules/validate-sql`，入参 `{datasetId,definition:{sql?,statements?}}`，返回 `{valid,checksum,diagnostics:[{statementKey,reasonCode,detail}],allowedFunctions}` | 允许读取平台元数据并检查对象权限；禁止连接目标数据源、执行用户 SQL。复用现有只读及作用域解析器 |
| K68 保存准入 | 校验、保存、草稿试跑和执行共用有效语句解析；非空 statements 优先，逐条校验 | 不允许合规 sql 掩盖非法 statements；拒绝时返回结构化原因；不回溯改写历史版本 |
| K69 失败行统计 | 单语句复用违规计数，无 id 依赖；多语句无法精确去重时标记 UNDEDUPLICATED，精确失败行数为空 | 次数单独保留但不当去重行数；统计缺失或未去重不参与通过率、评分；采样失败保留已确认违规 |
| K70 结论分离 | qualityOutcome=PASSED/VIOLATION/UNKNOWN，executionOutcome=OK/FAILED，statisticsStatus=EXACT/UNDEDUPLICATED/UNAVAILABLE | 任意违规保留 VIOLATION；纯故障 UNKNOWN；版本化安全结果持久化到 metrics_json，通过安全 DTO 显式透出；旧 status 兼容 |
| K71 工单准入 | 正式运行仅 VIOLATION 建业务工单，包括违规与故障并存；纯故障不建；DRY_RUN 永不建单 | 历史工单不自动删除；沿用既有去重与审计 |
| K72 页面反馈 | 编辑页接新草稿试跑 `POST /api/governance/quality/rules/dry-run`，入参同 K67；详情显示双维度及安全原因 | 不需要 ruleId；checksum 对应当前输入，输入变更丢弃旧结果；试跑不写规则/运行/样本，不进入发布门禁及评分；未知分类回落 reasonCode |

## 非功能预算（实施预算）

| 约束 | 预算 | 检查/责任 |
|---|---|---|
| 静态校验 | 单次 ≤200ms（JSqlParser 解析上限 2s 已存在），允许平台元数据读取，禁止目标库连接和写入 | T48 计时并断言允许只读平台元数据查询，禁止目标数据源连接和用户 SQL 执行 |
| 保存准入 | 不改变既有保存事务边界与幂等 | T48 并发保存与重放检查 |
| 失败统计 | 去重查询取消后，单语句规则运行 SQL 次数有违规时由 4 降为 3（包含总行数查询） | T45 记录实际语句数 |
| 兼容 | 既有 errorCode 取值与 `gov_quality_run.error_category` 列不变；不新增业务表 | T44 冻结，T50 回归既有 10 处 DATASET_SCOPE_BLOCKED 断言 |
| 安全 | 只读与绑定表作用域约束一律不放宽；静态校验不得成为探测他表存在性的旁路 | T48 越权/跨表用例，错误信息不泄露对象存在性 |

## 边界

不重写质量规则领域模型、不更换执行引擎、不新增菜单、不引入 SQL 方言转换层、不放宽 C57 的安全边界。开发目录只编辑/静态检查/review/commit/push；编译测试与构建部署在 deploy 经 ff-only 后执行。
