# Sprint-32 集成测试计划

## 目标

验证 React Flow 指标与语义工作台的完整闭环：画布建模、指标 DSL、DWS/ADS artifact、platform contract precheck、platform/dbt validation、审核发布、BI Dataset/血缘注册和回滚。

## 计划证据

| 证据 | 路径 | 状态 |
|---|---|---|
| React Flow 画布交互 | `it/evidence/react-flow-canvas/` | READY |
| platform 契约和 dbt 验证网关 | `it/evidence/platform-contracts/` | IN_PROGRESS |
| metrics 前端路由拆分 | `it/evidence/metrics-frontend/` | DONE |
| metric-pack 到 graph draft | `it/evidence/metric-pack/` | READY |
| DSL SQL 生成和安全预览 | `it/evidence/dsl-preview/` | READY |
| DWS/ADS artifact 和 dbt validation | `it/evidence/dbt-publish/` | READY |
| BI Dataset、血缘和权限校验 | `it/evidence/bi-permission/` | READY |
| 迁移 dry-run 和回滚 | `it/evidence/migration-rollback/` | READY |

## 验收命令草案

```bash
cd source/dts-metrics-webapp
pnpm install --frozen-lockfile
pnpm run test:source
pnpm run typecheck
pnpm run build
```

```bash
cd source
./mvnw -q -pl dts-metrics test
./mvnw -q -pl dts-platform -Dtest='*Metric*,*Dbt*Release*,*Semantic*' test
```

```bash
docker compose -f docker-compose-app.yml up -d dts-platform dts-ingestion dts-metrics dts-platform-webapp
curl -sS http://127.0.0.1:18082/api/capabilities
curl -sS http://127.0.0.1:18082/api/metrics/health
```

```bash
curl -sS -X POST http://127.0.0.1:18082/api/internal/metrics/model-validation \
  -H 'Content-Type: application/json' \
  -H 'X-DTS-Service: dts-metrics' \
  -H 'X-DTS-Service-Token: <token>' \
  -d @worklog/v2.2.3/sprint-32-202605/it/fixtures/model-validation-request.json
```

```bash
RUN_LIVE=1 \
DTS_AUTH_HEADER='Authorization: Bearer <token>' \
bash worklog/v2.2.3/sprint-32-202605/it/scripts/metrics-mvp-admission-check.sh
```

## 阻断条件

- React Flow 画布只是静态展示，不能保存、加载、验证或映射到 DSL。
- `dts-metrics` 直接读取 platform 用户、角色、数据源密钥、权限表或 dbt 项目目录。
- 模型检测由 `dts-metrics` 或前端直接调用 dbt，而不是通过 platform 验证网关。
- artifact 生成绕过 platform asset permission、RLS、domain、glossary 或 data standard 校验。
- 发布绕过 platform review、audit、dbt release gate 或 release submit。
- 验证失败不能定位到具体 node/edge/field/metric。
- 旧入口打开占位页面或返回模糊 403/404。
