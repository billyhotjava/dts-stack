# Sprint-103 发布与回滚计划

检查日期：2026-08-28。发布对象仅为 `dts-ingestion`、`dts-platform`、`dts-platform-webapp`；不重建其他服务，不修改或清理既有 Airflow DAG。

## 发布结论

本变更可以按“expand + 向后兼容应用”发布：数据库只新增 3 个可空 `target_dataset_id` 列和 1 个带条件的唯一索引；旧应用不会读取新列，新应用对旧数据使用 `qualityPolicyRef=dataset:<uuid>` 兼容读取。API 只增加端点和可选请求头，旧调用方保持可用。

不执行在线 schema contract。应用回滚时保留新增列和索引；若新版本已写入目标资产身份，禁止通过 drop column 回滚数据结构。

## 影响与发布顺序

| 顺序 | 对象 | 原因 | 放行条件 |
|---:|---|---|---|
| 1 | `dts-ingestion` | Liquibase 先扩展 schema，并提供 design/topology/command API | `/management/health=UP`；changeSet 已应用；列和索引存在 |
| 2 | `dts-platform` | 提供权限校验、资产/质量证据投影和平台代理 | `/management/health=UP`；启动日志无 Repository/SQL 错误 |
| 3 | `dts-platform-webapp` | 新页面依赖以上两个后端 | Nginx 配置通过；页面与静态资源 200 |

部署窗口内，前两个步骤之间旧前端仍使用旧 API；新后端保留这些 API。新前端必须最后切换。

## 发布前检查

1. 记录 Git revision、精确 diff、三张当前镜像 digest 和容器健康状态。
2. 为三个当前镜像各创建一个带时间戳的本地 rollback tag；不得覆盖已有 rollback tag。
3. 在 `dts_platform` 数据库执行只读检查：

```sql
select batch_id, count(*)
from ingestion_execution
where batch_id is not null
group by batch_id
having count(*) > 1;

select parent_execution_id, count(*)
from ingestion_execution
where parent_execution_id is not null
group by parent_execution_id
having count(*) > 1;
```

两条查询必须返回 0 行。2026-08-28 预检结果均为 0。

4. 确认 Airflow import error 为 0，并保存 [DAG 对账](dag-reconciliation.md)。未审批的 DAG 一律 `KEEP`。
5. 完成聚焦测试、legacy 前端构建和敏感信息扫描后才构建镜像。

## 构建与部署命令

在仓库根目录执行定向构建，不导出无关镜像：

```bash
./builds/dts-build.sh --image dts-ingestion dts-platform dts-platform-webapp --no-save
```

按以下顺序单服务重建；每一步通过健康检查后才进入下一步：

```bash
docker compose -p v223 -f docker-compose-app.yml up -d --no-deps --force-recreate dts-ingestion
docker compose -p v223 -f docker-compose-app.yml up -d --no-deps --force-recreate dts-platform
docker compose -p v223 -f docker-compose-app.yml up -d --no-deps --force-recreate dts-platform-webapp
```

实际执行时必须把旧/新镜像 ID、容器 ID和健康结果归档到 IT 证据；不能用“build 成功”替代运行态验证。

## 数据库放行检查

部署 `dts-ingestion` 后验证：

```sql
select id, author, dateexecuted, exectype
from databasechangelog
where id in (
  '20260822-01-ingestion-quality-workflow-link',
  '20260828-00-ingestion-target-dataset-identity',
  '20260828-01-ingestion-execution-command-idempotency'
)
order by dateexecuted;

select table_name, column_name, is_nullable
from information_schema.columns
where table_name in ('ingestion_task', 'ingestion_task_revision', 'ingestion_execution')
  and column_name = 'target_dataset_id'
order by table_name;

select indexname, indexdef
from pg_indexes
where tablename = 'ingestion_execution'
  and indexname in ('uk_ingestion_execution_batch_id', 'uk_ingestion_execution_retry_parent');
```

预期：3 个新列均为 nullable；batch-id 与 retry-parent 两个唯一索引存在；历史 execution 不做猜测性资产回填。

## 回滚策略

### 应用回滚

按相反顺序恢复 rollback 镜像：webapp → platform → ingestion。每步使用精确 rollback tag 重建单个服务并验证健康。新增 schema 保留，因此旧应用仍可运行；数据库写入不丢失。

### 数据库恢复

