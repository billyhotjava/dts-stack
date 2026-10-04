# F2: API 数据源管理与预检

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: F1

## 目标

在平台侧提供 API 数据源的创建、编辑、测试连接、鉴权 dry-run、请求模板校验和安全 preview 能力，为后续任务创建提供可靠输入。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | API 数据源 CRUD 与后端校验 | P0 | IN_PROGRESS |
| T02 | 鉴权 dry-run 与连接测试 | P0 | DRAFT |
| T03 | 请求模板校验与安全 preview | P0 | DRAFT |
| T04 | Secret 写入、脱敏回显与轮换入口 | P0 | DRAFT（等待 F1/T05 secret 表落地） |
| T05 | Mock API fixtures 与契约测试 | P1 | DRAFT |

> 状态语义：见 F1/README。

## 完成标准

- [ ] API 数据源能独立于任务创建。
- [ ] 连接测试不产生 ODS 数据。
- [ ] preview 有响应大小、超时和敏感字段保护。
- [ ] 数据源配置可被任务向导复用。

## 实现进展

- 2026-04-25: platform 侧已新增 API 数据源基础校验，支持 `baseUrl`、secret 明文拦截和 API props 默认字段归一化。
- 2026-04-25: 已跳过 API 数据源的 JDBC/file catalog sync，等待后续 preview/schema mapping 流程接管。
- 2026-04-25: 已扩展 `infra_data_source.props` 到 `text`，避免复杂 API 配置被 2048 字符限制卡住。
