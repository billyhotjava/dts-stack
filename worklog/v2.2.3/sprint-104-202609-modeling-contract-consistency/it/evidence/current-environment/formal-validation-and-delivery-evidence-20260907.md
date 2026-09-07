# Sprint 104 正式验证与离线交付证据（2026-09-07）

最新增量见 [编码收尾记录](coding-completion-20260907.md)：`32b7e1309` 定向回归和前端构建通过，尚未打包部署；运行仍为 `296`。下文各历史版本的测试、打包和部署边界分别保留。

**状态：IN_PROGRESS。** 本记录归档部署目录的正式测试、构建、交付包、容器替换和隔离迁移结果；不证明 Chrome 95 或真实租户的页面旅程通过。

## 证据版本与边界

- 当前已部署源码：`296605b39f98816dd4992ff2cd89777e9c6b9e9e`；下文保留 e04、e83、90d 历史包、迁移、部署和故障观察证据。`c275e244b` 正在正式构建，不能写为已部署或业务复验通过。
- 路由隔离修复正式构建、离线包校验和受控部署已完成（`/tmp/s104-release-e04a1fc34.log`）；当前三服务使用 `s104-e04a1fc34cb0` 标签。
- 隔离迁移验证脚本提交：`3aa077d0c49d48aa0600705a11c3676fb5bf95ce`。
- 执行目录：`/opt/prod/s10/deploy`；开发目录未用于编译、构建或测试。
- 当前状态：296 包的三个镜像已由发布流程部署；已完成部分 authenticated Chrome 走查，但 Chrome 95、离线安装和 IT-08 至 IT-18 的完整正反分支仍不能由本记录替代。

## 已执行的专项验证

| 范围 | 结果 | 原始证据 | 说明 |
|---|---:|---|---|
| 后端目标组首轮 | 73 项通过；整组未通过 | `/tmp/s104-backend-targeted-final.log` | 该次总计 96 项，其中 `ModelMaterializationRunArtifactServiceTest` 的 23 项因测试夹具 `UnfinishedStubbing` 出错；不得将 73 项写为该次 Maven 成功。 |
| 后端物化恢复组 | 29/29 通过，Maven `BUILD SUCCESS` | `/tmp/s104-recovery-final.log` | `ModelMaterializationRunArtifactServiceTest` 23 项和 `PlanOperationalRunServiceTest` 6 项。 |
| analytics 专项 | 15/15 通过，Maven `BUILD SUCCESS` | `/tmp/s104-analytics-final.log` | 含语义发布、平台源注册、启动有限重试和 Data Lake 初始化。日志中的重试耗尽为受控测试分支，不是测试失败。 |
| Node 原生质量前端契约 | 21/21 通过 | `/tmp/s104-formal-node-contracts.log` | `node --test` 执行，非 Vitest。 |
| 最终 source-contract | 9/9 通过 | `/tmp/s104-source-contract-final.log` | 单个 Vitest 文件通过。 |
| 前端正式构建 | 通过 | `/tmp/s104-formal-frontend-rebuild.log`、`/tmp/s104-release-build.log` | `pnpm build` 成功；存在既有大 chunk 警告，未导致失败。 |
| catalog 多输出组件 | 5/5 通过 | `/tmp/s104-catalog-multi-output.log` | 同一命令中 helper 因缺少 jsdom 失败，未将该 helper 写为通过。 |
| catalog 输出 helper 复测 | 3/3 通过 | `/tmp/s104-catalog-output-contract-retest.log` | 在 `7c502e0d3` 上的定向复测。 |
| catalog 多输出 typecheck | 退出码 0 | `/tmp/s104-multi-output-typecheck.log` | 在 `480e3f8b0` 上执行。 |

上述是不同命令和范围的证据，**73、29、15、21、9 不相加为统一测试总数**。

## 隔离 Liquibase 升级、回退与重放

脚本 `it/verify-delivery-migrations.sh` 在提交 `3aa077d0` 的部署目录执行，日志为 `/tmp/s104-delivery-migrations-3aa077d0.log`，退出码为 0。

- 只创建并清理 `s104_platform_*`、`s104_analytics_*` 临时数据库；未使用业务模块 POM，未连接业务库。
- 平台 changeSet 验证旧 `catalog_dataset` 行保留、`version` 非空且默认 `0`、回退删除列、重放恢复默认值。
- analytics changeSet 验证旧行保留、可空扩展、`tenant_id + platform_data_source_id` 重复写入被唯一约束拒绝、回退删除两列、重放恢复约束。
- 脚本完成后的 `pg_database` 查询显示两个 `s104_*` 前缀临时数据库数量为 `0`。
- 在线数据库执行记录：platform `20260906-01-catalog-dataset-version` 于 `00:49:54` 执行；analytics `0054-01` 于 `00:49:09` 执行。隔离回退验证不回退线上 changeSet。

