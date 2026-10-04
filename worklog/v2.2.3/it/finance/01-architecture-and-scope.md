# 财务 Demo 架构与范围

## 1. 业务目标

使用一套合成的费用预算数据，验证 DTS 从数据源接入到治理消费的完整关系：

```text
数据源与 Schema 探测
  → 入湖与 ODS
  → 元数据和资产目录
  → 业务域、业务过程、数据集市和建设计划
  → 术语、数据元、码表和单位
  → 维度定义和四类 ModelSpec
  → 质量阻断和修复恢复
  → 指标发布
  → 资产、血缘、权限、BI、API、数据产品
  → 运行与审计证据
```

Demo 只回答三个问题：

1. 预算执行数据能否形成稳定、可追溯、可质量管控的资产。
2. 同一财务口径能否由 ModelSpec、指标、BI、API 和数据产品一致复用。
3. 脏数据能否阻止错误模型进入发布和消费链路，并在修复后恢复。

## 2. 不在范围内

- 不接入客户 ERP、资金、总账、应付或采购系统。
- 不模拟会计凭证、借贷平衡、税务、结账、汇率或合并报表。
- 不使用客户预算台账或此前项目管理 ODS。
- 不把 Demo 健康状态作为真实财务审批或自动决策。
- 不改 DTS 产品源码、不新增页面、不新增模型台账。
- 不声称完成生产级高可用、细粒度权限或自动验收包。

## 3. 最小对象关系

| 层次 | 对象 | 所有权/用途 |
|---|---|---|
| 源端 | `it_fin_demo_src.cost_center` | 成本中心主数据 |
| 源端 | `it_fin_demo_src.budget_account` | 预算科目主数据及原始分类 |
| 源端 | `it_fin_demo_src.budget_execution_snapshot` | 周期预算执行快照 |
| ODS | `ods_it_fin_demo_cost_center` | DTS 接入副本 |
| ODS | `ods_it_fin_demo_budget_account` | DTS 接入副本 |
| ODS | `ods_it_fin_demo_budget_snapshot` | DTS 接入副本 |
| DWD | 3 个 DIMENSION | 日期、成本中心、预算科目 |
| DWD | 1 个 FACT | 预算明细周期快照 |
| DWS | 1 个 SUMMARY | 成本中心预算执行汇总 |
| ADS | 1 个 APPLICATION | 财务预算概览消费入口 |

三个源表已经是验证主数据、事实引用、标准码和金额规则所需的最小集合。继续减少会把成本中心或预算科目退化为事实表中的自由文本，无法验证主数据治理。

## 4. 粒度和历史

| 对象 | 粒度 | 历史策略 |
|---|---|---|
| 成本中心 | 一个成本中心一行 | TYPE1 |
| 预算科目 | 一个预算科目一行 | TYPE1 |
| 预算事实 | 一个预算明细在一个快照日期一行 | PERIODIC_SNAPSHOT |
| DWS/ADS | 一个成本中心在一个快照日期一行 | 由事实周期保留 |

成本中心更名只更新当前维度名称，历史金额变化由快照事实保留。这样既能验证 TYPE1，也不额外引入 SCD2 技术表。

## 5. 金额语义

为避免重复计算，字段含义固定如下：

| 字段 | 定义 |
|---|---|
| `budget_amount` | 财年调整后可控预算 |
| `committed_amount` | 已形成承诺、但尚未转为实际发生额的金额 |
| `actual_amount` | 已确认入账或验收的累计实际发生额 |
| `payable_amount` | 实际发生额中尚未支付的部分，是 `actual_amount` 的子集 |
| `occupied_amount` | `actual_amount + committed_amount` |
| `forecast_final_amount` | 财年结束时预计最终发生额 |
| `remaining_available_amount` | `budget_amount - actual_amount - committed_amount` |
| `overrun_amount` | `max(forecast_final_amount - budget_amount, 0)` |

由于 `payable_amount` 已包含在 `actual_amount` 中，任何汇总或指标都不得再次把应付加到实际发生额。

## 6. DTS 模块所有权

| 信息 | 唯一事实源 |
|---|---|
| 数据源连接和凭据 | 数据源/凭据控制面 |
| 物理表与字段 | 元数据目录 |
| 业务域、过程、集市 | 业务分类 |
| 术语、数据元、码表、单位 | 数据标准 |
| 维度语义 | 维度目录 |
| 模型逻辑、粒度、字段、revision | ModelSpec |
| SQL/dbt 实现 | 绑定 ModelSpec revision 的高级实现 |
| 指标口径和版本 | 指标工作台 |
| 密级 | SecurityLevelCatalog/密级控制面 |
| 业务分类标签 | 资产标签 |
| 消费 | ADS 资产对应的 BI/API/数据产品 |

ODS/STG 是物理接入和转换层，不创建为第五类 ModelSpec。高级 dbt 节点不能成为第二套逻辑模型台账。

## 7. 隔离和命名

| 范围 | 约定 |
|---|---|
| 源 Schema | `it_fin_demo_src` |
| ODS 表 | `ods_it_fin_demo_*` |
| 模型表 | `it_fin_demo_*` |
| 业务对象编码 | `IT_FIN_*` |
| 标准内容编码 | `IT-FIN-*` |
| 指标编码 | `IT_FIN_*` |
| dbt tag | `it-fin-demo` |

所有编码使用 ASCII。中文只用于名称和展示标签，不作为主键、外键或标准码。

## 8. 安全与权限

- 合成数据统一选择租户既有公开密级；若实际编码不是 `DATA_PUBLIC`，记录真实编码。
- `IT-FIN-DEMO` 和 `BUDGET-GOVERNANCE` 是业务标签，不能承载密级。
- 源端账号只允许访问 `it_fin_demo_src`。
- 凭据不得写入 SQL、CSV、Markdown、截图或 shell 历史。
- 只按当前 `read/write/export` 验证授权边界。
- API、BI 和数据产品不直接消费 ODS/DWD，默认只消费 ADS。

## 9. 三阶段测试

| 阶段 | 操作 | 源行数 | 期望 |
|---|---|---|---|
| 基线 | 运行 `01-source-bootstrap.sql` | 3 / 4 / 8 | 质量通过，DWS/ADS 各 4 行 |
| 阻断 | 运行 `02-source-dirty-cases.sql` | 3 / 4 / 9 | 标准码、引用、金额、期间规则失败，下游阻断 |
| 修复 | 运行 `03-source-remediation-and-increment.sql` | 3 / 4 / 12 | 质量恢复，DWS/ADS 各 6 行 |

三阶段都必须记录源批次、入湖 run ID、模型 run ID、质量结果、血缘和审计证据。
