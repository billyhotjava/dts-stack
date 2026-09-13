# T04: 测试矩阵、fixtures 与 E2E 门禁

**优先级**: P0
**状态**: DRAFT
**依赖**: F2/T05, F3, F4, F5

## 目标

建立 API 接入正式发布前的自动化和手工验收矩阵，避免只靠客户接口临场验证。

## 范围

- 单元测试：contract、provider、parser、schema inference、cursor、redaction。
- 集成测试：mock API、preview、schema snapshot、execution、ODS raw write、checkpoint。
- 前端测试：provider form、preview snapshot、stg mapping、sync config、diagnostics。
- E2E：创建 API 数据源、preview、创建任务、运行、查看 ODS 原始 record / `_dts_*` 技术字段、stg 产物和日志。

## 测试矩阵（最小集，作为发布门禁）

| 维度 A: 同步模式 | 维度 B: 鉴权 | 维度 C: 分页 | 维度 D: drift |
|---|---|---|---|
| `full_refresh` × `incremental(timestamp)` × `incremental(opaque_token)` | `none` × `apiKey` × `bearerToken` | `none` × `page_number` × `cursor_token` | `notify` × `block_stg` × `append_to_stg` |

- 矩阵不要求笛卡尔全展，但每个 cell 至少 1 个 mock API 用例。
- 失败注入用例：`AUTH 401`、`HTTP_4XX 400/403/404`、`HTTP_5XX 500/502/503`、`RATE_LIMIT 429 + Retry-After`、`TIMEOUT`、`PARSE error`、`PAGINATION cycle/无限页`、`SCHEMA_DRIFT 类型变更`、`ODS_WRITE 失败`。

## 覆盖率门槛

| 模块 | 工具 | 阈值 |
|---|---|---|
| `dts-ingestion`（API 包 + connector + capability） | jacoco line | ≥ 80% line / ≥ 70% branch |
| `dts-platform`（infra/api 包 + secret + checkpoint） | jacoco line | ≥ 80% line / ≥ 70% branch |
| `dts-platform-webapp`（API 接入向导 + provider form） | vitest c8 | ≥ 75% line（UI 视觉差异保护） |
| E2E | Playwright/Cypress | 关键 5 流程 100% 通过率 |

阈值在 CI 中由 jacoco-maven-plugin 与 vitest config 强制；门禁在 `verify` 阶段即拒绝合并。

## 完成标准

- [ ] 测试矩阵 16 个 cell（3 同步 × 3 鉴权 × 3 分页 × 3 drift 任意切片）至少各覆盖 1 个用例 —— 验收口径：测试 tag `@ApiMatrix(...)` + JaCoCo 报告。
- [ ] 9 类失败注入用例全部存在并通过 —— 与 F4/T05 错误枚举一一对应。
- [ ] ODS/stg 边界断言存在 —— ODS 表只包含 `_dts_raw_record` + `_dts_*` 技术字段；业务字段改名、类型标准化和敏感标记只出现在 schema snapshot / stg mapping。
- [ ] 脱敏断言覆盖日志、响应、job artifact、审计 —— 4 类工件各至少 1 个 fixture 比对测试。
- [ ] E2E 有可重复 mock API 环境 —— `docker compose -f tests/e2e/api/mock-api.yml up` 启动 WireMock 镜像；CI 上有 healthcheck。
- [ ] 发布前有 IT 证据目录 —— `worklog/v2.2.3/sprint-16-202604/evidence/` 目录归档 jacoco html、vitest html、Playwright trace、脱敏断言报告。
