# 按钮与组件验收矩阵

| 页面 | 控件 | 可见/可用条件 | 操作契约 | 成功反馈 | 失败/禁用反馈 | 自动化证据 |
|---|---|---|---|---|---|---|
| BI 数据集 | 搜索/领域/owner/分层/密级筛选 | 有 read | 更新 URL；重置 page=0；请求 published projection | 结果数与筛选 chip | 请求失败保留筛选，可重试 | UI contract + Playwright |
| 全部列表页 | 分页与每页条数 | 恒可用 | UI 默认每页 10 条；切换条数重新拉取并重置第 1 页 | 页码与总数一致 | 请求失败保留当前页 | 分页 contract test |
| BI 数据集 | 查看契约 | 有 read；数据集 PUBLISHED | 打开只读 drawer，显示 version/checksum/字段/指标/策略摘要 | drawer 与列表版本一致 | 归档/漂移提示，不泄露 SQL | API schema + E2E |
| BI 数据集 | 创建分析 | 有 write；PUBLISHED；默认 DWS/ADS | 导航 `/bi/questions/new?datasetId=&version=&checksum=`（ADR-94-13） | 编辑器显示钉定摘要 | DWD 无高级授权禁用；ODS/STG 禁止 | route contract + E2E |
| 分析列表 | 新建分析 | 有 write | 先选数据集或进入 `/bi/data` | 创建 DRAFT | 无可用数据集显示引导 | E2E |
| 分析列表 | 复制 | 有 write；源资产可读 | 复制 spec 为新 DRAFT，不复制受众/发布指针 | 新草稿打开 | legacy-invalid 禁用 | service IT |
| 分析列表 | 归档/恢复 | owner/write；非发布中 | 状态 DRAFT/PUBLISHED → ARCHIVED；恢复只回 DRAFT | 列表筛选更新 | 有被发布看板引用则阻断并列依赖 | state-machine IT |
| 分析编辑器 | 字段/指标拖放或选择 | contract 中允许字段 | 只修改本地 v1 spec，不立即执行 | 配置区与 preview dirty 状态 | policy/类型不兼容即时说明 | component test |
| 分析编辑器 | 预览 | spec 基本合法；read | `POST /api/analysis/query`，limit 使用 preview 上限 | 表格/图表、rowCount、duration、truncated | 403/409/422/429/504 分型反馈 | gateway IT + E2E |
| 分析编辑器 | 保存 | write；有未保存变更 | PUT/POST Analysis，支持 idempotency key | “已保存”时间，仍为 DRAFT | 409 乐观锁提示刷新/另存 | service IT + E2E |
| 分析编辑器 | 校验 | write；已保存 | `/validate`，不发布 | blockers/warnings 分组并定位字段 | 失败不改变 lifecycle | validate IT |
| 分析编辑器 | 发布 | 独立发布资格；validate valid | `/publish`，钉定 revision 和 dataset contract | PUBLISHED badge/version | blocker、受众缺失或契约漂移保持旧 published | concurrent publish IT |
| 分析编辑器 | 版本历史/回退 | read；回退需 write | 列 revision；回退产生新 DRAFT，不覆盖历史 | 新 draft version | 不允许直接修改已发布 revision | revision IT |
| 看板设计 | 添加分析 | write；至少一个 PUBLISHED Analysis | 选择 analysisId+revisionId | 组件显示版本和健康 | 草稿分析不可选；失效依赖标红 | component/E2E |
| 看板设计 | 参数映射 | write；字段类型兼容 | 参数映射保存到 dashboard draft | 预览联动 | 类型冲突阻断校验 | validation IT |
| 看板设计 | 保存/校验/发布 | 同分析职责分离 | 分别调用 save/validate/publish | 发布版本+受众摘要 | 任何依赖 blocker 不推进 current pointer | state-machine IT |
| 发布弹窗 | 部门/角色/密级/有效期 | 发布资格；密级不低于依赖 | 保存到平台 registration request | 同步成功/版本号 | 平台不可用保持未发布或待 reconcile，不假成功 | cross-service IT |
| 消费页 | 导出 | `export` + read + 当前受众 | 独立 export endpoint 和预算 | 文件名含资产/版本/时间 | 无 export 403；超限明确 | auth IT |

大屏继续作为需保留的历史资产；旧 BI 兼容控件不再演进。大屏保留与旧 BI 清理验收由 F0/T04、F6/T03 负责。

## 组件拆分约束

- `SemanticCardEditorPage.tsx`（1137 行）：先抽取 `AnalysisDatasetSummary`、`AnalysisFieldPanel`、`AnalysisConfigPanel`、`AnalysisPreviewCanvas`、`AnalysisValidationPanel`；原文件不得继续增长。**该文件是 5 条路由的共用宿主（含 VDS 两条），拆分前必须先补冻结路由回归测试**（ADR-94-14）。
- `analyticsApi.ts`（2483 行）：按 `analysisClient`、`dashboardClient` 拆分；R1 保留兼容导出 facade，R2 删除无调用旧 BI client；`screenClient` 永久保留。
- `ScreenResource.java`（3117 行）：本 Sprint 不改动；大屏 owner 与旧 BI Contract 清理分离。
- 状态、权限和按钮可用性来自后端 DTO/capability，不在多个页面复制角色字符串判断。
