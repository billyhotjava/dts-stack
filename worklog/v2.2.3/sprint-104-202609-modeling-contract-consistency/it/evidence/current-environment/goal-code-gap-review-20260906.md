# Sprint-104 目标与当前代码差距复核（2026-09-06）

**性质：源码与当次只读运行证据的差距复核，不是测试、构建、部署或浏览器验收。** 本文不将已有组件、历史源码专项或单次 HTTP 结果登记为功能完成。

## F1 证据边界

F1 已有源码专项、IT-04 dbt 样例和历史提交证据，只证明相应源码实现、专项测试或样例范围。它们不能替代登录、真实物化、质量、Chrome 95、正式离线包、已部署镜像或真实页面竖切片验收；F1-T08 仍保留这些阶段的独立证据责任。

## F2 任务差距

| Task | 当前已存在的源码能力 | 未闭合目标 | 状态 |
|---|---|---|---|
| F2-T01 | W3 质量、治理质量补跑、W4 发布/发布重试、serving-sync 重试的现有命令已核对；共享 fixtures 为 SPEC_ONLY。 | 资产局部更新 CAS、规则更新并发、多输出聚合 DTO、分析关联唯一性、登录/Chrome 95 基线；fixtures 尚未接入前后端执行。 | IN_PROGRESS，契约核验，不是功能验收。 |
| F2-T02 | F2-T02-A 帮助主题/step 解析；F2-T02-B1 单主动作与创作门禁；F2-T02-B2 首次设计保存。 | 没有 W1–W4 独立内容页、`step`/`candidateId` 深链、服务端交付聚合 DTO 或基于 allowedActions 的统一步骤导航；旧发布与物化弹窗仍并列。 | IN_PROGRESS，局部切片完成，不是四步向导完成。 |
| F2-T03 | 发布面板可展示候选治理质量证据并补跑；通用质量中心可管理规则。 | W3 未携带当前输出资产/候选/安全 returnTo 到规则编辑，未形成保存后返回并重读当前候选证据的闭环；规则更新并发契约未冻结。 | DRAFT。 |
| F2-T04 | 资产台账已有“编辑资产”入口，dataset 可进入治理信息页。 | W4 未提供 owner/description 的就地维护；缺目录局部更新 CAS、模型/step/candidate 返回上下文和双入口一致性验证。 | DRAFT。 |
| F2-T05 | 已有 serving-sync 状态读取和 If-Match 重试，编辑器可显示同步失败。 | W4 尚未以“分析准备”呈现状态/原因；任意平台源按需接入、唯一性、存量重复关系、目标表字段验证及真实可查询证据均未完成。 | IN_PROGRESS（仅 F2-T05-A）。 |
| F2-T06 | 有静态契约、源码专项和计划验收项。 | IT-08–IT-18、Chrome 95、正式构建/包、部署、浏览器和运行证据未完成。 | DRAFT。 |

## 已确认的当前运行根因与 F2-T05-A 边界

22:55:07，analytics 的 `DataLakeDatabaseInitializer` 请求 platform `/infra/data-sources` 发生 I/O 失败。初始化是单次启动动作，失败后没有自动恢复；23:04 随后的 semantic publish 返回 HTTP 400，本轮只读查询 `analytics_database` 为 0 行。请求 payload 包含 `platformDataSourceId`，因此不支持将根因归为 `dataSourceName=null`。

本轮只实施 F2-T05-A：为内置数仓启动初始化失败增加有界自动重试和专项测试。不得扩大为任意平台源按需接入、唯一约束或目标表/字段验证；该启动缺陷可以解释本次缺失关联导致的 HTTP 400；其它 HTTP 400 原因不在本切片范围内。代码已提交推送为 `6191dbca4`；部署目录同 SHA 的专项测试 11/11 通过，JAR 构建通过。镜像部署、故障恢复实测及实际可查询仍未验证。

## 后续顺序

1. F2-T05-A：完成有界恢复实现后，以专项测试验证重试、上限和不重复创建；再单独记录构建/部署/运行结果。
2. F2-T02-B3：用现有工作台承载 W1–W4、`step`/`candidateId` 上下文和服务端动作，不以旧弹窗替代步骤页。
3. F2-T03：在 W3 固定当前输出资产，复用规则页并安全返回，保存后重读候选质量证据。
4. F2-T04：待目录局部更新 CAS 定案后，将常用资产维护接入 W4；随后补 F2-T05 完整接入约束与 F2-T06 实际验收。

## F2-T05-A 本轮验证结果

- 流程：开发目录仅编辑/静态检查/commit/push；部署目录 `git pull --ff-only origin v2.2.3` 至 `6191dbca4` 后执行测试及构建。
- 命令：在部署目录的 Maven 3.9.9 / Temurin 21 容器内，以 source 为工作目录执行 `mvn -B -Dmaven.repo.local=/home/billy/.m2/repository -s /home/billy/.m2/settings.xml -pl dts-analytics -am -Dtest=DataLakeInitializationRetryTest,DataLakeDatabaseInitializerTest,SemanticPublishResourceTest -Dsurefire.failIfNoSpecifiedTests=false package`。
- 结果：11 tests，0 failures，0 errors，0 skipped；`BUILD SUCCESS`，27.302 秒。新增 10 项验证启动前不执行、依赖恢复、事务失败、次数上限、重复触发、已有源、缺失源、稳定 ID 注册和元数据失败边界；既有 1 项验证语义发布按平台源 ID 匹配。
- 产物：部署目录 `source/dts-analytics/target/dts-analytics-2.2.3-SNAPSHOT.jar`；日志 `/tmp/s104-datalake-retry-verification.log`。本轮没有重建或替换业务容器，无容器补丁、数据库修改或运行状态强制清理。
- 代码影响：GitNexus upstream 为 LOW，初始化方法 0 直接调用，类仅 DatabaseResource 引用；静态核对启动事件已迁移至重试协调器，数据库识别方法未变。提交前 detect_changes 为 low；新增调度器和测试由实际 diff 与专项测试补充核对。
- 限制：GitNexus 重建出现 worker 超时并回退串行；长时间未完成，已终止本轮重建，未宣称索引已刷新。影响结果结合未改变的初始化源码使用；本轮没有把静态调用图当作 Spring 事件或调度执行证明。提交钩子提示 lefthook 不在 PATH，不计为钩子通过。
- 运行验收待办：使用正式镜像验证平台晚启动时源注册自动恢复，再通过既有 serving-sync 重试确认当前模型结果；同时核对物化/发布次数未增加。耗尽 6 次后先恢复依赖，再重启 analytics 重新尝试。单实例有界恢复不替代多实例唯一性或任意源自动接入设计。
