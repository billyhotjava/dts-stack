# Sprint-21 Connector Center Runbook

## 发布前检查

1. 确认 `dts-platform`、`dts-ingestion`、`dts-common` 镜像来自同一代码基线。
2. 确认默认数据湖目标端已配置，且同步账号具备目标 ODS schema 的 `CREATE/ALTER/INSERT` 权限。
3. 确认 `services/dts-dbt/` 和 `services/dts-dbt/target` 可写，避免 dbt source 刷新失败。
4. 确认需要的 JDBC 驱动已放入现场驱动目录，并通过连接器目录或数据源配置引用。
5. 确认内部运行时服务令牌已完成数据库化配置，不需要修改初始化后的 `.env` 或 compose 文件；`dev`、`app`、`legacy` 三种模式均走同一条运行时配置链路：
   - 在平台侧由管理员创建 `serviceName=dts-ingestion` 的内部服务 token，平台只保存 hash，并支持过期/撤销。
   - 将返回的明文 token 写入 ingestion 的 `platform.serviceToken` 集成配置；该配置保存在 `infra_service_settings`，启用 `DTS_INFRA_ENCRYPTION_KEY` 后加密落库，GET 返回时脱敏。
   - `DTS_ADMIN_SERVICE_TOKEN` / `DTS_PLATFORM_SERVICE_TOKEN` 仅保留为历史兼容回退，不作为现场交付推荐方式。
6. 执行发布门禁：

```bash
cd source/dts-platform && ./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -DskipTests compile
cd ../dts-ingestion && ../dts-platform/mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -DskipTests compile
cd ../dts-platform && ./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -f ../dts-ingestion/pom.xml -Dtest=PlatformInfraClientTest test
jq empty ../../source/dts-common/src/main/resources/config/audit-action-catalog.json ../../source/dts-platform/src/main/resources/config/audit-action-catalog.json
git diff --check
```

## 现场冒烟

1. 准备源端样例表：

```bash
psql "$SOURCE_DATABASE_URL" -f worklog/v2.2.3/sprint-21-202604/it/samples/postgres-source.sql
```

2. 初始化内部服务 token。以下示例走平台与 ingestion API，页面上也应沉淀为“集成配置/内部服务凭据”流程：

```bash
SERVICE_TOKEN="$(
  curl -fsS "$DTS_BASE_URL/api/tokens/internal-service" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d '{"serviceName":"dts-ingestion","ttlDays":90}' | jq -r '.data.token'
)"

curl -fsS "$DTS_INGESTION_BASE_URL/api/infra/settings/platform" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d "{\"settings\":{\"baseUrl\":\"$DTS_BASE_URL\",\"apiPath\":\"/api\",\"serviceToken\":\"$SERVICE_TOKEN\"}}"
```

如需撤销或轮换内部服务 token，先创建新 token 并更新 ingestion `platform.serviceToken`，确认 smoke 通过后再调用：

