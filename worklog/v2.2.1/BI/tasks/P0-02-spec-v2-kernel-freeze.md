# P0-02 统一内核协议 Spec v2 冻结

`status`: `done`  
`priority`: `P0`  
`inspiration`: `Metabase(Model/Card资产化) + Superset(查询/可视化解耦)`

## 目标

冻结统一协议，避免继续“边开发边改结构”造成返工。

## 子任务

1. 协议定义
- 固化 `QuerySpec/VizSpec/ScreenSpec/InteractionSpec` Type。
- 增加 `schemaVersion`。

2. Schema 校验
- 为核心协议补 JSON Schema 与运行时校验器。
- 加入不兼容字段告警机制。

3. 存储与接口
- `screen` 保存接口只接收 `ScreenSpec`。
- 后端返回统一错误模型：`code/message/requestId/retryable`。

4. 兼容层
- 旧结构通过 `migrateLegacySpec()` 一次性升级到 v2。

## 验收标准

- 新建、保存、加载、预览全部走 `ScreenSpec v2`。
- 任意非法配置提交可被拦截并返回可追踪错误。

## 风险与回滚

- 风险：历史数据迁移失败。  
- 回滚：保留 `legacy parser` 只读能力，不允许继续写入 legacy 结构。

## 实现记录（2026-02-14）

- 已完成 `schemaVersion` 接入与 `ScreenSpec v2` 归一化入口（加载/预览/公开页/模板导入/AI草稿创建）。
- 已完成统一 payload 构建（保存/创建走 `buildScreenPayload`）。
- 已完成旧结构兼容升级主链路（`normalizeScreenConfig` + warnings）。
- 已完成后端写入校验器：`ScreenSpecValidator`
  - 校验 schemaVersion/画布尺寸/components/globalVariables/dataSource 关键字段；
  - 非法 payload 统一抛出 `SCREEN_SPEC_INVALID`。
- 已完成统一错误模型收敛：
  - 新增 `ScreenSpecValidationException`；
  - `GlobalExceptionHandler` 增加 `ScreenSpecValidationException/IllegalArgumentException` 映射，统一返回 `ApiError(code,retryable,requestId)`。
- 已完成前端保存前校验：
  - `validateScreenPayload` 覆盖组件类型、尺寸、变量格式、重复键校验；
  - 保存与 JSON 导入前先校验，减少无效请求与脏数据入库。
