# F1: API 接入契约与鉴权扩展点

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: 无

## 目标

定义 API 数据接入的产品级契约，明确 `api/http` 作为一等 source type 的配置模型、鉴权扩展点、能力声明、运行时策略入口和存储边界。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | API Source Contract v1 | P0 | DRAFT |
| T02 | Auth Provider SPI 与密钥引用契约 | P0 | DRAFT |
| T03 | Connector Capability 扩展 `api/http` | P0 | DRAFT |
| T04 | SourceConnector 与 ExecutionPlan 抽象 | P0 | DRAFT |
| T05 | API 配置存储与迁移设计 | P0 | DRAFT |

## 完成标准

- [ ] `api/http` 的业务 API 契约独立于 Addax 参数。
- [ ] 鉴权 provider 可由后端 metadata 驱动前端表单。
- [ ] 密钥字段只有引用和 masked display，不出现明文回显。
- [ ] 后续 runtime 能在 Addax、Airbyte、自研 runner 之间切换。

## 实现进展

- 2026-04-25: 已新增 `ApiSourceContracts`、`ApiAuthProviderRegistry`、`ApiConnectorContractResource`。
- 2026-04-25: 已新增 `api` connector capability 默认种子和 `api/http/rest/httpreader` 类型归一化。
- 2026-04-25: 已新增 `SourceConnector` / `ExecutionPlan` 抽象和 `ApiHttpSourceConnector` 占位实现。
- 2026-04-25: 已通过 ingestion 侧 API contract/Auth Provider 定向单测。
