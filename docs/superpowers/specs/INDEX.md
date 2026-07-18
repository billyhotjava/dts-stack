# Spec 索引与登记协议

本目录是设计 spec 的唯一存放地；`worklog/` 只放 sprint 执行文档并链接到此处。

## 登记协议（所有 agent 与人工必须遵守）

1. 新 spec 头部必须含：**状态**（ACTIVE / SUPERSEDED by X / IMPLEMENTED / ARCHIVED）与**取代关系**（取代谁、被谁取代、继承哪些部分）。
2. 新 spec 创建或状态变化时，**同步更新本索引**。
3. 对应 sprint 的 README 必须链接权威 spec；实施完成后状态改 IMPLEMENTED。
4. 取代他人 spec 时，须同时在被取代文件头部加 SUPERSEDED 横幅——单文件可独立读懂自己的地位。

## 建模域现行家谱（2026-07-18）

| Spec | 主题 | 层 | 状态 |
|---|---|---|---|
| 2026-07-18-classic-warehouse-planning-golden-path-design | 经典数仓规划内核、双起点、黄金主线（Sprint-65 总体设计） | 全栈架构 | **ACTIVE（权威）** |
| 2026-07-17-generic-modeling-workbench-ui-design | 四阶段通用建模工作台 | 前端 IA | SUPERSEDED by 07-18（通用内核 §4 与状态真实性 §10 被继承） |
| 2026-07-17-generic-modeling-template-boundary-design | 通用内核 vs 行业模板边界、PJM 模板化 | 后端不变量 | ACTIVE（不受 07-18 影响） |
| 2026-07-17-domain-modeling-candidate-review-design | 模板候选确认闭环 | 后端 API + UI | 后台不变量 ACTIVE；UI 部分 SUPERSEDED by 07-17-workbench-ui（连锁至 07-18） |
| 2026-07-11-warehouse-planning-standard-dimension-loop-design | 规划-标准-维度闭环（Sprint-63 蓝本） | 前端 UI | IMPLEMENTED；骨架决策已被 07-17/07-18 演进覆盖 |

## 其他历史 spec

| Spec | 状态 |
|---|---|
| 2026-07-03-metric-workbench-workflow-editor-design | IMPLEMENTED |
| 2026-06-16-workbench-home-personalization-design | IMPLEMENTED |
| 2026-05-17-dts-opmanager-independent-engine-design | IMPLEMENTED |
| 2026-05-09-dts-metric-visualization-center-refactor-design | IMPLEMENTED |
| 2026-04-28-upgrade-lite-target-path-contract-design | IMPLEMENTED |
| 2026-04-24-platform-workbench-leader-overview-design | IMPLEMENTED |
| 2026-04-01-screen-jump-picker-design | IMPLEMENTED |
| 2026-03-31-analytics-styling-unification-design | IMPLEMENTED |
| 2026-03-30-screen-permission-refactor-design | IMPLEMENTED |
| 2026-03-29-screen-permission-design | IMPLEMENTED |
| 2026-03-28-unified-asset-permission-design | IMPLEMENTED |
| 2026-03-28-opadmin-password-login-design | IMPLEMENTED |
| 2026-03-27-notebook-editor-join-design | IMPLEMENTED |

历史 spec 状态为归档判断，如与现实不符以代码与 sprint 证据为准，可随时修订本表。