## 正式镜像与离线包

构建日志 `/tmp/s104-release-build.log` 显示三服务镜像构建和 OpManager 离线包生成完成。交付元数据位于 `/opt/prod/s10/deploy/data/sprint104-release/s104-e83b51076e21/`。

| 服务 | 镜像标签 | 镜像 ID | 镜像归档 SHA-256 |
|---|---|---|---|
| dts-platform | `dts-platform:s104-e83b51076e21` | `sha256:13c721e4eafd90b8caed743dc93846eaf5c06e1512224a31ff98a307d6fa4447` | `328a1a89467aed33838b6dcc45847b7c5498f8edf9bfc51cc7af2acee6f97ee6` |
| dts-analytics | `dts-analytics:s104-e83b51076e21` | `sha256:29ed6c9f2c5be11b48b77daeb6216cb5741480bc85d9ed5d613e85123394cd21` | `91ae99be7dd7be5e673b4dbb99bd4ceb07e9ab3a2ef665113e456bfe5775d12e` |
| dts-platform-webapp | `dts-platform-webapp:s104-e83b51076e21` | `sha256:9dbe565ce3e0eb31768ce594206d00fba896e94dbe567544badc7a65422a526c` | `edd1e17749b852b091f66f900107dbd67756b474a3716d229e46c3675b8a43ca` |

- OpManager 包：`dts-opmanager-upgrade-20260907-003926.tar.gz`，SHA-256 `586342cd0fbfa3e7495bf7dab523b8b0a4ce841d522b545e862e18050e7e8473`（`archive.sha256`）。
- `package-verification.log` 对已解包内容逐项校验为 `OK`，其中包含 `misc/release-manifest.json` 与 `misc/rollback-manifest.json`。
- `source.json`、`unpacked/misc/release-manifest.json` 将三镜像和归档校验和绑定到上述 `e83b51076e21` 源码版本。

## 已部署容器与离线只读验证

`pre-deployment.json` 与 `deployed-containers.json` 记录此次只替换了以下三个容器；前者为 `1.0.0` 标签和旧 image ID，后者为本包中的 e83 镜像 ID。

| 容器 | 运行结果 | 实际 image ID |
|---|---|---|
| `/deploy-dts-platform-1` | `running`、`healthy` | `sha256:13c721e4eafd90b8caed743dc93846eaf5c06e1512224a31ff98a307d6fa4447` |
| `/deploy-dts-analytics-1` | `running`、`healthy` | `sha256:29ed6c9f2c5be11b48b77daeb6216cb5741480bc85d9ed5d613e85123394cd21` |
| `/deploy-dts-platform-webapp-1` | `running`、无容器 healthcheck | `sha256:9dbe565ce3e0eb31768ce594206d00fba896e94dbe567544badc7a65422a526c` |

已对解包目录执行只读/可逆的离线预检：

```sh
bin/dts-upgrade-lite plan \
  --source <unpacked/dts-stack> --images-dir <unpacked/images> \
  --extra-dir <unpacked/misc> --target <独立临时副本>
```

- `plan` 退出码为 0，报告保留于 `/tmp/s104-offline-plan-e83b51076e21-azu99Y/logs/upgrade-lite-20260907-005218/`；没有使用正式部署目录作为 target，也没有执行 `apply` 或 `rollback`。
- 三份归档均由本地 `docker load -i` 加载，标签和 image ID 与 manifest 相同；加载后上述三个运行容器的 image ID 未变化。该命令没有拉取网络镜像的步骤或输出。
- 解包包中存在 `dbt_project.yml`，其 `model-paths` 声明 `models` 与 `dbt_model/models`；后者含 46 个文件，另有 7 个 macro 和 1 个 profile 示例。`models` 目录当前为空，因此本次只证明包内容和预检路径，未执行 dbt，也不声称离线安装或模型运行已验收。

## 运营源快照修复与 90d 部署结果

