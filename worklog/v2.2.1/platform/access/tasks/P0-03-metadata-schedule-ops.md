# P0-03 元数据采集调度运维能力

`status`: `in-progress`
`priority`: `P0`

## 目标

把元数据采集从“能触发”提升为“可运维”（可启停、可调度、可定位失败）。

## 范围

- 前端：`source/dts-platform-webapp/src/pages/catalog/MetadataPage.tsx`
- 后端：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/CatalogSyncResource.java`

## 子任务

1. 新增采集任务启停操作。
2. 新增调度策略编辑（cron）。
3. 展示最近失败详情（错误摘要 + 日志片段）。
4. 增加按数据源过滤与快速刷新。

## 验收标准

- 可在页面启停采集任务并即时生效。
- 可编辑调度策略并成功持久化。
- 失败时可直接看到错误摘要。

## 风险与回滚

- 风险：调度变更影响线上自动采集节奏。
- 回滚：调度改动写审计并支持回滚到上一个版本。

## 实现进展（2026-02-14）

- 已新增采集配置接口：
  - `GET /api/catalog/sync/config`
  - `POST /api/catalog/sync/config`
- 已在元数据采集页增加“自动采集开关”与当前 Cron 展示。
- 已接入开关操作的前端调用与成功/失败提示。
- 已增加 Cron 在线编辑入口（前端输入 + 保存按钮）。
- 已增加前后端 Cron 基础校验（5-7 段），非法格式直接阻断。
- 当前限制：Cron 在线修改仅更新展示值，完整生效仍需重启服务（由后端接口 message 明确提示）。
