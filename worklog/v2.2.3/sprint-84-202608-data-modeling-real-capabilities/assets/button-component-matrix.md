# 按钮与组件矩阵

| 控件 | Owner component | 用户意图 | 真实契约 | 当前 | 完成条件 |
|---|---|---|---|---|---|
| 概览新建模型 | `HomeWorkspace` | 开始建模 | 跳转并消费 ModelSpec 工作台创建态 | CODE_COMPLETE | 部署后验证权限/返回 |
| 规划新建/编辑/归档 | `PlanningWorkspace` | 维护规划上下文 | WarehousePlan/CatalogDomain/planning adapter | CODE_COMPLETE | 部署后验证 CAS/审计 |
| 标准新建/编辑/导入 | `StandardsWorkspace` | 治理标准 | Modeling/MetadataStandard/Package/Reference APIs | CODE_COMPLETE | 部署后验证 preview/apply |
| 模型新建/保存/提交 | `DimensionalModelingWorkspace` / `ModelingEditor` | 创建修订并推进阶段 | v2 原子组合命令 + ModelSpec Gate/lifecycle | CODE_COMPLETE | 部署后验证冲突/恢复/审计 |
| 发布/物化 | `ModelingDialogs` | 发布并构建 | publish-intent/build-intent；禁止误接无 route 的旧 lifecycle client | CODE_COMPLETE | 联合 Sprint-83 验证 candidate/run/correlation |
| 高级 dbt / 物理预览 / ZIP 导入 | model-detail / `ReverseModelingWizard` | 技术维护、观测、导入 | Sprint-83 既有 API | CODE_COMPLETE | 联合 Sprint-83 最终验收 |
| 指标新建/保存/发布 | `MetricsWorkspace` / `MetricEditor` | 治理指标 | Governance Indicator APIs | CODE_COMPLETE | 部署后验证版本/引用/审计 |
| 图刷新/筛选/跳转 | `RelationshipGraphWorkspace` | 查看影响与依赖 | planning graph + refs + indicator deps | CODE_COMPLETE | 部署后验证真实图 |
| 工具卡与历史 | `ToolsWorkspace` | 打开受控导入/检查流程 | 各流程 owner 路由/run/history | CODE_COMPLETE | 部署后验证深链 |
| `UiStageNotice` / `BackendPendingButton` | `WorkspacePage` | 实施占位 | 无 | REMOVED | 保持生产路径零消费者 |
