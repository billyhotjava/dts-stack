# T04: API连接测试端点

**优先级**: P1
**状态**: DONE
**依赖**: T02

## 目标

数据源登记/任务配置时可一键测试：用解密凭据向客户 API 发探活请求，返回连通性、鉴权有效性、采样记录数与错误分类。

## 技术设计

- 端点 `POST /api/ingestion/api/test-connection`：入参支持 `dataSourceId`（已保存数据源，解密 secrets）或 `sourceConfig + secrets`（保存前表单探活），可选 `resource` 配置；复用 F2 的 HTTP 引擎（限 1 页、限 maxResponseBytes、短超时）。
- 返回：`{connected, httpStatus, authOk, sampleCount, recordPathResolved, sampleRecords, failureCategory, advice}`，错误分类对齐 `ExecutionFailureClassifier` 的 API_* 类别；`sampleRecords` 最多返回前 5 条用于前端预览。
- 权限：复用 `INFRA_MAINTAINER_EXPRESSION`（同 ApiConnectorContractResource）。

## 2026-06-12 进展

- 已在 ingestion 侧新增 `POST /api/ingestion/api/test-connection`，通过 `IngestionSourceResolver.resolveApiInfo(dataSourceId)` 读取解密后的 API 数据源配置，构造 1 页 `api-http` 探活计划并复用 `ApiHttpEngine`。
- 已返回统一摘要：`connected/httpStatus/authOk/sampleCount/recordPathResolved/sampleRecords/failureCategory/advice/elapsedMs`；`ApiHttpException` 不抛 500，统一转为错误分类和建议。
- 已在 platform 侧补代理：`POST /api/ingestion/api/test-connection` -> ingestion 同名端点，供前端通过现有 platform API 网关调用。
- 已在 F4-T01 接入已保存 API 数据源的编辑态测试按钮和结果展示。
- 已为 F4-T02 创建页预览补 `sampleRecords` 返回；前端可用当前 resource 探活并展示首页样本。
- 已支持保存前 raw config+secrets 探活：`ApiConnectionTestRequest.sourceConfig/secrets` 直接构造一次性探活计划，不调用 `resolveApiInfo`；`DataSourceFormModal` 新建态可用“测试当前配置”按钮。
- 补充服务级 mock API IT：本地 `HttpServer` + 真实 `ApiHttpEngine` 验证正确 bearer token 返回 `connected=true/sampleCount=1`，HTTP 401 返回 `authOk=false/PERMISSION_ERROR/API_RUNTIME_AUTH`；证据见 `it/evidence/api-test-connection-mock-api-20260612.txt`。

## 影响范围

- 新增 REST 端点（`ApiConnectorContractResource` 或独立 resource）
- 依赖 F2-T02 HTTP 引擎（可先以最小内核联调）

## 验证

- [x] 单测：正确凭据路径返回 `connected=true`、`sampleCount`、`recordPathResolved`、`sampleRecords`
- [x] 单测：鉴权失败返回 `authOk=false` 且 `failureCategory=PERMISSION_ERROR`，不抛 500
- [x] 单测：保存前 `sourceConfig+secrets` 探活构造计划，不依赖已保存 `dataSourceId`
- [x] 单测：不可达/网络错误返回 `CONNECTION_ERROR`，不抛 500
- [x] platform 代理单测：`/api/ingestion/api/test-connection` 正确转发到 ingestion client
- [x] 前端构建通过：`pnpm build`
- [x] 验证命令：`./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=ApiConnectorContractResourceTest test`
- [x] 验证命令：`./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-platform -Dtest=IngestionServiceClientTest,IngestionTaskProxyResourceTest test`
- [x] mock API IT：正确凭据返回 connected+sample；错误凭据返回 authOk=false 且分类 AUTH

## 完成标准

- [x] 前端 F4-T01 数据源表单可调用并展示结果
