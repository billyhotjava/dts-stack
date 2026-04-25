# T03: Connector Capability 扩展 `api/http`

**优先级**: P0
**状态**: DRAFT
**依赖**: T01

## 目标

把 API 接入纳入现有连接器能力契约，让前后端用同一份 capability 控制同步模式、schema drift、增量和运行时能力。

## 范围

- 新增 connector type：`api` 或 `http`，需要统一命名。
- 声明支持模式：`FULL_REFRESH`、`INCREMENTAL`。
- 预留能力：`PAGINATION`、`CURSOR_CHECKPOINT`、`AUTH_PROVIDER`、`PREVIEW_SCHEMA`、`RATE_LIMIT`。
- 服务端创建/更新任务时按 capability 校验非法组合。

## 完成标准

- [ ] 能力接口返回 API connector 的完整能力声明。
- [ ] 前端不硬编码 API 支持模式。
- [ ] 非法 sync mode 在前后端都能拦截。
- [ ] 能力降级策略明确且可测试。

