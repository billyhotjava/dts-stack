# 2026-01-09 — dts-analytics（modern 替代 legacy）功能点清单

目标：以 `source/dts-analytics-webapp/modern`（React 19/Vite，Chrome98 target）**全面替代** `source/dts-analytics-webapp/legacy`（Metabase UI 静态产物），完成后删除 legacy（代码/路由/运维配置）。

约束与约定：
- 只支持两种语言：中文（默认）+ 英文。
- embedded：用户只在 `dts-platform` 登录一次；analytics 不提供二次登录 UI，不走 metabase bootstrap 向导。
- 前端仅通过 `dts-analytics` 的 `/analytics/api/**` 交互（松耦合），不依赖 metabase.jar 或 cljs 源码。

## 1) 功能盘点方法（如何确保不漏）

1. 以 Java 重写后端为准：扫描 `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/*` 的 REST 资源，作为“应实现能力边界”。
2. 以 legacy UI 为验收基线：对 `source/dts-analytics-webapp/legacy/frontend_client` 的 bundle 扫描字符串（见 `source/dts-analytics-webapp/scripts/scan-legacy-api.sh` 输出 `source/dts-analytics-webapp/legacy/api-endpoints.txt`），再配合浏览器 Network 行为验证。
3. 以 Metabase v0.58.x 的模块结构做“检查表”校验：Browse/Collections、Questions、Dashboards、Data、Admin、Sharing/Embedding、Alerts/Pulses、Activity/Recents 等。

## 2) P0（必须交付：替代 legacy 的最小闭环）

### P0-Auth：平台一体化（embedded）
- [x] `dts-analytics-webapp/modern` 访问 `/analytics` 不出现二次登录 UI（依赖 `platform-forward-auth@file` 正常工作）。
- [ ] 401/403 统一处理：提示“回到 platform 重新登录/刷新 token”，并提供跳转链接。
- [ ] API 访问统一封装（cookie + X-Request-Id 透传/展示）。
- [x] `/analytics/api/user/current`：现代端基于该接口判断“已登录/未登录”。

相关后端：
- `GET /api/user/current`（`UserResource`）
- `GET /api/session/properties`（`SessionPropertiesResource`）

### P0-Nav：基础框架与路由
- [x] `/analytics` 默认进入 modern（legacy 临时对照路径：`/analytics/legacy`）。
- [x] App Layout：侧边栏导航（Collections/Dashboards/Questions/Search）。
- [ ] 404/错误页（最少 404 + 未登录提示页）。
- [ ] 语言切换（zh/en）与默认 zh（不引入第三语言资源）。

### P0-Collections：集合浏览
- [x] Collection 列表（含 root 概念）。
- [x] Collection items：展示 dashboard/card，支持点击进入详情。
- [ ] 最少支持：只读浏览、分页/排序（如后端支持）。

相关后端：
- `GET /api/collection`（`CollectionResource`）
- `GET /api/collection/{id}/items`（`CollectionResource`）

### P0-Dashboards：仪表盘只读浏览
- [x] Dashboard 列表。
- [x] Dashboard 详情页：展示 dashcards 列表（card 名称/跳转）；可视化渲染后续补齐。
- [ ] 参数（parameters）信息展示（先只读）。

相关后端：
- `GET /api/dashboard`（`DashboardResource`）
- `GET /api/dashboard/{id}`（`DashboardResource`）

### P0-Questions：卡片（问题）浏览 + 执行
- [x] Card 列表。
- [x] Card 详情页：展示 dataset_query / visualization_settings 基本信息（先只读）。
- [x] Card 执行：`POST /api/card/{id}/query`，渲染结果表格（cols/rows），展示 SQL（native_form.query）。
- [ ] 错误处理：SQL 执行错误也要可见（返回 accepted + error 字段时）。

相关后端：
- `GET /api/card`、`GET /api/card/{id}`、`POST /api/card/{id}/query`（`CardResource`）

### P0-Search：全局搜索（至少按名称）
- [x] 搜索页（query 输入 → results 列表 → 进入 dashboard/card/collection）。

相关后端：
- `GET /api/search`（`SearchResource`）

### P0-Health：冒烟与可观测
- [ ] `/analytics/api/health` 可用（浏览器可见的健康状态）。
- [ ] modern 页面提供“后端健康/当前用户”小面板（仅 dev 或隐藏入口）。

相关后端：
- `GET /api/health`（`HealthResource`）
- `GET /api/info`（`InfoResource`）

## 3) P1（核心体验：只读可视化 + 常用编辑）

### P1-Viz：可视化渲染（最小集合）
- [ ] Table（P0 已做）→ 增强：分页/下载 CSV（若后端支持）。
- [ ] 基础图表：Bar/Line/Pie（基于 query 返回 + visualization_settings）。
- [ ] 数字/日期格式化（基于 results_metadata.columns.fingerprint / base_type）。

### P1-Dashboard：只读渲染完善
- [ ] dashcard 布局（row/col/size_x/size_y）→ 简化响应式网格渲染。
- [ ] 参数面板（dashboard.parameters）与 parameter_mappings 注入 query 请求（按后端 query API 支持逐步补齐）。

### P1-Write：编辑能力（分阶段）
- [ ] 新建/编辑 Collection（名称/描述/归档/移动）。
- [ ] 新建/编辑 Dashboard（名称/描述/归档/复制）。
- [ ] Dashboard 编辑（添加/移动/删除 dashcard）。
- [ ] Card 保存（修改 name/collection/display/dataset_query）。

相关后端（现有实现）：
- `POST/PUT/DELETE /api/collection...`（`CollectionResource`）
- `POST/PUT/DELETE /api/dashboard...`（`DashboardResource`）
- `POST/PUT/DELETE /api/card...`（`CardResource`）

## 4) P2（管理与企业扩展：平台融合）

### P2-Data：数据浏览与元数据
- [ ] Databases 列表/详情（只读）。
- [ ] Tables/Fields 浏览（只读）。
- [ ] Datasets/Segments/Metrics（只读 → 编辑）。

相关后端：
- `DatabaseResource` / `TableResource` / `FieldResource` / `DatasetResource` / `SegmentResource` / `MetricResource`

### P2-Admin：用户/权限/设置
- [ ] 用户管理（只读/创建/禁用）。
- [ ] 权限图（permissions graph）与授权 UI（与 platform 策略对齐）。
- [ ] settings（站点名称/默认语言/邮件等）与平台配置的边界划分。

相关后端：
- `UserResource` / `PermissionsResource` / `SettingsResource` / `LoginHistoryResource`

### P2-Sharing：分享与嵌入
- [ ] dashboard/card public link（只读访问）。
- [ ] embed（iframe/分享链接）策略（与平台安全头兼容）。

相关后端：
- `PublicResource` / `EmbedResource`

### P2-Notifications：订阅/告警
- [ ] alert / pulse（创建、列表、发送测试）。
- [ ] email/slack 通道配置（如需要）。

相关后端：
- `AlertResource` / `PulseResource` / `EmailResource` / `SlackResource`

## 5) 删除 legacy 的收敛条件（Done Definition）

- modern 覆盖 P0 全部功能，并完成 P1-Viz + P1-Dashboard（只读渲染）到可用水平；
- 访问 `/analytics` 不再依赖 `source/dts-analytics-webapp/legacy`；
- Traefik/compose 中不再暴露 legacy router/service；
- 删除 `source/dts-analytics-webapp/legacy`、`source/dts-analytics-webapp/server.mjs`（如不再需要）及相关脚本/运维配置。
