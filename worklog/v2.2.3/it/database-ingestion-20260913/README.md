# 数据库接入保存失败：根因、修复与验证

## 现场事实

- 2026-09-13，源码基线 `6db8f37e2`，运行中 platform / ingestion / platform-webapp 镜像来自 `4d9fecbe3`。
- 业务“花卉租赁管理”和子数据域“花卉数据管理”已保存；连接 `prs_conn` 为 ACTIVE。
- 12:28:55 和 12:29:26，`prs_db` 创建成功后调用 `/api/ingestion/tasks/4/admit`、`/api/ingestion/tasks/5/admit` 失败。
- 两条任务均为 draft，目标数据源已选，`target_dataset_id` 为空，表映射数为 0。仅执行只读查询，未修改业务库或删除重复草稿。
- 两条任务的 `sync_mode=full_refresh`，`sync_schedule` 和 `airflow_dag_id` 均为空，即本次现场配置属于手动全量接入。
- 因此报错发生在“任务创建后的自动准入”，不是花卉业务/数据域创建失败，也不是已保存的 `prs_conn` 连接失败。

## 根因与关联缺陷

| 缺陷 | 原因及结果 | 修复 |
| --- | --- | --- |
| 创建任务漏掉物理计划 | 创建改为始终保存草稿后，提前返回位于表发现、表选择和映射生成之前 | 草稿保存前冻结源表范围及对应目标表，不创建运行作业 |
| 首次接入无法生效 | 准入要求已有单一 CatalogDataset，而数据库执行器在执行时才建目标表；多表任务也不应被迫绑定单一质量资产 | 共用 ManagedDatabaseLandingPlan 校验完整的托管 PostgreSQL 落地计划；仅无质量策略、无显式资产引用时允许资产待观测 |
| 全部表编辑丢失范围 | 创建请求支持 streams，但更新 DTO 未带 streams，前端按空 selectedTables 生成映射 | 创建和编辑均在提交前按当前筛选条件发现并冻结表清单 |
| 重试产生重复草稿 | 创建已提交，准入失败后前端丢失任务 ID | 准入前保存 ID；当前向导重试先读取并更新已保存草稿，读取失败不回退新建 |
| 编辑误扩大源表范围 | 更新逻辑合并 reader 表和 writer 表作为来源列表 | 数据库更新只用 reader 表，保留文件路径的既有行为 |
| 多表静默漏表及目标冲突 | 映射按 Math.min 截断；去除 Schema 后可能同名 | 拒绝数量不一致、重复目标、未解析占位符及超过 1000 张表的计划 |
| 调度格式误判 | 创建/更新保存 manual、cron:、interval:，设计校验却把它们全部当裸 Cron | 按已持久化的调度格式校验，保持无效间隔拒绝 |
| 改为手动仍保留旧调度 | 更新 DTO 对手动调度省略字段，partialUpdate 保留原值 | 显式提交 manual，确保后续准入使用手动调度 |

## 不变量与边界

- 质量启用时仍要求真实目标资产、可读性、启用状态、物理目标一致和已发布规则绑定；显式无效资产引用不能降级为首次落地。
- 资产待观测不等于质量通过，不返回可信可用。复用现有 TargetTableProvisioner、ODS 映射及资产观测流程，不伪造 CatalogDataset 或直接回填数据库。
- 后续执行入口源码复核：详情按钮依据 active 状态和有效版本，执行代理检查任务权限，执行服务检查 active 状态及密级封存，没有再次把“单一目标资产缺失”设为普通首次落地的执行前置条件。此项为源码证据，不代替实际读写执行验收。
- 来源连接权限、目标连接权限、密级封存、审计、版本化草稿和执行器的非破坏全量替换保持原有门禁。
- 自动恢复覆盖同一向导实例的准入失败重试；页面关闭后应从任务列表进入已保存草稿。创建请求本身响应丢失的跨刷新幂等不在本次新增契约内。
- 已绑定质量资产的任务改目标会继续触发一致性校验；不自动解除质量绑定。API 与文件接入保留既有策略。

## 验证记录

