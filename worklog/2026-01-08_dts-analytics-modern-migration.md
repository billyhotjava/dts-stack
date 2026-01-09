# 2026-01-08 — dts-analytics modern webapp 迁移计划（方案1）

目标：用 `source/dts-analytics-webapp/modern`（React19/Vite）逐步替换 `source/dts-analytics-webapp/legacy`（Metabase UI），最终做到不依赖 legacy UI。

功能点清单（对齐/验收基线）：`worklog/2026-01-09_dts-analytics-feature-checklist.md`

## 现状

- modern UI（默认）：`https://bi.iae.caep/analytics`（新 React19/Vite 工程，已接入 Traefik）。
- legacy UI（临时对照）：`https://bi.iae.caep/analytics/legacy`（Metabase v0.58.x 解压产物 + Node 注入 bootstrap/auth bridge；待删除）。
- 后端：`dts-analytics` 提供 Metabase 风格 API（`/analytics/api/**`）。

## 迁移原则

- 继续走 embedded：用户只在 `dts-platform` 登录一次；analytics 不做二次登录 UI。
- modern 只通过 `dts-analytics` 的 `/analytics/api/**` 交互；避免引入 Metabase/cljs 源码依赖。
- 语言仅保留：中文（默认）+ 英文。
- Chrome 98 兼容：Vite build target 固定 `chrome98`。

## TODO（按里程碑拆分）

### M1：可用的“浏览 + 查询预览”（1~2 周）

- 路由与布局
  - [ ] Top/Side layout（对齐 dts-admin-webapp 的体验规范）
  - [ ] 统一导航与面包屑（可选）
  - [ ] 错误页/404/403
- 鉴权与会话（embedded）
  - [ ] 统一 `fetch`/API client：自动携带 cookie、标准错误提示、traceId 展示
  - [ ] 处理 `platform-forward-auth` 失效时的兜底页面（提示回到 platform 登录）
- 集合（Collections）
  - [x] 列表（`GET /api/collection`）
  - [x] 集合内容（`GET /api/collection/{id}/items`）
  - [ ] 新建/重命名/归档/移动/权限（按后端支持逐步补）
- 仪表盘（Dashboards）
  - [x] 列表（`GET /api/dashboard`）
  - [x] 详情 JSON（`GET /api/dashboard/{id}`）
  - [ ] 详情渲染：Dashcard 布局、参数面板、过滤联动（优先只读）
- 问题/卡片（Questions/Cards）
  - [x] 列表（`GET /api/card`）
  - [x] 详情 JSON（`GET /api/card/{id}`）
  - [x] 查询结果 JSON（`POST /api/card/{id}/query`）
  - [ ] 结果表格渲染（columns/rows/分页/下载）

### M2：核心“可视化渲染 + 只读仪表盘”（2~4 周）

- 可视化
  - [ ] Table/Bar/Line/Pie（最小集合）渲染（基于后端返回的 result + visualization_settings）
  - [ ] 数字格式/时间格式/单位/颜色主题
- 仪表盘只读
  - [ ] dashcard 容器（大小/位置/响应式）
  - [ ] 参数（parameters）→ 请求 query 参数注入
  - [ ] 收藏/最近访问（bookmark/activity）

### M3：编辑能力（4~8 周）

- Query Builder（核心难点）
  - [ ] 数据源选择（database/table/field）
  - [ ] 过滤/分组/排序/聚合（MBQL 模型）
  - [ ] 预览 SQL（如果后端提供）
  - [ ] 保存为 card / 加入 dashboard
- Dashboard 编辑
  - [ ] 添加/移动/调整 dashcard
  - [ ] 参数编辑、过滤联动配置
- Revision/历史
  - [ ] Revision 列表与回滚（如后端支持）

### M4：管理与企业级功能（并行）

- Admin/Settings
  - [ ] 用户/组/权限（与 platform 融合的最终形态）
  - [ ] 数据库连接管理、驱动、加密参数
  - [ ] 审计日志（login-history / activity）
- 企业级扩展
  - [ ] 报表发布/订阅（pulse/alert）
  - [ ] 多租户/组织隔离（如果平台需要）
  - [ ] 数据权限（行列级策略，若平台已有则复用）

## 风险与决策点

- Metabase UI（legacy）源码包含 `cljs/cljc`（source maps 可见）。为了最终“无 clojure 代码”，modern 不能基于这些源码直接引入。
- Query Builder 与可视化渲染是迁移的核心工作量：建议先保证“只读可用”，再补齐编辑。