- `90d111280` 的运营源快照专项 44/44 通过，日志 `/tmp/s104-operational-source-final.log`。运营运行从发布记录固定的模型和实现版本读取源快照，候选构建与运营运行共用确定性 source YAML 渲染；没有加入硬编码测试表。
- 三服务正式构建退出码 0，日志 `/tmp/s104-release-90d111280.log`；包 `dts-opmanager-upgrade-20260907-010312.tar.gz`，SHA-256 `52f717ea4b1f8ff125cc5b34892f6fda5170f07e37573e2ce13874eba3e5e977`。
- 交付目录 `/opt/prod/s10/deploy/data/sprint104-release/s104-90d111280b89/` 中 `validation-summary.json`、`package-verification.log`、`archive.sha256` 记录 image tar、镜像 ID、源码 revision 和整包校验结果。
- 该版本含 W4 多输出资产维护。仅替换 analytics、platform、platform-webapp；前两者 healthy，webapp running 且没有容器 healthcheck。其他容器 ID/启动时间未变化。

| 服务 | `s104-90d111280b89` 镜像 ID |
|---|---|
| dts-platform | `sha256:4baf3179061c25d41ba60752110007e7cf59840a2b31b0f922b78866ca317c6d` |
| dts-analytics | `sha256:dfa4bd65bdadc49f002c1ac0c8895b2172b2d1b7015b7f2e46bc26fc3716f21a` |
| dts-platform-webapp | `sha256:e2d2068e30e5f764a3e8c4f706e71ca86766752fc8b43f710cc1d53abbe8724f` |

### 运营运行的两段故障与恢复边界

1. 原 dispatch `d727145f-2b91-3fb7-882e-37af67443cbc` 的 `MATERIALIZATION_SOURCE_MISSING` 已恢复：正常协调器在 01:09:01 将同一条记录提交到 Airflow，bundle checksum 为 `714fc3e4589b68e069eca2e1d3b346403621941475adc2fb25d86568041195e6`，没有重置数据库或更换幂等键。
2. 随后暴露任务路由错误：旧 MANUAL_ONLY 绑定把运营运行分配给 `dts_release_build_postgres_primary`。Airflow 准备阶段拒绝运营参数 `bindingId, bindingVersion, triggerType`，因此 SQL 未执行；finalize 也误走候选接口并返回 409。这不能记作重新运行成功。
3. 01:14:03 正常协调器将同一 dispatch 收敛为 `FAILED / MODEL_OPERATIONAL_AIRFLOW_RUN_FAILED`，并发占用自然解除；没有强制清理运行状态。旧失败记录应保留。
4. 后续修复限定为独立运营 DAG、绑定部署校验和及旧绑定的正常 repair 入口；两类 DAG 必须禁止互相覆盖，不降低 Airflow 参数校验，不要求重新发布模型来修复调度配置。源码修复、正式回归及部署已登记于下节；实际旧绑定 repair 和新的运营运行尚待登录后执行。

原始运行观察：90d release 目录下 `operational-recovery-observation.json`（01:12 时点）、`operational-terminal-observation.json`（最终 FAILED）及 Airflow prepare/finalize 日志；均为只读观察。

## 运营路由隔离修复的定向回归（e04a1fc34）

`/tmp/s104-operational-routing-e04a1fc34.log` 记录平台模块定向 Maven 回归：25/25 通过，覆盖 6 个测试类，且 Maven 为 `BUILD SUCCESS`。其中包括隔离 PostgreSQL/Testcontainers 的旧 MANUAL_ONLY binding repair：它固定生成每 binding 的运营 DAG、重算 deployment checksum、CAS 版本加一；陈旧 ETag 与带活动运营 claim 的 binding 都拒绝修改并保持行不变。

- 新发布 binding 不再引用 release-build DAG；旧 binding 通过既有 repair 命令在保留发布模型、资产和 scope 的前提下规范化为独立运营 DAG。
- 手工运营提交不再写入 DAG 文件。双向文件类型保护拒绝运营流程覆盖 `dts_release_build_*`，也拒绝 release 流程覆盖 `dts_plan_*`。
- Health 查询对旧错误 DAG 不读取共享 release DAG 的实际/最新运行证据；不可运行时仅给出 repair 动作。活动 `QUEUED`、`SUBMITTED`、`UNKNOWN` 运营状态不提供 repair 或重复运行。
- `PlanDagDeploymentServiceTest` **未**包含在这次 25 项定向命令中，不能把该类写为通过。

`/tmp/s104-operational-airflow-contract-901777dec.log` 记录 Airflow Python 合约 32/32 通过。该轮 Python 源码在相关提交中没有变化；结果仅复核现有 `_validate_operational_run_conf` 对 Java MANUAL conf 的接受，以及运营 prepare/finalize 使用 execution-bindings 内部端点。

此前 `901` 的 Java 编译括号错误和 `81` 的 Mockito 歧义失败均已在本轮源代码中修正；它们不是本轮成功证据，也不计入上述通过数。

## 路由修复正式交付与当前部署（e04a1fc34）