- 唯一索引创建失败：停止发布，先保留旧应用并核对重复 `batch_id`；不自动删除或合并 execution。
- changeSet 部分失败：使用前向修复恢复，确认 `databasechangelog` 与真实对象一致后重试。
- 已有新版本写入后：禁止执行 drop-column rollback。只有在维护窗口、证明 3 列全部无业务值且经审批后，才可单独评估 Liquibase rollback。

### DAG 与任务版本

部署本身不发布、暂停或删除 DAG。若业务发布动作失败，继续复用既有 admission 的 staged/published 补偿，不手工把 draft revision 改为 ACTIVE。历史 DSL 和旧 ACTIVE revision 均保留。

## 回滚演练判据

- 三个旧镜像均有唯一 rollback tag，可从 tag 恢复。
- 恢复旧应用后，健康检查、旧任务列表和旧 execution 查询正常。
- 新增列/索引保留，旧应用无 SQL 兼容错误。
- Airflow DAG 数、暂停状态和 import error 与发布前一致。
- 回滚过程不新增 execution、quality workflow 或重复调度。

## 爆炸半径与止损

- UI 爆炸半径：现有 `/explore/etl/orchestration` 页面。
- 后端爆炸半径：任务 design/topology/schedule、task-scoped execution 命令和读取投影。
- 数据爆炸半径：3 个 nullable 字段、1 个 execution 唯一索引；不回填历史质量结论。
- 止损：先恢复 webapp；若后端异常再依次恢复 platform、ingestion。质量投影无法证明 CURRENT 时一律 fail closed，不显示“可信可用”。

## 2026-08-28 实际发布记录

- 旧镜像回滚标签：`rollback-sprint103-20260828-030521`。
- 新镜像摘要：ingestion `sha256:40cae5c9e830…`、platform `sha256:5ad73eb0d5e6…`、webapp `sha256:033dcc1937f9…`。
- ingestion `/management/health` 为 `UP`；3 个 changeSet 执行成功，列与索引符合本计划。
- platform `/management/health` 为 `UP`；webapp `nginx -t` 成功，内外部根页面与编排路由返回 200。
- 未修改 Airflow DAG；发布后仍为 57 个总 DAG、34 个接入 DAG、0 个 import error。
- 本地 Chrome 150 真实只读旅程通过，关键编排 API 全部 200；Chrome 95 仍需现场复验。
- 未实际切回旧镜像；回滚恢复能力由不可覆盖 tag、精确旧 digest、expand schema 和顺序恢复命令证明，真实切回仍需维护窗口。

## 2026-08-29 独立编排入口退役增量

本次增量只替换 `dts-admin`、`dts-platform`、`dts-platform-webapp`，不重建 `dts-ingestion`，不修改或清理 Airflow DAG。发布 revision 为 `0ec0a9758e071514acce72765dd0922324f99162`，镜像从干净 detached worktree 构建。

| 服务 | 发布前镜像 | 发布后镜像 |
|---|---|---|
| `dts-admin` | `sha256:d3a7890a30b…` | `sha256:ac4163d2c8cc…` |
| `dts-platform` | `sha256:c741b8c1ef23…` | `sha256:8f143aeccb45…` |
| `dts-platform-webapp` | `sha256:095997b4ccc6…` | `sha256:9071444d918b…` |

- 回滚标签统一为 `rollback-sprint103-retirement-20260829-145249`，分别绑定上述三个发布前精确镜像。
- 发布顺序为 Admin → Platform → Webapp；Admin 和 Platform 健康放行后才切换前端。
- Admin changeSet `20260829-01-retire-task-orchestration-menu` 已执行，目标菜单记录为 `deleted=true`，未删除历史行和角色绑定。
- Admin、Platform 健康为 `UP`；Webapp `nginx -t` 成功，内外部首页为 200；真实 Chrome 登录旅程 1/1 通过。
- Platform 启动后仍有既有 `machineActor is not trusted` 定时审计错误，不影响健康、菜单迁移或本次路由；该告警不计入本次改动。
- 回滚时按 Webapp → Platform → Admin 顺序把 `1.0.0` 重标到对应 rollback 镜像并单服务重建；数据库菜单软删除可使用 changeSet rollback 恢复，但仅在确认产品范围需要回退时执行。
- Chrome 95 本机不可用，仍需客户现场复验；真实运行库当前无接入任务，业务任务行与执行动作未纳入本次只读发布验收。
