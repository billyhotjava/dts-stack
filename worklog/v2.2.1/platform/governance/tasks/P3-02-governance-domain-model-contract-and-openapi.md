# P3-02 治理领域模型契约与 OpenAPI 固化

`status`: `planned`
`priority`: `P3`

## 目标

固化治理 API DTO 契约与 OpenAPI，降低前后端漂移。

## 范围

治理 REST 资源与 DTO、OpenAPI 文档生成流程。

## 子任务

1. 清理 `any`/动态字段，补充类型化 DTO。
2. 导出治理 API OpenAPI 文档并纳入版本管理。
3. 增加契约变更检查（breaking change guard）。

## 验收标准

- 治理 API 核心对象具备稳定 schema。
- 前端调用不再依赖隐式字段。
- 契约破坏性变更可在 CI 发现。

## 风险与回滚

- 风险：历史接口兼容性压力。
- 回滚：保留兼容字段并标记 deprecated。