- 构建源码 `e04a1fc34cb0b4ef1641a1d8cfe21a7b5ee32f63`，正式构建退出码 0；在开发目录 commit/push 后，由部署目录 ff-only 拉取、核对 SHA 再测试与构建。
- 交付目录 `/opt/prod/s10/deploy/data/sprint104-release/s104-e04a1fc34cb0/`。
- 包 `dts-opmanager-upgrade-20260907-080229.tar.gz`，SHA-256 `7bbac852bab4905d3460bc85427d941fdc3bea78885d2361198572ccf4d852d1`。包和发布环境文件限制为本机用户可读；不把其中连接配置复制到测试记录。
- `package-verification.log`、`archive.sha256` 与 `validation-summary.json` 核对全包、文件、三项 image tar、实际镜像 ID、源码 revision 和 amd64 架构，全部通过。

| 镜像 | 实际 image ID | image tar SHA-256 |
|---|---|---|
| `dts-analytics:s104-e04a1fc34cb0` | `sha256:33983e9be3aa31e255d503f9dccf46ce5b2d6ee11d5812da894868a32ce109bf` | `4c9e18483826d75958f11f2401f1dd0753b223aff3828bb47e7962f6ac4f94f6` |
| `dts-platform-webapp:s104-e04a1fc34cb0` | `sha256:bd0c19ab1af0b5a37214da7fe09a015ec363cc1af61528c39348c7c27d808538` | `5f77bda04e0db50eb363111151b472b9e38a3d3468a3862c1fdeabc2c0f06ac8` |
| `dts-platform:s104-e04a1fc34cb0` | `sha256:0468f10fa2ca8bbb9d965e9dcdede0e3e86fa205faee768333140e3469b4d46b` | `5f656ebc2219d7198dddd131d5976ef3a7d17325c6c2162d2519f8ad385f6768` |

- 受控顺序 analytics → platform → webapp，日志 `/tmp/s104-deploy-e04-{analytics,platform,webapp}.log`。platform/analytics 为 healthy；webapp running，没有容器 healthcheck。
- `pre-deployment.json` 与 `deployed-containers.json` 确认仅这三个容器 ID/启动时间改变，其余 19 个容器未改变。没有开发目录容器、容器补丁或热修复镜像。
- 三份镜像归档经本地 `docker load` 加载，标签与 ID 再次核对一致；日志 `offline-image-load.log`，未拉取网络镜像。
- `dts-upgrade-lite plan` 在 `/tmp/s104-offline-plan-e04-cwi8a51v/target` 退出 0（只复制上一包的部署配置作为基线）；`offline-plan-result.json` 与 `offline-plan.log` 为证据。未运行 `apply`、容器回滚或完整离线安装。
- 该 e04 时点曾被转回 `#/auth/login`，因此当时没有写入业务复验通过。随后 296 包已完成 authenticated Chrome 走查；其 PASS/FAIL 结果和仍待复验项记录在本文末尾，不能回写为 e04 结果。
- 本记录的后续文档提交不改变产品镜像对应的 e04 源码；文档 HEAD 与镜像源码 SHA 分别追踪。

## 未执行或待确认

- 仅三服务容器的替换与上述运行状态已有记录；未执行离线包 `apply`、回滚演练、dbt 运行或真实业务流程验证。
- W4 的 owner/description 读取、保存和陈旧窗口冲突走查已在 authenticated Chrome 完成；目录入口的维护能力及分析失败状态仍有明确 FAIL，见本文末尾。
- 运营源快照和 e04 路由隔离修复的用户正常“再次运行”已完成浏览器复验，IT-07 记录的 binding v2 新 dispatch 为 COMPLETED；该成功不替代 W1–W4、F2 全量验收。
- Chrome 95、离线完整安装、四步 W1–W4 与 F2 IT-08 至 IT-18 的完整浏览器/运行时正反分支未执行。
- 本文不把历史失败日志、构建成功或离线包校验表述为 Sprint DONE。

## Chrome 运营再次运行复验（e04，2026-09-07）

登录 Chrome 后，从模型 `e71715b7-ad1d-49b5-86e2-a91fa91e1b07` 的“物化历史 / 再次物化”按页面正常流程执行“立即运行并核验”。新运行成功，不通过重置旧记录、直接改库或容器补丁实现。

