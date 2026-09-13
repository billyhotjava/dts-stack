# 页面能力矩阵

| 路由 | 页面 owner | 本 Sprint 能力 | API | 状态 |
|---|---|---|---|---|
| `/bi/data` | `DataPage.tsx` | 继续作为已发布数据集唯一创建入口 | published QueryDataset | 复用 |
| `/bi/questions/:id/edit` | `AnalysisEditorPage.tsx` + 新 workspace 子组件 | 字段货架、实时图表、样式、计算、发布、导出 | Analysis API | IN_PROGRESS |
| `/bi/dashboards/:id/edit` | `DashboardEditorPage.tsx` + dashboard 子组件 | 参数映射、定向联动、发布消费 | Dashboard API | READY |
| `/bi/questions/:id` | `CardDetailPage.tsx` | 发布后只读消费 | Analysis/Card query | 回归 |

无新增页面、菜单、路由或重定向。
