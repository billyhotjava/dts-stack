# Sprint-19 集成测试证据

## 当前基线

| 检查项 | 当前观察 | 结论 |
|---|---|---|
| OpenMetadata server | `dts-openmetadata` 已启动 | 已接入，需要继续验证 API 与认证 |
| OpenMetadata ingestion | `dts-openmetadata-ingestion` 退出 | 当前未实际采集 |
| ingestion 日志 | `OPENMETADATA_AUTH_TOKEN empty; skip ingestion` | token/no-auth 策略需要修复 |
| FQN pattern | `.env` 中出现 `{service` | 平台查询 OpenMetadata 可能无法命中 |
| lineage 注册 | 调用传入 `List.of()` | 当前实际跳过注册 |
| 目标库配置解析 | 仅读取顶层连接字段 | 与 Addax nested 配置不完全兼容 |

## 自动化验证矩阵

| 场景 | 覆盖点 | 状态 |
|---|---|---|
| 配置生成 | `init.sh` 默认 FQN pattern、旧 `.env` 迁移、坏 pattern 诊断 | TODO |
| OpenMetadata health | server API、token/no-auth、容器状态 | TODO |
| 连接配置解析 | nested `connection`、`jdbcUrl`、顶层字段、多数据源类型 | TODO |
| service/pipeline 创建 | create/ensure/trigger 结果和错误处理 | TODO |
| 血缘注册 | reader/writer/tableMapping 生成真实 source/target streams | TODO |
| 平台读路径 | 元数据、血缘、质量 FQN 命中和 fallback reason | TODO |
| 前端状态 | OpenMetadata 命中、未命中、回退、失败展示 | TODO |

## 预期命令

| 命令 | 目的 | 结果 |
|---|---|---|
| `docker compose -f docker-compose-app.yml ps dts-openmetadata dts-openmetadata-ingestion dts-platform dts-ingestion` | 检查运行时服务状态 | TODO |
| `docker logs dts-openmetadata-ingestion --tail 100` | 检查 ingestion 是否跳过或失败 | TODO |
| `curl -sS http://127.0.0.1:18585/api/v1/system/version` | 检查 OpenMetadata API | TODO |
| `source/dts-platform/mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -f source/dts-ingestion/pom.xml -Dtest=OpenMetadataAdapterTest,OpenMetadataClientTest test` | ingestion 侧单测 | TODO |
| `source/dts-platform/mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -Dtest=OpenMetadataServiceTest test` | platform 侧单测 | TODO |
| `pnpm build`（`source/dts-platform-webapp`） | 前端构建检查 | TODO |

## 手工验收步骤

### Step 1 - 配置验收

1. 重新执行初始化或读取当前 `.env`。
2. 确认 `DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN` 为完整 pattern。
3. 明确当前环境使用 token 还是 no-auth。
4. 检查 compose 中 server、ingestion、platform、ingestion-service 的配置一致。

### Step 2 - 接入任务验收

1. 创建数据库接入任务，选择至少 2 张源表。
2. 执行任务并确认 ODS 表生成。
3. 检查 `dts-ingestion` 日志中 OpenMetadata service/pipeline/lineage 结果。
4. 在 OpenMetadata API 或 UI 中检查 service、table、pipeline、lineage。

### Step 3 - 平台查询验收

1. 打开 catalog 数据集详情。
2. 检查元数据来源标识是否为 OpenMetadata 或本地 catalog。
3. 打开血缘和质量页，确认 FQN lookup 日志和结果一致。
4. 人为关闭 OpenMetadata 或使用不存在 FQN，确认 UI/API 显示 fallback reason。

## 产物留存

- `baseline-openmetadata-status.md` - 修复前容器和 API 基线。
- `fqn-pattern-migration.md` - `.env` 修复前后对比。
- `lineage-registration-sample.json` - 血缘注册请求与响应样例。
- `openmetadata-api-smoke.md` - OpenMetadata API 冒烟结果。
- `catalog-fallback-evidence.md` - 平台回退展示证据。
