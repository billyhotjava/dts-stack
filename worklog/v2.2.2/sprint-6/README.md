# Sprint-6：ELT 稳定性治理

## 目标

围绕“数据接入中心 + 数据开发中心”建立一轮系统级稳定性治理 Sprint，交付：

- 系统诊断
- 质量基线
- 首批修复

本 Sprint 的重点不是一次性清空所有历史问题，而是把 ELT 主链路、异常链路、测试门禁和首批高优先级问题一起收口。

## 范围

### 纳入

- 数据接入中心
  - 任务创建
  - 任务编辑
  - 异步执行
  - 执行历史
  - 日志回显
  - 重试 / 重建 DAG / 删除等边缘入口
- 数据开发中心
  - SQL 建模
  - dbt compile / test / build
  - Airflow 触发与状态同步
  - 发布门禁 / 重建 / 回滚相关链路
- 相关模块
  - `source/dts-ingestion`
  - `source/dts-platform`
  - `source/dts-platform-webapp`
  - `tests/web-e2e`

### 不纳入

- 不做整个平台全面重构
- 不优先处理分析看板等非 ELT 页面
- 不做部署体系改造，除非它直接阻断 ELT 稳定性

## 本 Sprint 输出

- 需求材料：`req/`
- 稳定性治理设计：`design.md`
- 可执行计划：`plan.md`
- 任务拆分：`tasks/`

## 当前结论摘要

当前系统已具备主路径可用性，但一旦进入失败、超时、重试、部分成功等异常路径，问题主要集中在：

1. 异步编排不稳定
2. 错误传播失真
3. 状态反馈和日志回显不可靠
4. 前后端/执行层契约不一致
5. 测试基线无法覆盖真实脆弱点

## 当前诊断进展

第一批诊断已经完成到“可复现失败面”：

- 接入中心：
  - 主链已确认是 `Transform* 页面 -> ingestion.ts -> IngestionTaskResource -> IngestionTaskService -> AirflowAdapter/AirflowClient -> AirflowExecutionSyncService`
  - 异步入口 `executeAsync/retryExecutionAsync` 之前只做“提交后台”，没有复用同步执行的前置校验，错误请求也会直接返回 `submitted`
  - 该问题已完成首批修复：异步执行/异步重试现在会在提交后台前先做同步校验，运行中任务、缺失 execution、非法 retry mode 会被前置拦截
  - `dts-ingestion` 资源层错误契约已确认是 `HTTP 200 + body.status`，相关 WebMvc 测试已对齐
- 开发中心：
  - 主链已确认是 `SqlModelingPage -> EtlResource -> DbtDagService/AirflowClient -> Airflow DAG -> dbt -> DbtRunResultService`
  - `waitForDagRegistration()` 仍然会把真实 Airflow 异常收敛成 “DAG 尚未注册”
  - 发布门禁、质量门禁、产出表重建相关测试已和当前实现语义漂移

## 第一批修复入口

当前最先要修的不是页面样式，而是下面两类基础问题：

1. 恢复后端最小回归面
2. 统一异步触发、状态同步和错误传播语义

当前已完成的后端修复与验证：

- `dts-ingestion`
  - `IngestionTaskService.validateAsyncExecutionRequest`
  - `IngestionTaskService.validateAsyncRetryRequest`
  - `IngestionTaskResource.executeTaskAsync/retryExecutionAsync` 前置校验接入
  - `IngestionTaskResource.executeTaskAsync/retryExecutionAsync` 对异步线程池拒绝提交统一返回 `503 / 后台执行队列繁忙，请稍后重试`
  - 已确认 `dts-platform-webapp` 的 `apiClient` 会按响应体 `status` 处理 `dts-ingestion` 错误契约，前端不会把 `body.status=409/404/503` 误判成成功
  - 焦点回归：
    ```bash
    cd source/dts-ingestion
    mvn -Dtest=IngestionTaskServiceTest,IngestionTaskResourceTest,IngestionTaskExecutionFilterTest,AirflowExecutionSyncServiceTest test
    ```
  - 结果：`Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`

## 当前自动化补强

在恢复 `ELT-004/005` 后，`ELT-006` 已开始补第一批前端冒烟基线：

- 接入中心：
  - `任务列表 -> 执行 -> 详情 -> 最新日志`
- 开发中心：
  - `逻辑建模 -> compile -> test -> build -> 运行记录`

当前已完成并验证：

- `elt-ingestion-center-smoke.spec.ts`
- `elt-development-center-smoke.spec.ts`

联跑命令基于本地 `.env` 和本地 Vite dev server：

```bash
set -a; source .env; set +a
DTS_WEB_E2E_USE_TEST_SERVER=0 \
DTS_PLATFORM_URL=http://127.0.0.1:19349/expert/ \
DTS_BASE_URL=http://127.0.0.1:19349/expert/ \
DTS_EXPERT_URL=http://127.0.0.1:19349/expert/ \
DTS_PLATFORM_AUTH_TOKEN=platform-playwright-token \
DTS_ADMIN_AUTH_TOKEN=admin-playwright-token \
DTS_PLATFORM_REQUIRE_PASSWORD_LOGIN=0 \
DTS_ADMIN_REQUIRE_PASSWORD_LOGIN=0 \
tests/web-e2e/node_modules/.bin/playwright test \
  tests/web-e2e/specs/biz/elt-ingestion-center-smoke.spec.ts \
  tests/web-e2e/specs/biz/elt-development-center-smoke.spec.ts \
  --project=chromium \
  --config=tests/web-e2e/playwright.config.ts
```

结果：

- `2 passed (10.8s)`

对应材料见：

- [req/elt-manual-failure-runbook.md](./req/elt-manual-failure-runbook.md)
- [tasks/ELT-006-e2e-smoke-and-manual-failure-runbook.md](./tasks/ELT-006-e2e-smoke-and-manual-failure-runbook.md)

## 文档

- [design.md](./design.md)
- [plan.md](./plan.md)
- [req/README.md](./req/README.md)
- [tasks/README.md](./tasks/README.md)
