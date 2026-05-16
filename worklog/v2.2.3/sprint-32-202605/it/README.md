# Sprint-32 集成测试计划

## 目标

验证 `dts-metrics` 从 platform 中独立出来后，默认随应用栈启动，权限和资产事实源仍然由 platform 统一管理。当前版本不在配置层限制 metrics，商务限制后续由 license 模块统一承接。

## 计划证据

| 证据 | 路径 | 状态 |
|---|---|---|
| 默认 metrics 启动证据 | `it/evidence/default-metrics/` | READY |
| 服务鉴权和 platform 契约 | `it/evidence/platform-contracts/` | READY |
| metric-pack 导入/校验/差异报告 | `it/evidence/metric-pack/` | READY |
| DSL SQL 生成和安全预览 | `it/evidence/dsl-preview/` | READY |
| dbt 候选 artifact 提交和门禁 | `it/evidence/dbt-publish/` | READY |
| BI Dataset 候选注册和权限校验 | `it/evidence/bi-permission/` | READY |
| 迁移 dry-run 和回滚 | `it/evidence/migration-rollback/` | READY |

## 验收命令草案

```bash
docker compose -f docker-compose-app.yml up -d dts-platform dts-ingestion dts-metrics dts-platform-webapp
curl -sS http://127.0.0.1:18082/api/capabilities
```

```bash
curl -sS http://127.0.0.1:18082/api/metrics/health
```

```bash
curl -sS -X POST http://127.0.0.1:18082/api/metrics/packs/import \
  -H 'Content-Type: application/yaml' \
  --data-binary @worklog/v2.2.3/sprint-32-202605/assets/examples/flower-rental/manifest.yml
```

```bash
curl -sS -X POST http://127.0.0.1:18082/api/metrics/models/{id}/generate-artifacts
curl -sS -X POST http://127.0.0.1:18082/api/metrics/models/{id}/preview
curl -sS -X POST http://127.0.0.1:18082/api/metrics/models/{id}/publish
```

```bash
curl -sS -X POST http://127.0.0.1:18082/api/internal/asset-permission/check \
  -H 'Content-Type: application/json' \
  -H 'X-DTS-Service: dts-metrics' \
  -H 'X-DTS-Service-Token: <token>' \
  -d '{"username":"ptrdemo","asset":{"type":"DATASET","id":"ads_flower_rental_overview"},"action":"VIEW"}'
```

## 阻断条件

- `dts-metrics` 直接读取 platform 用户、角色、数据源密钥或权限表。
- metrics 默认部署后导致数据源、ELT、dbt、资产目录主链路不可用。
- metric-pack 可以携带任意 SQL 或引用未登记物理表。
- 预览绕过 platform asset_grant。
- 发布绕过 dbt release gate。
- Sprint-32 MVP 误把 Superset 远端注册或完整大屏自动生成纳入交付准入。
- 兼容代理返回模糊 403/404，导致用户无法判断是未授权还是服务异常。
