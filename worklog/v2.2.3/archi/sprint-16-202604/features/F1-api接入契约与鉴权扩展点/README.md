# F1: API 接入契约与鉴权扩展点

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: 无

## 目标

定义 API 数据接入的产品级契约，明确 `api/http` 作为一等 source type 的配置模型、鉴权扩展点、能力声明、运行时策略入口和存储边界。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | API Source Contract v1 | P0 | PARTIAL |
| T02 | Auth Provider SPI 与密钥引用契约 | P0 | PARTIAL |
| T03 | Connector Capability 扩展 `api/http` | P0 | IN_PROGRESS |
| T04 | SourceConnector 与 ExecutionPlan 抽象 | P0 | PARTIAL |
| T05 | API 配置存储与迁移设计 | P0 | PARTIAL |

> 状态语义：`DRAFT`=未动工；`PARTIAL`=核心 scope 部分落地、仍有未完成完成标准；`IN_PROGRESS`=主链路已落、剩验收/边角；`DONE`=完成标准全部勾选。

## 完成标准

- [ ] `api/http` 的业务 API 契约独立于 Addax 参数。
- [ ] 鉴权 provider 可由后端 metadata 驱动前端表单。
- [ ] 密钥字段只有引用和 masked display，不出现明文回显。
- [ ] 后续 runtime 能在 Addax HTTP reader 和自研 runner 之间切换，不依赖 Airbyte。

## 实现进展

- 2026-04-25: 已新增 `ApiSourceContracts`、`ApiAuthProviderRegistry`、`ApiConnectorContractResource`。
- 2026-04-25: 已新增 `api` connector capability 默认种子和 `api/http/rest/httpreader` 类型归一化。
- 2026-04-25: 已新增 `SourceConnector` / `ExecutionPlan` 抽象和 `ApiHttpSourceConnector` 占位实现。
- 2026-04-25: 已通过 ingestion 侧 API contract/Auth Provider 定向单测。
