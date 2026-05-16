# Sprint-31 集成测试计划

## 目标

验证 DTS 企业级数据平台黄金链路，并证明语义指标增值能力可从基础版关闭：

```text
数据源 -> ODS -> dbt DWD/DWS/ADS -> Catalog/OpenMetadata/OpenLineage -> platform 权限/资产/审计
可选：语义指标 -> BI Dataset -> 大屏权限
```

## 计划证据

| 证据 | 路径 | 状态 |
|---|---|---|
| 黄金链路 smoke 脚本输出 | `it/evidence/golden-path/` | READY |
| Connector ODS 预检/应用结果 | `it/evidence/connector-center/` | READY |
| Addax/Airflow/OpenLineage 运行血缘 | `it/evidence/runtime-lineage/` | READY |
| dbt compile/test/build + release gate | `it/evidence/dbt-release-gate/` | READY |
| 语义指标 capability flag 和服务边界 | `it/evidence/semantic-boundary/` | READY |
| BI Dataset 和大屏权限验收 | `it/evidence/consumption-permission/` | READY |
| 性能/审计/告警基线 | `it/evidence/observability-performance/` | READY |

## 验收命令草案

```bash
curl -sS http://127.0.0.1:18082/api/infra/data-sources
```

```bash
curl -sS -X POST http://127.0.0.1:18082/api/infra/data-sources/{id}/ods-precheck \
  -H 'Content-Type: application/json' \
  -d '{"tables":["public.demo_table"],"odsSchema":"ods_demo"}'
```

```bash
dbt compile --project-dir services/dts-dbt --profiles-dir services/dts-dbt/profiles
dbt test --project-dir services/dts-dbt --profiles-dir services/dts-dbt/profiles
dbt build --project-dir services/dts-dbt --profiles-dir services/dts-dbt/profiles --select tag:dbt
```

```bash
curl -sS http://127.0.0.1:18082/api/capabilities
curl -sS http://127.0.0.1:18082/api/semantic/health
```

```bash
curl -sS -X POST http://127.0.0.1:18082/api/internal/asset-permission/check \
  -H 'Content-Type: application/json' \
  -H 'X-DTS-Service: dts-analytics' \
  -H 'X-DTS-Service-Token: <token>' \
  -d '{"username":"ptrdemo","userRoles":["花卉租赁PTR"],"userClassification":"INTERNAL","asset":{"type":"SCREEN","id":"1"}}'
```

## 阻断条件

- dbt strict release gate 未通过。
- 运行血缘不能落库，且没有明确 fallback 告警。
- 非 PUBLIC 大屏在无授权用户列表中可见。
- 关闭语义指标 capability 后，基础版菜单/API 仍暴露不可用入口。
- 语义模型未审核通过仍可发布。
- Connector 预检对大表执行无超时 `count(*)`。
