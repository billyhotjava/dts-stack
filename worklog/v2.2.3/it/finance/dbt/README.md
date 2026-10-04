# IT Finance Demo dbt 高级实现

本项目为财务 Demo 的 6 个 ModelSpec 提供高级 SQL/dbt 实现。它与项目健康 Demo 相互独立，但仍以 DTS 中的 ModelSpec ID 和 revision 为唯一逻辑模型台账，不创建平行模型。

## 目标节点

```text
model.it_fin_demo_governance.it_fin_demo_dwd_dim_date
model.it_fin_demo_governance.it_fin_demo_dwd_dim_cost_center
model.it_fin_demo_governance.it_fin_demo_dwd_dim_budget_account
model.it_fin_demo_governance.it_fin_demo_dwd_fct_budget_snapshot
model.it_fin_demo_governance.it_fin_demo_dws_cost_center_budget
model.it_fin_demo_governance.it_fin_demo_ads_finance_overview
```

每个节点必须绑定 DTS 中对应 ModelSpec 的精确 revision。

## 源表

默认从 `public` Schema 读取：

- `ods_it_fin_demo_cost_center`
- `ods_it_fin_demo_budget_account`
- `ods_it_fin_demo_budget_snapshot`

如果 ODS 位于其他 Schema：

```bash
dbt build --vars '{"it_fin_demo_ods_schema": "your_schema"}'
```

## 本地编译和运行

使用 DTS 运行时 profile，不在本目录保存凭据：

```bash
dbt parse
dbt compile
dbt test --select source:it_fin_demo_ods
dbt build --select tag:it-fin-demo
```

`dbt build` 前必须已经存在三张财务 Demo ODS。不得提交 `profiles.yml`、密码、`target/`、`logs/` 或其他运行产物。

## 设计说明

- 所有模型使用 table/FULL，保证删除脏数据后可完整恢复。
- 成本中心和预算科目采用 TYPE1；周期历史保存在预算执行快照事实。
- 预算科目类别和管控类型从原始别名收敛为稳定 ASCII 标准码。
- 未识别值映射为 UNKNOWN，但阻断级白名单测试不接受 UNKNOWN。
- `committed_amount` 只表示尚未转为实际发生额的承诺金额，避免与 `actual_amount` 重复计算。
- BI、API 和数据产品只能消费 ADS 财务概览。