- GitNexus 修改前影响分析：LOW；映射生成直接影响 createTask/updateTask，平台引用校验影响设计保存、设计校验、准入。路由扫描未识别 Java 路由，补用源码核对。
- 复现提交 `c5f40bf68`：IngestionFlowProjectionServiceTest 的新增数据库首次落地用例稳定触发原始 422 TARGET_ASSET_UNRESOLVED。
- 基线 IngestionTaskResourceTest 存在既有 HTTP 状态断言与统一异常处理不一致；分别记录，不能当成本次修复造成的回归。
- 开发分支修复提交：`c5f40bf68`（复现）、`bdbc5ef9d`（修复）、`5b1a7ec03`（调度与测试补充）；针对性后端 50 个、前端 47 个用例通过。
- 发布分支 `fix/database-ingestion-20260913`：以现网 `4d9fecbe3` 为基线，仅带入上述 13 个文件；修复文件与开发分支逐文件比较一致。未带入开发分支尚未部署的 F9 建模权限改造及迁移。首轮提交为 `ad052ea62`，最终提交为 `d3654389f37a70bab2567e12f8f5ef8f721e7ba4`。
- 在 `/data/dts-stack` 拉取并核对发布 SHA 后，重新 `clean test`：公共计划校验 2、平台准入投影 10、接入创建/编辑 6、手动全量准入 2、目标表处理 16、设计校验 13、准入事务 1，共 **50 个后端用例通过**；前端 4 个测试文件共 **47 个用例通过**。
- 后端入口：`mvn -B -f source/pom.xml -pl dts-platform,dts-ingestion -am -Dtest=ManagedDatabaseLandingPlanTest,IngestionFlowProjectionServiceTest,IngestionTaskDesignServiceTest,IngestionTaskResourceTest#createTask_database*+createTask_jdbcFlagsCannotBypassDraftAdmission+createTask_fileFlagsCannotBypassDraftAdmission+updateTask_database*,TargetTableProvisionerColumnResolutionTest,IngestionTaskAdmissionTransactionTest,IngestionTaskServiceTest#admitManualFullRefresh* -Dsurefire.failIfNoSpecifiedTests=false clean test`（实际执行时对 `-Dtest=...` 整体加单引号）。
- 前端入口：`pnpm exec vitest run src/pages/foundation/access/useAccessPlanWizard.test.ts src/pages/foundation/access/accessPlanPayload.test.ts src/pages/foundation/access/AccessPlanWizardPage.test.tsx src/pages/foundation/access/AccessPlanSteps.test.tsx`。
- 首轮正式构建捕获本次新增“全部表”路径的 TS2322（连接 ID 可选）；通过调用前必填校验修复，开发分支提交 `f870792e5`、发布分支提交 `d3654389f`。最终前端 **48 个用例及完整 `tsc --noEmit` 通过**；最终提交与 `ad052ea62` 的所有后端源文件/测试没有差异，沿用已通过的 50 个后端用例证据。
- `dts-common` 全量 `verify` 未通过：`CatalogTagAuditActionCatalogContractTest.canonicalAndDockerFallbackCatalogsRemainByteForByteSynchronized` 发现 canonical 与两个 Docker fallback 审计目录不同。修复前 `6db8f37e2` 与修复后 `5b1a7ec03` 的三个文件各自 SHA256 完全相同，属于既有偏差；差异涉及 `governance.qualityTasks`、`governance.qualityWorkflows`、`modeling.glossary`。不把它记为全量验证通过。
- 完整日志位于 `/data/dts-evidence/database-ingestion-20260913/`；`release-backend-tests.log`、`release-frontend-tests.log` 对应发布提交，`mainline-*` 保留初次复现与开发分支校验。Git hook 提示本机未找到 lefthook；另行执行了静态差异检查和上述正式目录测试。

## 发布范围与回退边界

- 现网容器标签证实 Compose 项目为 `dts-stack`，工作目录与配置为 `/data/dts-stack/docker-compose-app.yml`；`/opt/dts/release/dts-stack` 当前不存在。本次沿用现网项目，不迁移运行环境。
- 仅发布 `dts-platform:1.0.0`、`dts-ingestion:1.0.0`、`dts-platform-webapp:1.0.0`。已停止的 `dts-dbt-runtime-init` 也引用 platform 镜像，须随 platform 更新其引用；初始化脚本仅校验运行目录，拒绝修改非空且不合规的目录。
- 与实际现网提交相比没有数据库结构、Compose 或挂载配置变更，无业务数据回填。部署前查询接入执行记录仅有 success/failed，没有运行中的执行。
- 原镜像 ID 与挂载清单保存在 `before-containers.json`；旧镜像保留用于按原版本标签回退，禁止删除数据卷或以容器补丁替代发布。
- 最终正式构建入口：`DTS_BUILD_EXPECTED_SHA=d3654389f37a70bab2567e12f8f5ef8f721e7ba4 bash builds/dts-build.sh --image dts-platform dts-ingestion dts-platform-webapp --legacy --opmanager-output /data/dts-stack/builds/opmanager-dist/database-ingestion-d3654389f.tar.gz`，日志 `final-formal-build.log`；首轮失败日志单独保留为 `formal-build.log`。
- 正式构建、交付包、容器部署和回退验证待补充；真实页面验收另列，不以健康检查替代。

## 扩展检查发现的未闭合项

- **定时全量接入**：`stageAdmissionArtifacts` 仅对无已有 DAG 的手动 full_refresh 延迟生成作业；定时分支仍在执行记录产生之前调用 Addax 作业生成，而 `applyFullRefreshPreSql` 必须绑定真实 executionId。该路径存在制品生成时序冲突，需通过按次执行上下文驱动调度作业解决，不能伪造执行 ID 或退回预清空。此项仅完成源码定位，尚未修复或验证，不能宣称定时全量闭环通过。
- **旧全量执行测试夹具**：`IngestionTaskFullRefreshExecutionTest` 有 5 个用例只桩定 findById，现有执行入口已使用 findByIdForUpdate，因此报 Task not found，未到达执行主体。该类失败不证明实际运行失败，也不算已通过；本次另用准入事务与目标表处理的针对性用例记录有效证据。
- **真实业务验收入口**：本轮浏览器工具返回无可用浏览器，构建目录也无现成 e2e/.auth/user.json。不能用匿名探测或服务身份替代花卉业务的实际操作验收。
