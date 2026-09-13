# Sprint-27 IT 验收

**状态**: IN_PROGRESS

## 验收范围

- ELT 可视化控制台 smoke。
- 指标可视化运营台 smoke。
- 审计一致性 smoke。
- Kafka 关闭状态下主流程 smoke。

## 证据目录

证据统一归档到：

`worklog/v2.2.3/sprint-27-202605/it/evidence/<date>-local/`

## Smoke 脚本

```bash
DTS_WEBAPP_URL=http://127.0.0.1:3001 \
DTS_PLATFORM_API_URL=http://127.0.0.1:18082/api \
  worklog/v2.2.3/sprint-27-202605/it/scripts/sprint-27-smoke.sh
```

脚本覆盖：

- `/explore/etl`
- `/metrics/operations`
- `/ops/events`
- `/ops/audit-evidence`
- `/ops/release-governance`
- `/metrics/semantic`
- `/metrics/semantic/subjects`
- `/metrics/semantic/objects`
- `/metrics/semantic/metrics`
- `/metrics/semantic/models`
- `/metrics/semantic/publish`
- `/metrics/semantic/runs`
- `/api/platform/sprint27/elt-console`
- `/api/platform/sprint27/metric-operations`
- `/api/platform/sprint27/events-console`
- `/api/platform/sprint27/audit-evidence`
- `/api/platform/sprint27/release-governance`
- `/api/semantic/workbench`
- `/api/semantic/menu-diagnostics`

## 自动化校验

```bash
cd source/dts-platform
./mvnw -ntp -DskipTests=false -Dtest=Sprint27ConsoleServiceTest test

cd ../dts-platform-webapp
pnpm build
```

`Sprint27ConsoleServiceTest` 覆盖聚合 API 的数据源状态和发布治理依赖失败场景，防止后续页面重构时重新出现“空数据不可解释”或“依赖失败误判可发布”。

## 原则

- Kafka 不作为必需依赖。
- 端到端权限、脱敏、审批只验证审计字段预留，不验证最终策略。
- smoke 失败时必须能区分页面失败、API 失败、依赖服务失败。
