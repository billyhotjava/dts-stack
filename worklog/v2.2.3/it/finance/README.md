# DTS 财务预算治理 Demo

[返回 Demo 分类入口](../README.md)

## 1. 给测试同事的入口

这是与现有“项目健康度”Demo 完全隔离的第二套完整 Demo，业务场景为“费用预算执行与预测”。测试同事可以从本目录独立开始，不需要先创建项目健康度对象。

最短执行路径：

1. 阅读 [01-architecture-and-scope.md](01-architecture-and-scope.md) 的边界和对象数量。
2. 在独立 PostgreSQL 源库运行 [sql/01-source-bootstrap.sql](sql/01-source-bootstrap.sql)。
3. 严格按 [03-ui-runbook.md](03-ui-runbook.md) 在 DTS 界面创建对象。
4. 用 [assets/model-field-matrix.csv](assets/model-field-matrix.csv) 录入 6 个模型字段。
5. 用 [assets/demo-object-register.csv](assets/demo-object-register.csv) 记录所有实际 ID、revision、run ID 和证据。
6. 按 [04-acceptance-checklist.md](04-acceptance-checklist.md) 完成基线、阻断、修复、权限和消费验收。

## 2. 最小规模

| 对象 | 数量 | 说明 |
|---|---:|---|
| 独立源 Schema | 1 | `it_fin_demo_src` |
| Demo 源表 | 3 | 成本中心、预算科目、预算执行快照 |
| Demo ODS | 3 | 统一前缀 `ods_it_fin_demo_` |
| 业务域/业务过程/数据集市 | 各 1 | 财务治理/预算执行监控/财务预算分析集市 |
| 业务维度定义 | 3 | 日期、成本中心、预算科目 |
| ModelSpec | 6 | 3 DIMENSION + 1 FACT + 1 SUMMARY + 1 APPLICATION |
| 公共码表 | 3 | 科目类别、管控类型、预算健康状态 |
| 质量规则 | 12 | 主数据、引用、值域、金额、会计期间与时效 |
| 治理指标 | 8 | 预算、承诺、实际、应付、预测、执行率、占用率、超支 |
| 消费出口 | 3 | BI、API、数据产品各 1 |

## 3. 数据链路

```text
it_fin_demo_src
  → ods_it_fin_demo_*
  → 3 个 DWD 维度 + 1 个 DWD 周期快照事实
  → 1 个 DWS 成本中心预算汇总
  → 1 个 ADS 财务预算概览
  → 指标 / BI / API / 数据产品
```

## 4. 目录

| 文件 | 用途 |
|---|---|
| [01-architecture-and-scope.md](01-architecture-and-scope.md) | 模块关系、隔离边界、对象所有权 |
| [02-data-governance-design.md](02-data-governance-design.md) | 主数据、标准、维度、模型、质量、指标完整设计 |
| [03-ui-runbook.md](03-ui-runbook.md) | 当前 DTS UI 逐页填写值和执行顺序 |
| [04-acceptance-checklist.md](04-acceptance-checklist.md) | 可勾选验收清单与期望结果 |
| [05-local-validation-report.md](05-local-validation-report.md) | 本地 PostgreSQL/dbt 制品验证结果 |
| [assets/model-field-matrix.csv](assets/model-field-matrix.csv) | 6 个模型逐字段录入矩阵 |
| [assets/demo-object-register.csv](assets/demo-object-register.csv) | 实施对象、状态、实际 ID 与证据登记 |
| [dbt/README.md](dbt/README.md) | 高级 dbt 实现说明 |
| [sql/01-source-bootstrap.sql](sql/01-source-bootstrap.sql) | 建源表并写入 3/4/8 行基线数据 |
| [sql/02-source-dirty-cases.sql](sql/02-source-dirty-cases.sql) | 注入标准码、引用和金额脏数据 |
| [sql/03-source-remediation-and-increment.sql](sql/03-source-remediation-and-increment.sql) | 修复并新增第三期快照 |
| [sql/99-source-cleanup.sql](sql/99-source-cleanup.sql) | 带确认口令的独立 Schema 清理 |
| [evidence/README.md](evidence/README.md) | 证据文件命名和内容要求 |

## 5. 隔离约束

- 不修改、不删除、不回填任何客户 ODS。
- 不复用项目健康 Demo 的源 Schema、ODS、模型、业务编码或指标编码。
- 财务 Demo 业务编码统一使用 `IT_FIN_`，标准编码使用 `IT-FIN-`，物理表使用 `it_fin_demo_`。
- 所有人员、部门和金额均为合成数据。
- 业务标签使用 `IT-FIN-DEMO`、`BUDGET-GOVERNANCE`；密级必须单独选择平台既有公开级。
- 清理脚本只允许删除 `it_fin_demo_src`。

## 6. 当前产品约束

- ModelSpec 是唯一逻辑模型台账；高级 dbt 节点只绑定同一 ModelSpec revision。
- 当前普通实现不能完整表达标准码归一、哈希键和聚合派生，因此本 Demo 使用 `dbt/` 高级实现。
- 当前权限矩阵仅能按 `read/write/export` 验收，不声称具备更细粒度动作。
- 模型物化链路当前仍有 profile lease 返回字段契约缺口；修复、部署并实测前，只能分别确认 UI 配置、dbt 本地制品和平台物化状态。
- 自动聚合验收包 API 尚未闭合，因此采用 `evidence/` 手工证据包。

本目录是实施与验收底稿，不代表当前租户已经创建这些对象。
