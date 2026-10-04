# P2-01 连接器能力契约统一

`status`: `done`
`priority`: `P2`

## 目标

统一 FULL/INCREMENTAL/CDC/BACKFILL 能力契约，前后端同一语义。

## 范围

- 前端能力展示：`TransformCreatePage.tsx`
- 后端能力接口：`/api/ingestion/connectors/capabilities`

## 子任务

1. 定义能力契约版本（字段、约束、兼容规则）。
2. 前端严格按能力契约控制可选项。
3. 提交前后端双重校验，拒绝非法模式组合。
4. 增加能力探测失败的降级策略。

## 验收标准

- UI 可选项与后端可执行能力完全一致。
- 能力变更后无需改前端硬编码即可生效。
- 非法组合在前后端均被拦截。

## 风险与回滚

- 风险：历史连接器能力声明不完整。
- 回滚：提供默认能力映射与兼容层。

## 实现进展（2026-02-15）

- 后端能力契约增强：
  - `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/ConnectorCapabilityService.java`
  - 新增能力契约字段（`contractVersion`、`syncModes`、`fallbackSyncMode`）默认种子；
  - 新增模式归一化、支持模式解析、降级兜底与服务端校验（`validateSyncModeOrThrow`）。
- 入湖任务创建/更新增加双重校验：
  - `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java`
  - 基于连接器类型（file/addax/airbyte）校验 `syncMode` 是否受支持，不合法直接 `400`。
- 前端按契约驱动同步模式：
  - `source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`
  - `syncMode` 选项由能力契约动态生成；
  - 当能力探测失败时启用保守降级（file=full、addax=full+incremental、airbyte=full+incremental+cdc+backfill）；
  - 当前模式不受支持时自动切换到 `fallbackSyncMode` 并提示。
