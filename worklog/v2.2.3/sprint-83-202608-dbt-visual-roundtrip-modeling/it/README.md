# Sprint-83 集成验收计划

当前目录定义真实验收口径，并保留编码后聚焦回归的准入证据。它尚不包含真实浏览器、PostgreSQL + Airflow/dbt 物化、公共审计抽样或现场退役的 PASS 证据；这些只在当前制品部署后集中执行一次。

## 编码后准入证据（2026-08-03）

- 前端页面/路由聚焦回归：`4 files / 47 tests` PASS。
- 前端 dbt/ModelSpec API 契约：Vitest `4 files / 9 tests` PASS，Node source-contract `7/7` PASS。
- 后端表示、ZIP、source-only、apply/retry/撤销、高级草稿、物理预览、发布/Catalog serving、审计/可观测与 runtime lease：两组聚焦 Maven 回归合计 `253 tests / 0 failures / 0 errors`。
- dbt runtime 源码契约：`runtime-contract: PASS`；F0/T05 认证记录与 evidence manifest SHA-256 一致。
- TypeScript 无错，Chrome 95 target 生产 bundle 和前端镜像已生成并部署；`dts-platform-webapp:1.0.0` 为 `sha256:2bf603f5ce642319182ae4a89cc26e32063bc0ce910d9a7e6783f5b9f7bdc767`，内外入口 HTTP 200。当前未提供授权 DTS E2E 凭据和可写测试边界，因此 IT-00～IT-08 仍不标记 PASS。
- 联合可写验收用例 `e2e/sprint83-84-modeling-roundtrip.spec.ts` 已完成静态准入：Biome PASS、源契约 4/4、TypeScript PASS、Playwright 发现 3 个串行旅程、独立 Review APPROVED。它以本次 package/run/attempt/draft/candidate 资源 ID 绑定 ZIP 导入、高级 dbt、发布物化和公共审计证据，并在缺少显式写授权时 fail-closed；当前仍未启动浏览器或产生验收写入。

## 准入与兼容声明边界

- IT-00 只消费 H83-01 的候选/原始证据、F0/T05 登记的 `certificationProfileId` 与无敏感数据的 RT-01 最小执行项目，不在验收阶段重新发明 runtime profile、兼容状态或证据 owner。
- 工程 fixtures 可用于当前切片实现准入、契约回归和 fail-closed 验证；尚未取得客户脱敏 dbt 包只阻断“已兼容某客户实际项目/版本分布”的声明。S1/S2 不依赖 runtime；S3 另行消费 H83-01 原始证据与 F0/T05 认证登记。
- 客户兼容声明必须另有获授权脱敏包或客户受控环境画像证据；工程 fixture 通过不得被表述为客户 runtime、macro、package 或项目复杂度已认证。

