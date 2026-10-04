# P1-07 自助分析会话（Explore Session）

`status`: `done`  
`priority`: `P1`  
`inspiration`: `Hetu(分析闭环) + Superset Explore(探索路径) + Metabase(提问历史)`

## 目标

把一次性查询升级为“可保存、可复盘、可分享”的分析会话，形成“问题 -> 探索 -> 结论”的可追溯闭环。

## 子任务

1. 会话模型
- 新增 `ExploreSession`：问题、步骤、中间图、结论、标签、归档状态。
- 每一步记录输入参数、筛选上下文、组件快照引用。

2. 会话 API
- 新增会话 CRUD、步骤追加、结论标注、归档/复制接口。
- 支持按用户/部门/项目维度检索与分页。

3. 前端会话面板
- 新增“分析会话”侧栏：时间线、关键步骤、结论摘要。
- 支持从当前大屏一键“保存为会话”并继续编辑。

4. 复盘与重放
- 支持按步骤重放筛选与组件状态（最小可用版）。
- 重放失败提供差异说明（数据已变更/组件已删除）。

5. 分享与权限
- 会话继承大屏 ACL，支持只读分享链接。
- 会话访问与修改纳入审计日志。

## 验收标准

- 用户可在单次分析后保存完整会话并在后续继续编辑。
- 会话至少支持 20 步操作记录且可稳定重放。
- 会话分享链接可被权限系统正确拦截。

## 风险与回滚

- 风险：会话数据快速膨胀影响查询性能。  
- 回滚：先保留“关键步骤快照”，原始明细按 TTL 自动清理。

## 实现难度评估

- 难度：`中`
- 预计周期：`1.5 ~ 2 周`
- 关键难点：
  - 会话重放与实时数据变化的一致性处理；
  - 会话权限与大屏权限口径保持一致。

## 前置依赖

- `P0-04-release-acl-audit-sharing.md`
- `P1-02-global-filter-interaction-engine.md`
- `P1-06-analysis-explainability-layer.md`

## 实现记录（2026-02-22）

- 新增 `ExploreSession` 数据模型（问题、步骤、结论、标签、归档、项目/部门维度）：
  - 数据表：`analytics_explore_session`
  - 代码：`source/dts-analytics/src/main/resources/config/liquibase/changelog/0032_explore_sessions.xml`、`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/domain/AnalyticsExploreSession.java`。
- 新增会话 API：
  - CRUD、步骤追加、步骤重放、归档、复制、公共链接创建/删除/访问。
  - 代码：`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ExploreSessionService.java`、`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ExploreSessionResource.java`。
- 前端 SDK 已接入：
  - `list/create/update/append/replay/archive/clone/public-link` 全量方法。
  - 代码：`source/dts-analytics-webapp/modern/src/api/analyticsApi.ts`。
- 前端会话中心已落地（2026-02-22）：
  - 新增页面：`source/dts-analytics-webapp/modern/src/pages/ExploreSessionsPage.tsx`；
  - 新增路由：`/analytics/explore-sessions`；
  - 新增导航入口：侧边栏“分析会话”；
  - 能力覆盖：会话创建、列表筛选（含归档）、步骤追加、步骤重放、结论更新、复制、归档、公共分享链接复制。
- 大屏设计器一键沉淀会话（2026-02-22）：
  - `ScreenHeader` 设计菜单新增“沉淀会话”动作；
  - 按当前大屏快照自动生成会话步骤（屏幕元信息 + 关键组件概览）；
  - 可选跳转到会话中心继续复盘；
  - 代码：`source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenHeader.tsx`。
