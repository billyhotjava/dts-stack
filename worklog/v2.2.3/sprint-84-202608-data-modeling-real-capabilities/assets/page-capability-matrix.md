# 页面能力矩阵

| 页面 | 路由 | 数据/动作事实 | 当前评级 | 目标 |
|---|---|---|---|---|
| 建模概览 | `/data-modeling/home/workspace` | 并发投影 WarehousePlan、ModelSpec、MetadataStandard 和 Indicator | CODE_COMPLETE | 部署后核对空/错状态与真实数量 |
| 数仓规划 | `/data-modeling/planning/**` | 逐叶消费 WarehousePlan/Catalog owner；无 owner 能力显式禁用并说明 | CODE_COMPLETE_WITH_OWNER_GAPS | 不共享建设计划模块，部署后验收 CRUD |
| 数据标准 | `/data-modeling/standards/**` | 标准/码表/词典使用真实 owner；映射与词根按当前 owner 能力只读/禁用 | CODE_COMPLETE_WITH_OWNER_GAPS | 部署后验收权限、CRUD 和导入 |
| 维度建模 | `/data-modeling/dimensions/**` | 对象树 + 单页编辑器已接 ModelSpec/表示/release/build/preview；逆向建模已接 ZIP 主链 | CODE_COMPLETE | 部署后验收保存、发布、物化和部分成功 |
| 数据指标 | `/data-modeling/metrics/**` | 指标目录、校验、保存、发布和归档使用 Governance Indicator owner | CODE_COMPLETE | 部署后验收编辑与发布 |
| 通用工具 | `/data-modeling/tools/**` | 仅保留有真实 owner 的深链；不伪造统一运行历史 | CODE_COMPLETE_WITH_OWNER_GAPS | 部署后验收深链与空态 |
| 关系图 | `/data-modeling/graphs/**` | 投影 WarehousePlan relationship graph，支持搜索、缩放和真实节点深链 | CODE_COMPLETE | 部署后验收节点定位与跨模块跳转 |

`CODE_COMPLETE` 只表示源码、聚焦测试与 Chrome 95 target 构建通过；`CODE_COMPLETE_WITH_OWNER_GAPS` 还表示页面已诚实暴露服务端尚无 canonical owner 的能力边界。当前制品尚未部署，最终 E2E 前不得提升为 `REAL/DELIVERED`。