| ID | 旅程 | 必须证明 |
|---|---|---|
| IT-00 | dbt runtime 认证证据消费 | 读取 H83-01 固定的 Core/adapter/transitive dependencies、PostgreSQL 数据源版本和 image digest，并校验它们与 F0/T05 登记的 `certificationProfileId` 完全一致；以 RT-01 重放 parse/compile/build/run、artifact 和 relation evidence；当前镜像标签不作为版本证明；交接证据缺失、checksum 不一致或 profile 未认证时返回 `DBT_RUNTIME_NOT_CERTIFIED`，不得降级执行 |
| IT-01 | 表示读模型契约 | 同一 model/implementation revision 下 logical/dbt/runtime provenance 与 checksum 可复现 |
| IT-02 | 普通可视化与高级实现隔离 | BUSINESS scope 不返回/展示 SQL；显式 TECHNICAL scope 复用同一 model/implementation revision，且无新增菜单/列表 |
| IT-03 | 可视化选择 dbt、高级 checkpoint 与候选预览 | DESIGNER_GENERATED 无 SQL 完成 dbt 物化选择；高级草稿校验无副作用，commit 创建新 Implementation Revision，CAS 冲突不覆盖；技术维护者可显式预览成功未发布 candidate 并看到非正式标识，普通用户不能请求 |
| IT-04 | 外部 dbt ZIP 导入父旅程 | 聚合 IT-04A/04B；IT-04A 是工程 P0 准入，IT-04B 是 P1 兼容扩展。父旅程不单独记 PASS，且 IT-04A 通过不得冒充 source-only/complex 或客户实际项目已兼容 |
| IT-04A | P0 · artifact-rich ZIP → canonical DRAFT | 仅 ZIP；使用固定 manifest/catalog 的 artifact-rich fixture 完成 inspect→mapping→preview→apply→`ModelSpec DRAFT + DBT_MANAGED Implementation Revision`→工作台精确 revision 深链；注入一个已选合格项持久化失败得到真实 PARTIAL，刷新恢复后 retry 不重放已成功项并最终成功；同 idempotencyKey 重放不产生重复修订；导入不直接发布/物化；全过程 0 Git、0 在线 packages 下载、0 模型 SQL 执行 |
| IT-04B | P1 · source-only/complex 静态导入 | source-only 仅在 enforced contract、字段唯一 `name` + 显式 `data_type`、literal 依赖闭包和业务语义全部成立时 apply；非 enforced、缺 type、无可信字段最多 `STRUCTURE_VIEW_ONLY` 且 apply BLOCKED；dynamic ref/source、自定义 macro 隐藏依赖、缺失 package 和 complex 资源按受影响 caller/downstream fail-closed；成功仍为 `DBT_MANAGED` |
| IT-05 | 重复导入、漂移、依赖选择与恢复父旅程 | 聚合 IT-05A/05B/05C；父旅程不单独记 PASS，三个子旅程必须分别保存命令、状态代数、持久化结果与审计证据 |
| IT-05A | 三方漂移与语义保护 | accepted base/current Implementation/incoming ZIP 技术三方矩阵稳定产生 SKIP/UPDATE/CONFLICT/BLOCKED（含 BLOCKED_REMAP）；ModelSpec 业务语义独立保留并重验映射；外部缺失、删除和重命名不得自动删除、覆盖或猜测映射 |
| IT-05B | 依赖闭包与选择门禁 | dynamic ref/source、自定义 macro 隐藏依赖和缺失 package 阻断 caller/downstream；BLOCKED 项不可进入 selected 集合；独立合格闭包可单独选择并成功，未选择的 BLOCKED 项不进入 attempt 汇总，也不得把该成功结果标记为 PARTIAL |
| IT-05C | PARTIAL、retry、重启恢复与前向撤销 | PARTIAL 只来自已选合格项启动后的逐项失败；覆盖全 SKIP、SKIP+FAILED/BLOCKED、纯 FAILED/BLOCKED 状态代数；source-only 前置问题 `retryable=false` 且修复后必须重新 inspect/preview；retry 不重复成功项，服务重启可恢复；前向撤销覆盖 UPDATE，CREATE 无 base revision 时明确 BLOCKED；CAS 冲突不覆盖且重放幂等；每个失败/阻断项具有 code/stage/category/message/retryable/recoveryAction/correlationId，汇总与明细一致且敏感正文为 0 |
| IT-06 | 发布/物化/Catalog serving | StageGate→ReleaseCandidate→DbtExecutionGateway→Airflow/dbt→relation evidence；PUBLISHED 推进 latestPublishedRef，新修订不可消费；MATERIALIZED 才切 serving；r2 failed/stale/乱序时旧 r1 serving 保持，外部 sync retry 不重跑 dbt |
| IT-07 | 安全审计与物理预览 | 恶意 ZIP、跨租户、权限、表级密级、ALLOW/MASK/DENY/UNKNOWN、显式加载、默认100/最大500/501、历史无行、no-store/无导出、中央审计零样例值与 outbox 重试；对引号、分隔符、注释、Unicode 混淆或越界限定名等恶意 relation identifier 必须在 SQL 构造/数据库调用前拒绝，并以 datasource 查询计数为 0 证明未发出查询；参数化篡改 model revision、implementation revision、candidate/version、attempt、pipelineRunId、observationAttempt、evidence ID/checksum 任一 pin，均稳定返回 `PHYSICAL_PREVIEW_EVIDENCE_MISMATCH`、0 行且 datasource 查询数为 0；普通已认证用户/建模角色直接调用旧 `/api/etl/dbt/preview` 只能得到 403/404 或同一安全服务的受控结果，原始行泄露为 0 |
| IT-08 | 旧建模边界与物理退役 | **调用边界验收**：新建模 UI/服务不调用 `/etl/dbt/run`、`/api/etl/dbt/preview`、共享 dbt 文件写或已退役导入脚本，旧深链按冻结兼容策略收敛。**物理退役残余风险**：`R-DBT-LEGACY-DAG`、`R-DBT-LEGACY-PREVIEW` 及旧 controller/service/parser/route/file 只有在 caller=0、客户环境画像、迁移/备份恢复和回滚证据齐备后才能物理删除；旧 preview 若有非建模 owner，必须先迁入同一 evidence/classification/masking 服务并封闭原旁路。条件未齐时明确记录 owner、残余调用与处置计划，不得仅凭源码扫描或命令退出码宣称完成 |

最终证据必须包含：测试命令及退出码、精确 dbt 版本/依赖锁/image digest、API 请求/响应摘要、PostgreSQL 行与 checksum、Airflow/dbt run 标识、Chrome95 四态截图、dts-admin 审计记录。占位说明不能作为 PASS。
