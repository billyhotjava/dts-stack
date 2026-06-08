# Sprint-35 集成测试计划

## 目标

证明 `dts-metrics` 按 ELT 分层契约工作：默认从 DWS/ADS 做指标可视化，高级流程从 DWD 生成候选 DWS，所有预览、验证、发布和消费都通过 platform 资产、权限、RLS、审计和 dbt 发布网关。

## 证据目录

| 证据 | 路径 | 状态 |
|------|------|------|
| 架构与 PRD review | `it/evidence/architecture-review/` | READY |
| API contract | `it/evidence/api-contracts/` | IN_PROGRESS |
| 前端工作台 Playwright | `it/evidence/frontend-workbench/` | READY |
| 后端 graph/artifact/dbt gateway | `it/evidence/backend-dbt-gateway/` | IN_PROGRESS |
| 安全与评审 gate | `it/evidence/security-review/` | READY |

## 验收命令草案

```bash
cd source/dts-metrics-webapp
pnpm run test:source
pnpm run typecheck
pnpm run build
```

```bash
cd source
./mvnw -q -pl dts-metrics test
./mvnw -q -pl dts-platform -Dtest='*Metric*,*Dbt*Release*,*AssetPermission*,*Audit*' test
```

```bash
curl -sS 'http://127.0.0.1:18084/api/metrics/visual-assets?layers=DWS,ADS'
curl -sS 'http://127.0.0.1:18084/api/metrics/visual-assets?layers=DWD&includeDrilldown=true'
```

```bash
curl -sS -X POST http://127.0.0.1:18084/api/metrics/graphs/{graphId}/preflight \
  -H 'Content-Type: application/json' \
  -d @worklog/v2.2.3/sprint-35-202605/it/fixtures/dws-graph-preflight.json
```

```bash
curl -sS -X POST http://127.0.0.1:18084/api/metrics/models/{modelId}/artifacts \
  -H 'Content-Type: application/json' \
  -d @worklog/v2.2.3/sprint-35-202605/it/fixtures/dwd-to-dws-validation.json

curl -sS -X POST http://127.0.0.1:18084/api/metrics/models/{modelId}/validate \
  -H 'Content-Type: application/json' \
  -d @worklog/v2.2.3/sprint-35-202605/it/fixtures/dwd-to-dws-validation.json

curl -sS -X POST http://127.0.0.1:18084/api/metrics/models/{modelId}/publish

curl -sS http://127.0.0.1:18084/api/metrics/models/{modelId}/versions

curl -sS -X POST http://127.0.0.1:18084/api/metrics/models/{modelId}/rollback \
  -H 'Content-Type: application/json' \
  -d '{"reason":"smoke rollback"}'
```

```bash
curl -sS -X POST http://127.0.0.1:18082/api/internal/metrics/model-validation \
  -H 'Content-Type: application/json' \
  -H 'X-DTS-Service: dts-metrics' \
  -H 'X-DTS-Service-Token: <token>' \
  -d @worklog/v2.2.3/sprint-35-202605/it/fixtures/dwd-to-dws-validation.json
```

## 阻断条件

- 普通用户能把 ODS/STG 拖入指标画布。
- DWD 明细能绕过高级建模、粒度校验、标准码校验或 RLS/masking 直接发布。
- `dts-metrics` 直接读取 platform 用户、角色、权限、数据源密钥或 dbt 文件系统。
- platform internal contract 不可用时仍生成候选 artifact。
- 发布绕过 platform audit、review、dbt release gate、BI Dataset 或 lineage register。
