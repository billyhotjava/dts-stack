# F8 质量规则运行、报错与反馈契约及勘察账本

日期：2026-09-10；状态：DRAFT。来源：2026-09-10 两次真实运行失败（`727cbc0c-…` DATASET_SCOPE_BLOCKED、`a06452ba-…` RESULT_ID_REQUIRED）及随后的只读源码核验。仓库路径相对根目录；行号为登记时位置。

本账本是 F8 下游 Task 的唯一事实来源，Task 引用条目编号，不重复扫描。

## Context Ledger C56–C70

| 编号 | 已核实事实 | 源码证据 |
|---|---|---|
| C56 | 保存规则时对 SQL **只校验非空**，不解析、不查函数、不查作用域、不查结果集形状 | source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/QualityRuleService.java:661 |
| C57 | 运行时有两道预检：正则挡 DML（`WRITE_BLOCKED`）→ AST 作用域与函数白名单（`DATASET_SCOPE_BLOCKED`），都在连库之前 | QualityDatasetStatementExecutor.java:106、:122；service/sql/SqlValidationService.java:19 |
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

**开放问题**（须 T44 关闭，不得带进实现）：

1. 存量规则里有多少条 SQL 在现行约束下不可运行？按拒绝原因分类计数（无 id 列、函数越界、跨表引用），决定"保存前校验"对存量是拦截、告警还是只对新保存生效。
2. 多语句规则（`definition.statements`）现网是否真实存在、占比多少？决定 C60 的去重是否还有保留价值。
3. `RESULT_ID_REQUIRED` 历史运行记录条数，用于验证修复后的回归面。
4. 试跑接口 C68 的权限表达式 `GOVERNANCE_MAINTAINER_EXPRESSION` 与规则编辑页实际使用角色是否一致；不一致则页面按钮须按权限禁用而非报 403。

## 目标契约（草案，T44 冻结后方可编码）

| ID | 契约 | 语义与落点 |
|---|---|---|
| K67 静态校验 | `POST /api/governance/quality/rules/validate-sql`，入参 `{sql:string, datasetId:UUID}`，返回 `{valid:boolean, reasonCode:string\|null, detail:string\|null, message:string, requiresIdColumn:boolean, warnings:string[]}` | 纯静态、不连库、零副作用。复用 C70 的 `checkScope` 与 C57 的只读正则，不新建解析器。精确路径与是否并入既有 rules 资源由 T44 冻结 |
| K68 保存准入 | 规则保存时对 `definition.sql` 执行 K67；不通过则 400 并回传 `reasonCode/detail` | 替换 C56 的非空校验。存量规则的处置策略由 T44 开放问题 1 决定，默认只对本次提交内容生效，不回溯改写历史规则 |
| K69 失败行统计 | 单语句：直接复用 `countFailures` 结果，不再执行 C60 的去重查询；多语句：探测结果集是否有 `id`，无则按语句求和并在 `warnings` 标注"未去重" | 去掉 `id` 硬依赖。任何情况下统计失败都不得使整次运行判失败，退化为统计缺失 + 警告 |
| K70 结论分离 | 运行结果区分 `qualityOutcome`（PASSED/VIOLATION）与 `executionOutcome`（OK/FAILED+分类）两个维度 | 修正 C63 的压制：已查出违规行时业务结论必须保留并展示，基础设施故障单独呈现，不互相覆盖 |
| K71 工单准入 | 仅 `qualityOutcome=VIOLATION` 生成质量问题工单；`executionOutcome=FAILED` 走运维提示，不建待认领业务工单 | 修正 C64。试跑维持不建工单 |
| K72 页面反馈 | 运行详情按 K70 分区展示：业务结论、执行状态、具体原因（reasonCode+detail）、可执行的修改建议；规则编辑页展示函数清单与结果集要求，并接入 C68 试跑 | 文案映射须覆盖**全部**已知分类，新增分类无映射时回落到 reasonCode 原文而非笼统兜底（修正 C66） |

## 非功能预算（建议值，T44 基线后冻结）

| 约束 | 预算 | 检查/责任 |
|---|---|---|
| 静态校验 | 单次 ≤200ms（JSqlParser 解析上限 2s 已存在），不连库、不落库 | T48 计时并断言零 DB 交互 |
| 保存准入 | 不改变既有保存事务边界与幂等 | T48 并发保存与重放检查 |
| 失败统计 | 去重查询取消后，单语句规则运行 SQL 次数由 3 降为 2 | T45 记录实际语句数 |
| 兼容 | 既有 errorCode 取值与 `gov_quality_run.error_category` 列不变；不新增业务表 | T44 冻结，T50 回归既有 10 处 DATASET_SCOPE_BLOCKED 断言 |
| 安全 | 只读与绑定表作用域约束一律不放宽；静态校验不得成为探测他表存在性的旁路 | T48 越权/跨表用例，错误信息不泄露对象存在性 |

## 边界

不重写质量规则领域模型、不更换执行引擎、不新增菜单、不引入 SQL 方言转换层、不放宽 C57 的安全边界。开发目录只编辑/静态检查/review/commit/push；编译测试与构建部署在 deploy 经 ff-only 后执行。
