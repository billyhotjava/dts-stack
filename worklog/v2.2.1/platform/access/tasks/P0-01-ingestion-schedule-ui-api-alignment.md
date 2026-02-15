# P0-01 入湖任务调度配置 UI/API 对齐

`status`: `in-progress`
`priority`: `P0`

## 目标

补齐入湖任务的调度配置入口，让 `syncSchedule` 从“后端可用”变成“前端可配”。

## 范围

- 前端：`source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`
- 接口：`source/dts-platform-webapp/src/api/ingestion.ts`
- 后端校验：`source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java`

## 子任务

1. 在任务创建/编辑页新增调度配置组件（`manual/interval/cron`）。
2. 表单值写入 `syncSchedule` 与 `sync.schedule` 结构。
3. 编辑态回显已有调度配置。
4. 增加调度表达式合法性校验与错误提示。

## 验收标准

- 新建任务可配置 cron/间隔并成功保存。
- 编辑任务时调度配置可准确回显。
- 非法 cron 提交被阻断并返回可读错误。

## 风险与回滚

- 风险：旧任务无调度字段导致回显异常。
- 回滚：前端对空值默认 `manual`，后端保持向后兼容。

## 实现进展（2026-02-14）

- 已在 `TransformCreatePage` 增加调度策略字段：`manual/interval/cron`。
- 已打通创建、草稿、编辑三条链路的 `sync.schedule` 与 `syncSchedule` 回写。
- 已完成历史 `syncSchedule` 到表单字段的回显解析（`cron:*`、`interval:*`）。
- 前端构建通过：`pnpm -C source/dts-platform-webapp build`。