- binding `69949db3-d42d-3870-ba0e-39e6e238e184` 已为版本 `2`、`ACTIVE`，使用独立 canonical DAG `dts_plan_69949db3d42d3870ba0e39e6e238e184`；desired 与 deployed checksum 都是 `61653755d6252cb25950223540fdb2d23a4b61d63fbcd3065007144d6def82d2`。
- 新 dispatch `515966d1-4a91-33d1-90a1-a18a9b967e56` 为 `COMPLETED`，Airflow run `dts_plan_69949db3d42d3870ba0e39e6e238e184_5545299eb2c84e2cb88d`，bundle checksum `714fc3e4589b68e069eca2e1d3b346403621941475adc2fb25d86568041195e6`。
- Airflow 四个任务 `prepare_runtime`、`dbt_build`、`sync_manifest_and_probe`、`finalize_run` 均为 `success`。pipeline `ce84126d-c67e-370e-b8b6-dd69ddbe2380` 为 `SUCCEEDED`；`public.dwd_s104_detail_retry` 关系存在并已核验。
- 旧 dispatch `d727145f-2b91-3fb7-882e-37af67443cbc` 保留为 `FAILED / MODEL_OPERATIONAL_AIRFLOW_RUN_FAILED`（binding v1、旧 release-build DAG）；本轮没有重置或删除历史失败证据。

W4 治理编辑 GET DTO `version` 与 legacy 单模型按模型聚合读取的修复已随 296 包完成对应浏览器分项复测。随后发现的目录 `canMaintain` contract 能力和 `servingRef` 掩盖 `SYNC_FAILED` 属于 c275 修复范围；其正式构建、部署和浏览器正常重试仍待完成。Sprint 保持 `IN_PROGRESS`。

## 296 正式包、浏览器复测与遗留分析失败（2026-09-07）

- 正式包源码为 `296605b39f98816dd4992ff2cd89777e9c6b9e9e`，包 `dts-opmanager-upgrade-20260907-082929.tar.gz`，SHA-256 `1813907a401984664a9a4d64db1d102fe0ad0d846f368823c4905f8a41bf4eb3`。`archive.sha256` 与 `validation-summary.json` 位于 `/opt/prod/s10/deploy/data/sprint104-release/s104-296605b39f98/`。
- `validation-summary.json` 记录三项 image tar 与 amd64/source revision 一致；其包内定向结果为 catalog governance 5/5、ModelReleaseDialog 45 通过/5 跳过/0 失败、typecheck PASS。它不覆盖后续 Chrome FAIL 项，也不等于完整交付验收。
- 发布流程仅替换 `dts-analytics`、`dts-platform`、`dts-platform-webapp` 三服务；`deployed-containers.json` 记录其余 19 个容器未改变。未采用容器补丁或开发目录挂载。
- authenticated Chrome 复测结果以 `browser-catalog-and-dialog-retest.json` 为准：W4 治理读取、描述保存、陈旧窗口冲突拒绝且保留输入、目录读取已保存描述、单模型历史候选范围与 dev/test 执行隔离均为 PASS。目录维护按钮因 contract 尚未返回 `canMaintain` 为 FAIL；analysis 因物理 `servingRef` 被误视为成功为 FAIL。浏览器证据明确 `allSprintTasksAccepted=false`。
- `browser-analysis-failure.json` 固定了旧失败的真实投影：模型 r2/test 候选的 revision/checksum/implementation pins 一致，但 serving projection version 3 为 `SYNC_FAILED`，attempts=1，错误 `ANALYTICS_SEMANTIC_PUBLISH_HTTP_400`，`nextSyncAt=null`。2026-09-06 23:04:01 的 Analytics 400 原因为当时未注册 platform data source `a0000000-0000-0000-0000-000000000001`；400 被归为永久失败，旧记录不会自动重试。当前 Analytics 已有 default tenant 的该数据源映射（database id 1、postgresql）；新版部署后仍须通过页面正常重试复验，不能以映射存在或旧 `servingRef` 宣称分析成功。
- c275 后端定向正式测试 `/tmp/s104-status-permission-c275e244b.log` 为 35/35 通过、Maven `BUILD SUCCESS`，覆盖 `ModelDeliveryStatusQueryServiceTest` 25、`CatalogAssetPortalResourceTest` 6、`CatalogAssetPortalUnifiedResourceTest` 1、`CatalogDatasetGovernanceSummaryTest` 3。前端 `/tmp/s104-catalog-panel-c275e244b.log` 为 6/6 通过。c275 正式构建和包校验已完成（日志 `/tmp/s104-release-c275e244b.log`、`/tmp/s104-package-c275e244b.log`），包为 `s104-c275e244bfce/dts-opmanager-upgrade-20260907-084954.tar.gz`；为合并后续向导修复，未部署该包，也未写为浏览器重试或业务成功。

Sprint 保持 **IN_PROGRESS**：未执行的 Chrome 95、离线完整安装、IT-08 至 IT-18 完整正反分支和分析重试仍不得标为通过。
