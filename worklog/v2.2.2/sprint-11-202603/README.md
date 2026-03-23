# Sprint-11: 数据入湖前后端优化

**时间**: 2026-03
**状态**: IN_PROGRESS
**目标**: 以“创建页状态收口 + 后端职责拆分 + 执行链/日志链统一”为主线，降低数据入湖模块的复杂度，提升可维护性、可测试性和异常场景稳定性。

## 背景

当前数据入湖主链已经可用，但前后端复杂度明显偏高：

- 前端创建页 [TransformCreatePage.tsx](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx) 超过 3000 行，同时承担初始化加载、模板应用、文件解析、草稿恢复、异步执行进度和表单提交流程。
- 后端资源层 [IngestionTaskResource.java](/opt/prod/s10/s10-stack/source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java) 与服务层 [IngestionTaskService.java](/opt/prod/s10/s10-stack/source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskService.java) 都超过 2000 行，CRUD、执行编排、日志回显、历史查询和 Airflow/Addax 协调逻辑高度耦合。
- 执行链和日志链的前后端契约已经能跑，但重复实现较多，后续继续叠加功能会放大维护成本。

因此本 sprint 不追求大而全重构，而是从最重热点切第一批优化切口，先把结构和回归基线收紧。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 创建页与表单状态优化 | 4 | IN_PROGRESS |
| F2 | 入湖任务后端服务拆分 | 3 | READY |
| F3 | 执行链与日志链优化 | 3 | READY |

## 当前诊断结论

- 前端最大热点：
  - [TransformCreatePage.tsx](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx)
  - [ingestionFormHelpers.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/ingestionFormHelpers.ts)
  - [ExecutionHistoryTable.tsx](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/components/ExecutionHistoryTable.tsx)
- 后端最大热点：
  - [IngestionTaskResource.java](/opt/prod/s10/s10-stack/source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java)
  - [IngestionTaskService.java](/opt/prod/s10/s10-stack/source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskService.java)
  - [AddaxJobService.java](/opt/prod/s10/s10-stack/source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/AddaxJobService.java)

## 本轮已实现

- 创建页的“任务提交后异步执行进度”已从 [TransformCreatePage.tsx](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx) 中抽出到独立 helper 与 hook：
  - [transformCreateAsyncRun.helpers.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/transformCreateAsyncRun.helpers.ts)
  - [useTransformAsyncRunProgress.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/useTransformAsyncRunProgress.ts)
- 创建页初始化加载链已收成统一 bootstrap helper：
  - [transformCreateBootstrap.helpers.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/transformCreateBootstrap.helpers.ts)
  - [transformCreateBootstrap.helpers.test.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/transformCreateBootstrap.helpers.test.ts)
- 草稿、编辑态文件流恢复与模板 `sourceCategory` 判定已收成状态 helper：
  - [transformCreateState.helpers.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/transformCreateState.helpers.ts)
  - [transformCreateState.helpers.test.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/transformCreateState.helpers.test.ts)
- 文件解析结果与建议表名推导已收成文件流 helper：
  - [transformCreateFileFlow.helpers.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/transformCreateFileFlow.helpers.ts)
  - [transformCreateFileFlow.helpers.test.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/transformCreateFileFlow.helpers.test.ts)
- 草稿保存请求体已收成独立 helper：
  - [transformCreateDraft.helpers.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/transformCreateDraft.helpers.ts)
  - [transformCreateDraft.helpers.test.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/transformCreateDraft.helpers.test.ts)
- 模板应用结果已收成独立 helper：
  - [transformCreateTemplate.helpers.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/transformCreateTemplate.helpers.ts)
  - [transformCreateTemplate.helpers.test.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/transformCreateTemplate.helpers.test.ts)
- 新增模块级回归：
  - [transformCreateAsyncRun.helpers.test.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/transformCreateAsyncRun.helpers.test.ts)
- Excel 文件流已支持“粘贴 ODS 字段列表”并按顺序映射 Excel 列：
  - [fileOdsPasteMapping.helpers.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/fileOdsPasteMapping.helpers.ts)
  - [fileOdsPasteMapping.helpers.test.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/fileOdsPasteMapping.helpers.test.ts)
  - [FileBasicStep.tsx](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/steps/FileBasicStep.tsx)

## 当前验证

- [x] `pnpm -C source/dts-platform-webapp exec tsx --test src/pages/explore/etl/transformCreateAsyncRun.helpers.test.ts`
- [x] `pnpm -C source/dts-platform-webapp exec tsx --test src/pages/explore/etl/fileOdsPasteMapping.helpers.test.ts src/pages/explore/etl/transformCreateAsyncRun.helpers.test.ts`
- [x] `pnpm -C source/dts-platform-webapp exec tsx --test src/pages/explore/etl/transformCreateBootstrap.helpers.test.ts src/pages/explore/etl/fileOdsPasteMapping.helpers.test.ts src/pages/explore/etl/transformCreateAsyncRun.helpers.test.ts`
- [x] `pnpm -C source/dts-platform-webapp exec tsx --test src/pages/explore/etl/transformCreateState.helpers.test.ts src/pages/explore/etl/transformCreateBootstrap.helpers.test.ts src/pages/explore/etl/fileOdsPasteMapping.helpers.test.ts src/pages/explore/etl/transformCreateAsyncRun.helpers.test.ts`
- [x] `pnpm -C source/dts-platform-webapp exec tsx --test src/pages/explore/etl/transformCreateFileFlow.helpers.test.ts src/pages/explore/etl/transformCreateState.helpers.test.ts src/pages/explore/etl/transformCreateBootstrap.helpers.test.ts src/pages/explore/etl/fileOdsPasteMapping.helpers.test.ts src/pages/explore/etl/transformCreateAsyncRun.helpers.test.ts`
- [x] `pnpm -C source/dts-platform-webapp exec tsx --test src/pages/explore/etl/transformCreateDraft.helpers.test.ts src/pages/explore/etl/transformCreateFileFlow.helpers.test.ts src/pages/explore/etl/transformCreateState.helpers.test.ts src/pages/explore/etl/transformCreateBootstrap.helpers.test.ts src/pages/explore/etl/fileOdsPasteMapping.helpers.test.ts src/pages/explore/etl/transformCreateAsyncRun.helpers.test.ts`
- [x] `pnpm -C source/dts-platform-webapp exec tsx --test src/pages/explore/etl/transformCreateTemplate.helpers.test.ts src/pages/explore/etl/transformCreateDraft.helpers.test.ts src/pages/explore/etl/transformCreateFileFlow.helpers.test.ts src/pages/explore/etl/transformCreateState.helpers.test.ts src/pages/explore/etl/transformCreateBootstrap.helpers.test.ts src/pages/explore/etl/fileOdsPasteMapping.helpers.test.ts src/pages/explore/etl/transformCreateAsyncRun.helpers.test.ts`
- [ ] `pnpm -C source/dts-platform-webapp build`
  当前被并发改动 [index.tsx](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/workbench/index.tsx) 中的未使用 `toast` 阻断，不是本次入湖优化引入

## 完成标准

- [ ] 创建页的初始化加载与异步执行进度不再散落在单页大组件内
- [ ] 入湖后端的查询职责与执行编排职责出现清晰边界
- [ ] 执行状态与日志回显链路具备更明确的契约和回归测试
- [ ] 至少完成一批可交付的结构优化与验证，不只是文档诊断
