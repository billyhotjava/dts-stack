# 本地制品验证报告

**验证日期**：2026-07-29

**验证范围**：本目录 SQL、dbt 工程和阻断级数据测试

**非验证范围**：DTS UI、真实入湖任务、ModelSpec 发布、Airflow 物化、资产回写、权限和 BI/API

## 1. 验证环境

- 临时数据库镜像：`postgres:17-alpine`
- dbt 镜像：`dts-dbt:1.10.0`
- 镜像内 dbt：1.11.3
- PostgreSQL adapter：1.10.0
- 临时容器无宿主机数据库数据卷，验证结束后已停止并自动删除。
- 未连接或修改任何客户数据库、ODS 或现有 DTS 服务。

## 2. 源 SQL

按顺序执行：

1. `sql/01-source-bootstrap.sql`
2. `sql/02-source-dirty-cases.sql`
3. `sql/03-source-remediation-and-increment.sql`

结果：

| 阶段 | 组织 | 项目 | 任务快照 | 结论 |
|---|---:|---:|---:|---|
| 基线 | 3 | 2 | 8 | 通过 |
| 注入脏数据 | 3 | 3 | 9 | 通过 |
| 修复和新增快照 | 3 | 2 | 12 | 通过 |

初始化、脏数据注入和修复 SQL 均以 `ON_ERROR_STOP` 执行成功。

## 3. dbt Parse

本机 `dbt parse` 成功识别：

- 6 个模型。
- 3 个 source。
- 26 个 generic test，其中 8 个属于 source、18 个属于模型。
- 3 个 singular test。

`tag:it-demo` 构建执行 6 个模型、18 个模型 generic test 和 3 个 singular test；source test 可在实际接入验收时单独选择执行。

临时 `profiles.yml` 和生成的 `target/`、`logs/` 已清除，未纳入交付材料。

## 4. 基线 dbt Build

基线 ODS 行数为 3、2、8。

结果：

```text
PASS=27 WARN=0 ERROR=0 SKIP=0 TOTAL=27
```

目标行数：

| 关系 | 行数 |
|---|---:|
| `it_demo_dwd_dim_date` | 730 |
| `it_demo_dwd_dim_org` | 3 |
| `it_demo_dwd_dim_project` | 2 |
| `it_demo_dwd_fct_task_snapshot` | 8 |
| `it_demo_dws_project_health` | 4 |
| `it_demo_ads_project_overview` | 4 |

## 5. 脏数据阻断

注入脏数据并刷新 Demo ODS 后，`dbt build` 按预期失败：

```text
PASS=13 WARN=0 ERROR=7 SKIP=7 TOTAL=27
```

7 个失败分别为：

1. 项目责任组织引用失败。
2. 任务项目引用失败。
3. 任务状态标准码失败。
4. 风险等级标准码失败。
5. 进度范围失败。
6. 成本非负失败。
7. 计划日期顺序失败。

DWS 和 ADS 因上游测试失败被跳过，证明阻断发生在消费资产之前。

## 6. 修复后 dbt Build

修复脏数据并新增 2026-08-04 快照后：

```text
PASS=27 WARN=0 ERROR=0 SKIP=0 TOTAL=27
```

目标行数：

| 关系 | 行数 |
|---|---:|
| `it_demo_dwd_dim_date` | 730 |
| `it_demo_dwd_dim_org` | 3 |
| `it_demo_dwd_dim_project` | 2 |
| `it_demo_dwd_fct_task_snapshot` | 12 |
| `it_demo_dws_project_health` | 6 |
| `it_demo_ads_project_overview` | 6 |

## 7. 结论边界

本次验证证明：

- 三份源 SQL 可以在 PostgreSQL 17 执行。
- 6 个 dbt 模型可以真实构建。
- 标准码、引用、范围、金额和日期测试能够阻断脏数据。
- 修复后 FULL 重建可以恢复正确关系和行数。

本次验证不证明 DTS 平台链路已经打通。完整结论仍需按 `04-acceptance-checklist.md` 在实际 DTS 环境验证数据源、入湖、ModelSpec revision、物化候选、资产回写、血缘、权限、指标、BI/API 和审计。
