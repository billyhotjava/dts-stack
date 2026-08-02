# 页面能力矩阵

| 页面 | 路由 | 数据/动作事实 | 当前评级 | 目标 |
|---|---|---|---|---|
| 建模概览 | `/data-modeling/home/workspace` | WarehousePlan/ModelSpec/标准/指标真实并发投影；创建深链已消费 | CODE_COMPLETE | 部署后验证真实租户空/错/成功 |
| 数仓规划 | `/data-modeling/planning/**` | 计划、域、过程、层级、DataMart、策略来自真实 owner；无 owner 项诚实空态 | CODE_COMPLETE | 部署后验证 CAS、权限与审计 |
| 数据标准 | `/data-modeling/standards/**` | 标准、元数据标准、标准包、码表、术语真实读写；字段逐项保真 | CODE_COMPLETE | 部署后验证导入与模型字段重载 |
| 维度建模 | `/data-modeling/dimensions/**` | ModelSpec、维度定义、原子创建/恢复、dbt 表示、预览、ZIP 与 release intent 已接 canonical owner | CODE_COMPLETE | 联合 Sprint-83 做发布/物化 E2E |
| 数据指标 | `/data-modeling/metrics/**` | Governance Indicator 目录、编辑、校验、发布、版本与引用真实化 | CODE_COMPLETE | 部署后验证真实数据集与审计 |
| 通用工具 | `/data-modeling/tools/**` | 仅保留 dbt/标准/码表/lineage/审计真实流程入口；无 owner 历史不伪造 | CODE_COMPLETE | 部署后验证深链权限 |
| 关系图 | `/data-modeling/graphs/**` | WarehousePlan 关系投影、标准/指标关系、筛选、分页与安全深链真实化 | CODE_COMPLETE | 部署后验证真实图与视口 |

`CODE_COMPLETE` 只表示源码、聚焦测试、独立 Review 与 Chrome 95 构建通过；最终 E2E 前不得提升为 `REAL/DELIVERED`。
