# Sprint-21 集成验收

## 目标

验证 DTS Connector Center 从数据源创建到运行血缘的完整闭环。

## 冒烟路径

```text
创建数据源
  -> 连接测试
  -> Schema Discover
  -> 选择表字段
  -> 提交前预检
  -> 生成 ODS / Addax Job / Airflow DAG / dbt source
  -> 执行同步
  -> Run Center 查看状态和日志
  -> LineagePage 查看 source -> task -> ODS -> dbt model
```

## 验收样例

| 场景 | 输入 | 期望 |
|---|---|---|
| PostgreSQL 单表全量 | `public.orders` | 生成 `ods_orders`，执行成功，血缘可见 |
| PostgreSQL 多表批量 | `orders/customers/items` | 批量生成任务，运行中心可查看 |
| 时间戳增量 | `updated_at` | watermark 成功推进，失败不推进 |
| Excel/CSV 文件 | 上传文件 | 生成 ODS，运行状态和字段元数据可见 |
| Schema drift | 源表新增字段 | drift 事件出现，可选择同步到 ODS |

## 验收证据

- API 请求/响应样例。
- Run Center 截图。
- LineagePage 截图。
- 任务执行日志。
- 审计事件记录。

## 构建验证

```bash
cd source/dts-platform && ./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -DskipTests compile
cd source/dts-platform-webapp && pnpm build
jq empty source/dts-common/src/main/resources/config/audit-action-catalog.json source/dts-platform/src/main/resources/config/audit-action-catalog.json
git diff --check
```

## 当前已接入接口

- `POST /api/infra/data-sources/{id}/schema-discover`：探测 schema/table/column/key/index，并支持缓存与强制刷新。
- `POST /api/infra/data-sources/{id}/ods-preview`：预览 ODS DDL、dbt source YAML、Addax/Airflow 草稿。
- `POST /api/infra/data-sources/{id}/ods-precheck`：建任务前预检 ODS/同步任务草稿基础规则，并执行源端只读探测和目标端 ODS schema 写入探测；返回 PASS/WARN/FAIL、规则明细和修复建议。
- `POST /api/infra/data-sources/{id}/ods-apply`：写入 ODS 映射、catalog 字段、dbt source 和接入血缘。
- `POST /api/infra/data-sources/{id}/sync-task-draft`：生成 ingestion task payload。
- `POST /api/ingestion/tasks`：创建同步任务并生成 Addax Job / Airflow DAG。
- `POST /api/ingestion/tasks/{taskId}/executions/{executionId}/retry/async`：Run Center 列表级失败重试/整批重跑。
