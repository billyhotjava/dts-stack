# F8: 安全审计、验收与发布材料

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

把接入中心能力收口到可交付状态：权限、审计、凭据安全、现场冒烟、发布步骤和回滚说明完整。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 数据源/连接器/任务权限矩阵 | P0 | DONE | F1-F6 |
| T02 | 接入中心审计事件补齐 | P0 | IN_PROGRESS | F2-F6 |
| T03 | 端到端验收脚本与样例数据 | P0 | READY | F1-F7 |
| T04 | 发布、升级、回滚与运维 Runbook | P1 | READY | T03 |

## 完成标准

- [ ] 数据源凭据不在 API、日志、审计、导出中明文出现。
- [ ] 所有接入中心关键操作进入审计。
- [ ] 至少一条数据库源和一条文件源完成端到端冒烟。
- [ ] 验收覆盖：建数据源、discover、生成 ODS、建任务、运行、看日志、看血缘、重跑。
- [ ] Runbook 包含部署参数、故障排查、升级回滚和现场演示脚本。

## 权限矩阵

| 对象 | 查看 | 创建/更新 | 删除/停用 | 执行/重跑 | 说明 |
|---|---|---|---|---|---|
| Connector Registry | `INFRA_MAINTAINERS` | 内置 seed/刷新接口 | 不开放删除 | 刷新目录 | 当前不做 Marketplace 写入。 |
| 数据源 | `INFRA_MAINTAINERS` | `INFRA_MAINTAINERS` | `INFRA_MAINTAINERS` | 连接测试 | 含凭据详情的接口仅 `OP_ADMIN` 可访问。 |
| Schema Discover | `INFRA_MAINTAINERS` | - | - | 探测/强制刷新 | 只返回脱敏元数据和采样结果。 |
| ODS/dbt source 生成 | `INFRA_MAINTAINERS` | `INFRA_MAINTAINERS` | - | 预览/落库 | 写入 catalog、dbt source 和接入血缘。 |
| 同步任务 | 入湖任务权限 | 入湖任务权限 | 入湖任务权限 | 执行、失败重试、整批重跑 | 任务运行中心面向业务用户，Airflow UI 仍为运维入口。 |

## 当前落地

- 平台侧 `audit-action-catalog.json` 已补齐 Connector Center 相关动作：连接器目录、数据源登记/测试/停用、Schema Discover、ODS 预览/落库、同步任务草稿生成和调度任务动作。
- `dts-common` 默认 audit catalog 同步补齐同一批 Foundation 动作，避免不同服务加载默认目录时出现 unknown action fallback。
- 数据源 Resource 已显式记录数据源查看、创建、更新、删除、连接测试、Schema Discover、ODS preview/apply、ODS precheck 和 sync-task-draft 审计事件。
- 新增 `FOUNDATION_ODS_PRECHECK` 审计动作，用于区分建任务前 dry-run/precheck 与真正落库/创建任务。
- Run Center 已提供最新失败执行的失败重试与整批重跑入口，任务执行/重跑仍需继续确认 ingestion 服务侧审计闭环。

## 端到端验收路径

```text
1. 新增 PostgreSQL/MySQL 数据源，完成连接测试。
2. 执行 Schema Discover，确认表、字段、主键、索引、增量候选和缓存/漂移状态。
3. 配置 ODS schema、系统编码、业务编码、同步模式，预览 ODS DDL / dbt source / Addax / Airflow 草稿。
4. 执行提交前预检，确认 PASS/WARN/FAIL、规则明细和审计事件。
5. 生成 ODS 映射与 dbt source，并检查 catalog 表字段和 Sprint-20 lineage graph。
6. 生成同步任务，执行一次全量同步。
7. 在 Run Center 查看行数、耗时、错误分类、日志和血缘入口。
8. 制造一次失败执行，验证失败重试、整批重跑和日志建议。
9. 切换源端 schema，强制刷新 discover，验证 schema drift 摘要。
10. 在审计日志中核对数据源、discover、ODS precheck、ODS apply、任务草稿、执行/重跑相关事件。
```

## 待补

- 补样例库 SQL、样例文件和一键冒烟脚本。
- 任务创建、任务执行、任务删除、失败重试、整批重跑需要在 ingestion 服务侧形成显式审计动作，避免仅依赖平台代理或 HTTP fallback。
- Runbook 需要补发布参数、升级回滚、现场演示脚本和常见故障定位。