```bash
curl -fsS -X DELETE "$DTS_BASE_URL/api/tokens/internal-service/$TOKEN_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

3. 在 DTS 页面登记 PostgreSQL/MySQL/DM8 等 JDBC 数据源，完成连接测试，并记录数据源 ID。
4. 执行 smoke 脚本。`DTS_TOKEN`、`DTS_COOKIE`、`DTS_COOKIE_JAR` 三选一；当前平台会话如果只返回 HttpOnly cookie，推荐先用 `curl -c /tmp/dts.cookies` 登录，再设置 `DTS_COOKIE_JAR=/tmp/dts.cookies`：

```bash
DTS_BASE_URL="https://dts.local" \
DTS_TOKEN="$ACCESS_TOKEN" \
DTS_COOKIE="$PORTAL_SESSION_COOKIE" \
DTS_COOKIE_JAR="/tmp/dts.cookies" \
DTS_DATA_SOURCE_ID="$DATA_SOURCE_ID" \
worklog/v2.2.3/sprint-21-202604/it/scripts/connector-center-smoke.sh
```

5. 检查输出目录 `/tmp/dts-sprint21-smoke`：
   - `01-schema-discover.json` 包含 `dts_smoke.erp_project` 字段、主键、索引和样本。
   - `03-ods-precheck.json` 返回 PASS/WARN/FAIL 明细，包含类型兼容、行数基线、源端查询和目标写入规则。
   - `04-ods-apply.json` 写入 ODS mapping、catalog 字段、dbt source 和接入血缘。
   - `06-ingestion-task-create.json` 创建入湖任务。
   - `08-backfill.json` 提交 `BACKFILL_RANGE` 补数执行，窗口为 `[2026-04-29T00:00:00Z, 2026-04-30T00:00:00Z)`。
   - `10-observability.json` 可看到任务执行观测指标。

6. 文件源样例使用 `it/samples/budget-upload.csv`，用于验证 Excel/CSV 上传接入链路和后续 dlt 阶段的文件源基线。

```bash
DTS_BASE_URL="https://dts.local" \
DTS_TOKEN="$ACCESS_TOKEN" \
DTS_COOKIE="$PORTAL_SESSION_COOKIE" \
DTS_COOKIE_JAR="/tmp/dts.cookies" \
DTS_FILE_SMOKE_OUT="worklog/v2.2.3/sprint-21-202604/it/evidence/20260430-rc1/file-source-smoke" \
bash worklog/v2.2.3/sprint-21-202604/it/scripts/file-source-smoke.sh
```

7. 执行凭据脱敏审计。`DTS_SECRET_SENTINEL` 建议填本次测试数据源密码或专门设置的测试哨兵值：

```bash
DTS_BASE_URL="https://dts.local" \
DTS_TOKEN="$ACCESS_TOKEN" \
DTS_COOKIE="$PORTAL_SESSION_COOKIE" \
DTS_COOKIE_JAR="/tmp/dts.cookies" \
DTS_DATA_SOURCE_ID="$DATA_SOURCE_ID" \
DTS_SECRET_SENTINEL="$TEST_SOURCE_PASSWORD" \
DTS_AUDIT_EXPORT_QUERY="action=FOUNDATION_DATASOURCE_REGISTER" \
DTS_REDACTION_OUT="worklog/v2.2.3/sprint-21-202604/it/evidence/20260430-rc1/credential-redaction" \
bash worklog/v2.2.3/sprint-21-202604/it/scripts/credential-redaction-audit.sh
```

8. 按 `it/evidence/dialect-validation-matrix.md` 补齐 PostgreSQL、MySQL、Oracle、SQL Server、DM8 的方言验证结果。现场缺少某类数据库时，在验收记录中写明原因和补测计划。

## 升级步骤

1. 备份 `.env`、`config/`、`services/dts-dbt/`、Airflow DAG/job 目录和平台数据库。
2. 更新镜像版本配置并构建：

```bash
PREBUILD_JARS=1 MAVEN_UNRESTRICTED=1 ./builds/dts-build.sh -all
```

3. 先升级 `dts-common` 依赖镜像，再升级 `dts-platform` 和 `dts-ingestion`。
4. 重启应用服务后，执行连接器目录刷新、数据源连接测试和 smoke 脚本。

## 回滚步骤

1. 停止新版本应用服务，保留数据库和日志现场。
2. 恢复上一版本镜像标签与 `imgversion*.conf`。
3. 恢复上一版本 `services/dts-dbt/`、Airflow DAG/job 目录备份。
4. 启动上一版本服务，并执行数据源连接测试、任务列表加载、最新执行日志查看。
5. 对已经创建的新任务，优先停用或删除任务，不直接删除 ODS 表；如需数据回退，走入湖 rollback 能力。

## 常见故障

| 现象 | 定位 | 处理 |
|---|---|---|
| `ods-precheck` 目标写入失败 | 默认数据湖目标端缺失、schema 不存在或权限不足 | 配置默认目标端，创建 ODS schema，授予 `CREATE/ALTER/INSERT` |
| `SOURCE_QUERY_PERMISSION` 失败 | 源端账号无 SELECT 权限 | 修正源端授权或数据源凭据 |
| `SOURCE_ROW_VOLUME_BASELINE` 告警 | 当前行数超出配置化基线 | 核对源端业务变更；确认正常后调整 `precheckRowCountBaselines` |
| 用户侧 `/detail` 返回空 `secrets` | 这是预期行为 | 用户侧详情已强制脱敏；内部执行链路使用 `runtime-detail` 获取运行时凭据 |
| `runtime-detail` 返回 403 | 使用了用户 token、未带内部服务头、服务令牌不是 `service:dts-ingestion` 类型或 ingestion 未保存 `platform.serviceToken` | 创建/轮换平台内部服务 token，并写入 ingestion `platform.serviceToken` 集成配置；浏览器和普通用户不可读取明文凭据 |
| dbt source 刷新失败 | `services/dts-dbt` 不可写或 dbt 项目缺失 | 修正目录权限，确认 compose mount 未使用只读模式 |
| Run Center 看不到执行 | Airflow DAG/job 未生成或执行仍在排队 | 查看任务详情、Airflow DAG、Addax job 路径和执行日志 |
| 补数没有推进 watermark | 这是预期行为 | `BACKFILL_RANGE` 只修复历史窗口，不推进主增量 checkpoint；正常调度成功后再推进 watermark |
| 审计动作缺失 | catalog 未同步或 ingestion 只写本地日志 | 确认 common/platform audit catalog 同版，检查 ingestion 日志中的 `audit action=` |
