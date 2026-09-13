# Sprint-60 后端 TDD 证据（当前阶段）

日期：2026-07-14

## RED → GREEN 命令

| 阶段 | 命令 | 结果 |
|---|---|---|
| F4-T01 RED | `./mvnw -ntp -Dtest=ModelingVNextLiquibaseTest ... test` | 2 个断言失败：master include/changelog 不存在 |
| F4-T01 GREEN | 同一命令 | 2 tests passed |
| F4-T02 RED | `./mvnw -ntp -Dtest=ModelingDomainValidatorTest ... test` | 编译失败：validator 尚不存在 |
| F4-T02 GREEN | 同一命令 | 5 tests passed |
| F5-T01 GREEN | `./mvnw -ntp -Dtest=ModelingDbtCompilerTest ... test` | 3 tests passed |
| F5-T03 GREEN | `./mvnw -ntp -Dtest=ModelingDriftGateTest ... test` | 3 tests passed |
| F6-T01 GREEN | `./mvnw -ntp -Dtest=ModelingRunStateMachineTest ... test` | 2 tests passed |
| F5-T02 GREEN | `./mvnw -ntp -Dtest=ModelingDbtManifestImporterTest ... test` | 3 tests passed |
| F6-T02 GREEN | `./mvnw -ntp -Dtest=ModelingRunRequestContractTest ... test` | 3 tests passed |
| F6-T03 GREEN | `./mvnw -ntp -Dtest=ModelingAirflowSubmissionGateTest ... test` | 2 tests passed |
| F3 REST RED | `./mvnw -ntp -Dtest=ModelingVNextResourceTest ... test` | 编译失败：REST resource/service 尚不存在 |
| F3 REST GREEN | `./mvnw -ntp -Dtest=ModelingVNextResourceTest ... test` | 2 tests passed |
| F4/F5/F6 PostgreSQL RED | `./mvnw -ntp -Dtest=ModelingVNextApplicationServiceIT ... test` | 先因语义 ID 不能落 UUID 失败，再因对象关联规范失败 |
| F4/F5/F6 PostgreSQL GREEN | 同一命令（Testcontainers PostgreSQL 17.4） | 1 test passed；对象、ModelSpec、dbt 制品、旧 manifest、运行队列均落库，并验证跨租户不能读取制品 |
| F6 runtime RED | `./mvnw -ntp -Dtest=ModelingRuntimeSubmissionServiceTest ... test` | 编译失败：运行时协调器尚不存在 |
| F6 runtime GREEN | 同一命令 | 5 tests passed；编译门禁、Addax→Airflow 顺序、关闭开关及“关闭开关不绕过编译/批次校验”均有断言 |
| F5 drift RED | `ModelingVNextApplicationServiceIT` | 手工篡改 SQL checksum 后仍返回 CLEAN |
| F5 drift GREEN | 同一 Testcontainers 命令 | 1 test passed；未篡改返回 CLEAN，篡改后返回 DRIFTED，并保留 SOURCE 血缘 |
| F3/F4 权限契约 GREEN | `./mvnw -ntp -Dtest=ModelingVNextResourceContractTest ... test` | 2 tests passed；写接口要求 CATALOG_MAINTAINERS，读台账保持可消费 |
| F6 回调契约 GREEN | `./mvnw -ntp -Dtest=ModelingRunCallbackContractTest,ModelingRunStateMachineTest ... test` | 4 tests passed；回调携带 Addax/Airflow/dbt 外部 ID，同状态幂等，终态不被迟到失败覆盖 |
| F4 审计与发布门禁 GREEN | `./mvnw -ntp -Dtest=ModelingVNextResourceTest,ModelingVNextResourceContractTest ... test` | 6 tests passed；写操作和运行回调写审计，发布门禁返回 `RELEASE_READY/BLOCKED` |
| F3/F4/F5/F6 集成 GREEN | `./mvnw -ntp -Dtest=ModelingVNextApplicationServiceIT ... test` | 1 test passed（Testcontainers PostgreSQL 17.4）；编译、运行、回调、发布门禁及 checksum drift 均有断言 |
| F3 旧接口兼容 GREEN | `./mvnw -ntp -Dtest=ModelingCompatibilityPolicyTest ... test` | 2 tests passed；旧 `/api/semantic/models` 与 `/api/semantic/business-objects` 读取路由保持存在 |

## 说明

- 当前证据覆盖 XML/source-contract、领域规则、dbt 编译器/manifest 导入、漂移发布门禁、运行上下文和 Airflow 投递门禁。
- PostgreSQL migration、JdbcTemplate 持久化服务和幂等写回已经由 Testcontainers 集成测试覆盖；真实生产数据库迁移仍需部署环境验收。
- Addax/Airflow/dbt 的真实外部编排适配器已经接入，默认由 `DTS_MODELING_RUNTIME_ENABLED=false` 安全关闭；开启后通过既有 ingestion/Airflow client 投递，CI 仍不启动外部服务。
- SQL 制品使用“模型 ID + 文件名”的稳定 dbt unique id，避免 SQL/schema/tests/docs 在同一唯一键上互相覆盖。
- 发布门禁读取持久化制品和 drift 证据：缺少业务对象、SCHEMA/TEST 制品或 checksum 漂移会返回稳定 blocker code；正式审批回写和真实生产迁移仍需环境验收。

## 聚焦验收

`./mvnw -ntp -Dtest=ModelingVNextLiquibaseTest,ModelingDomainValidatorTest,ModelingDbtCompilerTest,ModelingDbtManifestImporterTest,ModelingDriftGateTest,ModelingRunStateMachineTest,ModelingRunRequestContractTest,ModelingAirflowSubmissionGateTest,ModelingRuntimeSubmissionServiceTest,ModelingVNextResourceTest,ModelingVNextApplicationServiceIT ... test`：原聚焦批次 29 tests passed；本轮新增回调/资源/兼容测试分别通过（4、6、2 tests），Testcontainers IT 1 test passed。

本轮 Sprint-60 新增测试集合（20 个 test class）重跑结果：**48 tests passed，0 failures，0 errors**。之前全量 modeling 包含的 `ModelingSqlModelServiceTest` 仍有 2 failures/5 errors，属于既有 SQL 模型归档测试的 fixture/权限问题，不计入本 Sprint-60 新增代码门禁。
