# P3-01 GitOps 与 CI 门禁

- 优先级：P3
- 状态：done

## 范围

- 建立开发中心代码化流程的 CI 门禁能力。

## 子任务

- 接入 Git 分支策略（开发/发布）。
- PR 触发 dbt compile/test，失败阻断合并。
- 发布动作必须绑定一次成功构建记录。

## 验收标准

- 无通过构建记录不可发布。
- 可追踪每次发布对应 commit 与构建产物。

## 风险与回滚

- 风险：离线环境 CI 资源有限。
- 回滚：先本地 runner + 夜间批量校验。

## 已完成进展（2026-02-17）

- 新增发布门禁服务（Release Gate）：
  - 接口：`POST /api/etl/dbt/release-gate/check`
  - 校验项：
    - Git 分支策略（main/master/release/*/hotfix/*）
    - Commit SHA 合法性（7-40 位十六进制）
    - 最近一次 dbt 构建记录必须成功，且命令属于 compile/test/build
    - 构建记录有效期默认 24h
  - 返回：`PASS/WARN/BLOCK`、阻断/告警列表、构建证据（invocationId/command/status/generatedAt/runResultsPath）
- 发布动作追踪增强：
  - `dbt run` 请求新增字段：`gitRef`、`commitSha`、`buildInvocationId`
  - 触发 Airflow 时写入 `conf`，用于后续审计追踪“发布对应代码版本 + 构建记录”
- 运行日志保留追踪上下文：
  - `ExternalRunLogService.syncAirflowRuns` 同步状态时保留已有 `conf`，避免覆盖丢失 `gitRef/commitSha/buildInvocationId`
- 前端“提交上线”流程接入门禁：
  - 在质量门禁后新增 GitOps 门禁校验
  - 阻断项直接禁止提交；告警项二次确认
  - 提交弹窗补充输入：Git 分支、Commit SHA、严格门禁开关（默认开启）

## 影响文件

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtReleaseGateService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/ops/ExternalRunLogService.java`
- `source/dts-platform-webapp/src/api/platformApi.ts`
- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`

## 回归结果（2026-02-17）

- `mvn -f source/dts-platform/pom.xml -DskipTests compile`：通过
- `pnpm -C source/dts-platform-webapp build`：通过

## 备注

- PR 自动触发 compile/test 属于 CI 编排层能力，本次在平台侧先实现“上线前强门禁 + 构建证据绑定”，确保离线环境下也可执行与审计。
