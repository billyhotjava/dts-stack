# T01: 对象、规划与 ModelSpec CRUD 契约

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: F1-T01

## 目标

定义新版本建模主链的 REST 请求、响应、分页、状态和 revision 行为。

## 技术设计

接口：`GET/POST/PUT /api/modeling/vnext/business-objects`、`/plans`、`/model-specs`，以及 `GET /api/modeling/vnext/model-specs/{id}/dependencies`。

所有写操作带 `revision` 和 `idempotencyKey`；冲突返回 `MODEL_REVISION_CONFLICT`，不覆盖最新版本。

## 影响范围

- OpenAPI 文档或 controller 注释。
- `source/dts-platform-webapp/src/api/modelingApi.ts`
- Resource/DTO contract tests。

## 验证

- [x] 空粒度、非法层级、未知对象、跨过程关联的错误码已纳入共享契约。
- [x] 创建和更新请求统一携带 `revision` 与 `idempotencyKey`，重复请求规则已纳入共享契约。

## 完成标准

- [x] 前端类型由 API 契约反推，不能再维护一套不一致字段。
