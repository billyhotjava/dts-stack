# 财务 Demo 本地制品验证报告

## 1. 结论

2026-07-29 在一次性 PostgreSQL 17 和 DTS dbt 镜像中完成了财务 Demo 的基线、脏数据阻断和修复恢复验证：

| 阶段 | dbt 结果 | 下游结果 |
|---|---|---|
| 基线 | PASS=29，WARN=0，ERROR=0，SKIP=0 | DWS/ADS 各 4 行 |
| 脏数据 | PASS=14，ERROR=8，SKIP=7 | DWS/ADS 被跳过 |
| 修复增量 | PASS=29，WARN=0，ERROR=0，SKIP=0 | DWS/ADS 各 6 行 |

此外，基线 ODS source tests 为 PASS=8/8。结果证明本目录 SQL 和 dbt 制品能够实现预期质量阻断及恢复；它不等同于当前 DTS 租户的 UI、调度、权限或物化验收。

## 2. 验证环境

| 项目 | 值 |
|---|---|
| 日期/时区 | 2026-07-29 / Asia/Shanghai |
| PostgreSQL | `postgres:17-alpine` 一次性容器 |
| dbt 镜像 | `dts-dbt:1.10.0` |
| 镜像内 dbt | 1.11.3 |
| PostgreSQL adapter | 1.10.0 |
| 本机静态解析 | dbt-fusion 2.0.0-preview.175 |
| 持久化 | 无宿主数据库卷 |

验证完成后，数据库容器、专用 Docker network 和临时 `profiles.yml` 已删除。

## 3. 静态解析

本机使用临时、合成凭据 profile 执行：

```text
dbt parse
```

结果：成功。

临时 profile、`target/`、`logs/` 和 `.user.yml` 未保留在交付目录。

## 4. 基线阶段

### 4.1 源端与 ODS

执行 `sql/01-source-bootstrap.sql`，再以 FULL 方式复制为三张 ODS：

| 数据集 | 行数 |
|---|---:|
| 成本中心 | 3 |
| 预算科目 | 4 |
| 预算执行快照 | 8 |

ODS source tests：

```text
PASS=8 WARN=0 ERROR=0 SKIP=0 TOTAL=8
```

### 4.2 dbt build

```text
Found 6 models, 31 data tests, 3 sources
Finished running 6 table models, 23 selected data tests
PASS=29 WARN=0 ERROR=0 SKIP=0 TOTAL=29
```

输出：

| 模型 | 行数 |
|---|---:|
| `it_fin_demo_dwd_dim_date` | 730 |
| `it_fin_demo_dwd_dim_cost_center` | 3 |
| `it_fin_demo_dwd_dim_budget_account` | 4 |
| `it_fin_demo_dwd_fct_budget_snapshot` | 8 |
| `it_fin_demo_dws_cost_center_budget` | 4 |
| `it_fin_demo_ads_finance_overview` | 4 |

抽样口径：

| 快照日 | 成本中心 | 执行率 | 占用率 | 应付比 | 健康 |
|---|---|---:|---:|---:|---|
| 2026-06-30 | CC-QA | 0.3714 | 0.5571 | 0.2885 | BH-GREEN |
| 2026-06-30 | CC-RD | 0.3538 | 0.5462 | 0.1522 | BH-RED |
| 2026-07-28 | CC-QA | 0.5571 | 0.7429 | 0.3590 | BH-RED |
| 2026-07-28 | CC-RD | 0.5077 | 0.6692 | 0.1364 | BH-RED |

## 5. 脏数据阻断阶段

执行 `sql/02-source-dirty-cases.sql`，FULL 刷新 ODS 后行数为 3/4/9。

dbt build 结果：

```text
PASS=14 WARN=0 ERROR=8 SKIP=7 TOTAL=29
exit=1
```

8 个失败测试：

1. 科目类别标准码白名单。
2. 管控类型标准码白名单。
3. 金额非负。
4. 预算金额大于 0。
5. 预测最终额不小于实际。
6. 应付金额不大于实际。
7. 财年与快照日期一致。
8. 成本中心主数据引用。

`it_fin_demo_dws_cost_center_budget`、`it_fin_demo_ads_finance_overview` 及其测试共 7 个节点被 SKIP，符合阻断预期。数据库中上一次基线构建的 DWS/ADS 表仍作为 last-good 存在；失败批次没有刷新它们。

## 6. 修复与增量阶段

执行 `sql/03-source-remediation-and-increment.sql`，FULL 刷新 ODS 后：

```text
成本中心       3
预算科目       4
预算执行快照  12
```

dbt build：

```text
PASS=29 WARN=0 ERROR=0 SKIP=0 TOTAL=29
```

输出行数：

| 模型 | 行数 |
|---|---:|
| 日期维度 | 730 |
| 成本中心维度 | 3 |
| 预算科目维度 | 4 |
| 预算快照事实 | 12 |
| DWS | 6 |
| ADS | 6 |

新增快照抽样：

| 快照日 | 成本中心 | 名称 | 执行率 | 占用率 | 应付比 | 健康 |
|---|---|---|---:|---:|---:|---|
| 2026-08-04 | CC-QA | 质量费用中心 | 0.6000 | 0.7571 | 0.3571 | BH-AMBER |
| 2026-08-04 | CC-RD | 研发与创新费用中心 | 0.6154 | 0.8077 | 0.1250 | BH-GREEN |

预算科目别名验证：

```text
BA-SERVICE
  服务费 → BAC-SERVICE
  软管控 → BCT-SOFT
```

## 7. 尚未由本报告证明

- DTS 真实数据源连接、Schema 探测和入湖 UI。
- 业务域、标准、维度和 ModelSpec 在指定租户的保存及 revision。
- Airflow/dbt 平台物化链路成功。
- 质量规则绑定执行、指标发布、血缘同步。
- BI、API、数据产品和 read/write/export 权限。
- 浏览器兼容、审计和自动验收包。

这些项目必须由测试同事按 `03-ui-runbook.md` 和 `04-acceptance-checklist.md` 在目标 DTS 环境留证。
