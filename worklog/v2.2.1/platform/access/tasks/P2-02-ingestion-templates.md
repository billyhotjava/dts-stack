# P2-02 模板化接入能力

`status`: `done`
`priority`: `P2`

## 目标

提升新手接入效率，降低配置错误率。

## 范围

- 前端：数据源管理 + 入湖任务创建页
- 后端：模板参数渲染与校验接口

## 子任务

1. 提供标准模板：ERP/CRM/Excel 批量入湖。
2. 模板支持参数化（前缀、schema、增量列、调度）。
3. 预置校验规则与风险提示。
4. 生成后允许一键编辑再保存。

## 验收标准

- 通过模板可在 3 分钟内完成一个可执行任务。
- 模板生成任务可直接执行成功（基线环境）。
- 模板变更有版本与兼容策略。

## 风险与回滚

- 风险：模板覆盖不足导致误导。
- 回滚：模板仅作为向导，不限制手工高级配置。

## 实现进展（2026-02-15）

- 新增模板清单接口：
  - `GET /api/ingestion/templates`
  - 文件：`source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java`
  - 当前内置模板：`erp_jdbc_full`、`crm_jdbc_incremental`、`excel_batch_full`。
- 前端接入“快速模板”：
  - 文件：`source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`
  - 第一步新增模板选择与一键应用；
  - 应用后自动填充 `syncMode/syncPrefix/sourceSystem/schedule` 等默认参数。
- 新增模板渲染与预检接口：
  - `POST /api/ingestion/templates/{templateId}/render`
  - 文件：`source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java`
  - 支持按表单上下文参数渲染模板默认值、返回风险提示与必填缺失项（例如增量列、调度参数）。
- 前端模板应用改为“先渲染后落表单”：
  - 文件：`source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`
  - 应用模板时会传入当前表单参数（含 `sourceDataSourceId`、`fileName`）做预检；
  - 对缺失必填项给出即时提示，但仍允许进入手工补齐流程，满足“先生成后编辑”。
- API 封装补齐：
  - 文件：`source/dts-platform-webapp/src/api/ingestion.ts`
  - 新增 `IngestionTaskTemplateDTO`、`IngestionTemplateRenderDTO`、`getTaskTemplates()`、`renderTaskTemplate()`。
