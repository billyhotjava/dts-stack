# 2026-01-09 — dts-analytics（modern 替代 legacy）功能点清单（对齐 Metabase v0.58.1）

目标：以 `source/dts-analytics-webapp/modern`（React 19/Vite，Chrome98 target）**全面替代** `source/dts-analytics-webapp/legacy`（Metabase UI 静态产物，基准 Metabase `v0.58.1`），完成后删除 legacy（代码/路由/运维配置）。

约束与约定：
- 只支持两种语言：中文（默认）+ 英文。
- embedded：用户只在 `dts-platform` 登录一次；analytics 不提供二次登录 UI，不走 metabase bootstrap 向导。
- 前端仅通过 `dts-analytics` 的 `/analytics/api/**` 交互（松耦合），不依赖 metabase.jar 或 cljs 源码。
- analytics 作为 platform 子模块：入口由 `dts-platform-webapp` 打开新窗口 `/analytics`；鉴权/会话完全复用 platform（Traefik `platform-forward-auth@file`）。
- 当前交付范围（明确不做）：入门（Getting Started）、Examples、使用向导/帮助中心、用户管理 UI、Admin Settings、Alerts/Pulses、Embedding（iframe 匿名/公开嵌入）。
- 分享策略：只生成分享链接，但访问必须登录；暂不允许跨部门/跨密级分享（平台接口未定，先预留/占位）。
- 数据源：支持 PostgreSQL / MySQL / Oracle / 达梦（JDBC 直连配置 UI）；达梦 JDBC 驱动 jar 放置在 `services/jdbc/` 并通过 `ANALYTICS_JDBC_DRIVERS_DIR` 动态加载。
- 目标菜单结构（对齐 Metabase 0.58.1 左侧导航语义，允许名称微调，但要保证主要入口存在）：
  - 首页
  - 集合 / 你的个人集合
  - 分析中心（作为“问题/仪表盘”的聚合页）
  - 数据（数据库/表/字段）
  - 模型
  - 指标（本地 + 平台指标占位）
  - 废纸篓

## 0) Metabase v0.58.x “对齐口径”（验收基线）

说明：这里的“对齐”指**用户可见能力 + 关键交互闭环**对齐，不强行要求 UI/内部实现与 Metabase 一致，但 API 行为与结果需满足 v0.58.x UI/业务预期（或 modern 自研 UI 的一致预期）。

### 0.1 核心对象（Core entities）
- [ ] Collections（集合/目录）
- [ ] Questions / Cards（问题/卡片）
- [ ] Dashboards（仪表盘）
- [ ] Databases / Tables / Fields（数据源/表/字段）
- [ ] Models（数据模型：metrics/segments 等）
- [ ] Permissions（权限）
- [ ] Users & Groups（用户/组）
- [ ] Sharing / Public / Embedding（分享/公开/嵌入）
- [ ] Alerts / Pulses（告警/订阅）
- [ ] Activity / Recents / Bookmarks（动态/最近/收藏）

### 0.2 关键交互（必须跑通的用户路径）
- [ ] 进入 analytics → 自动识别 platform 登录态 → 进入首页
- [ ] 浏览集合 → 打开卡片 → 执行查询 → 查看结果/图表
- [ ] 浏览仪表盘 → 支持布局渲染 → 卡片查询结果展示
- [ ] 仪表盘参数（filters）→ 下拉取值/搜索 → 注入到卡片查询 → 刷新结果
- [x] 新建/编辑卡片（当前仅 SQL）→ 保存到集合 → 回到列表可见
- [x] 新建/编辑仪表盘（基础编辑）→ 添加/移除卡片 → 保存 → 回到列表可见

## 1) 功能盘点方法（如何确保不漏）

1. 以 Java 重写后端为准：扫描 `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/*` 的 REST 资源，作为“应实现能力边界”。
2. 以 legacy UI 为验收基线：对 `source/dts-analytics-webapp/legacy/frontend_client` 的 bundle 扫描字符串（见 `source/dts-analytics-webapp/scripts/scan-legacy-api.sh` 输出 `source/dts-analytics-webapp/legacy/api-endpoints.txt`），再配合浏览器 Network 行为验证。
3. 以 Metabase v0.58.x 的模块结构做“检查表”校验：Browse/Collections、Questions、Dashboards、Data、Admin、Sharing/Embedding、Alerts/Pulses、Activity/Recents 等。

## 2) P0（必须交付：替代 legacy 的最小闭环）

### P0-Auth：平台一体化（embedded）
- [x] `dts-analytics-webapp/modern` 访问 `/analytics` 不出现二次登录 UI（依赖 `platform-forward-auth@file` 正常工作）。
- [x] 401/403 统一处理：提示“回到 platform 重新登录/刷新 token”，并提供跳转链接。
- [x] API 访问统一封装（cookie + Authorization Bearer + 401 自动 refresh 一次）。
- [x] `/analytics/api/user/current`：现代端基于该接口判断“已登录/未登录”。

相关后端：
- `GET /api/user/current`（`UserResource`）
- `GET /api/session/properties`（`SessionPropertiesResource`）

### P0-Nav：基础框架与路由
- [x] `/analytics` 默认进入 modern（legacy 临时对照路径：`/analytics/legacy`）。
- [x] App Layout：侧边栏导航（Collections/Dashboards/Questions/Search）。
- [x] 404/错误页（最少 404 + 未登录提示页）。
- [x] 语言切换（zh/en）与默认 zh（不引入第三语言资源）。
- [x] dev 路由对齐：Traefik 代理 Vite 时 strip `/analytics`（保证 `/analytics/@vite/client` 正常，不出现“空白页/只有脚本标签”）。

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

### P0-Data：数据源配置（JDBC 直连）
- [x] 数据源列表（Databases）。
- [x] 新增数据源：连接信息表单 + 连接校验（validate）。
- [x] 同步元数据（sync schema）并在数据源详情页展示表列表。
- [ ] 表/字段浏览页（补齐 Metabase v0.58.x 的交互：schema 分组/搜索/字段详情）。
  - [x] 字段详情页（field detail）：字段属性 + Top values（`GET /api/field/{id}` + `GET /api/field/{id}/values`）。

相关后端：
- `GET/POST /api/database`、`POST /api/database/validate`、`POST /api/database/{id}/sync_schema`、`GET /api/database/{id}/metadata`（`DatabaseResource`）
- `GET /api/table?db_id=...`、`GET /api/table/{id}`（`TableResource`）
- `GET /api/field/{id}`、`GET /api/field/{id}/values`（`FieldResource`）

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
- [x] dashcard 布局（row/col/size_x/size_y）→ 简化响应式网格渲染。
- [x] 参数面板（dashboard.parameters）与 parameter_mappings 注入 query 请求（按后端 query API 支持逐步补齐）。
  - [x] `GET /api/dashboard/params/valid-filter-fields`
  - [x] `GET /api/dashboard/{dashId}/params/{paramId}/values`
  - [x] `GET /api/dashboard/{dashId}/params/{paramId}/search/{query}`
  - [ ] dashcard query body.parameters → 后端套用到 MBQL/native SQL → 返回过滤后的数据
    - [x] MBQL（dimension target）已支持：按 `parameter_mappings[].target=["dimension",["field",fieldId,...]]` 将 `body.parameters[].value` 注入为 `query.filter`（`=` / `in`）
    - [ ] native SQL `{{tag}}/[[...]]` 参数：已支持（`NativeQueryTemplateService`），需要在 modern UI 做完整参数 UI
  - [x] MBQL query.filter → SQL（含 bindings）执行链路已打通：`DatasetResource` / `CardResource` / `DashboardResource` / `PublicResource` / `EmbedResource`

### P1-Dev：快速冒烟（建议）
- [ ] MBQL filter 冒烟：创建一个 `type=query` 的 card（query.filter 含 `=` / `in` / `not-null`）→ `POST /api/card/{id}/query` 应返回 filtered rows。
- [ ] 注意：当前环境如果无法访问 Maven Central，执行 `mvn test` 可能因为缺少 `surefire-junit-platform` 依赖而失败；建议使用现有 offline 镜像/内网 Maven 仓库。

### P1-Write：编辑能力（分阶段）
- [ ] 新建/编辑 Collection（名称/描述/归档/移动）。
- [x] 新建/编辑 Dashboard（名称/描述）。
- [x] Dashboard 编辑（添加/删除 dashcard；移动/布局调整待补齐）。
- [x] Card 保存（修改 name/collection/display/dataset_query；当前仅 SQL/native）。

相关后端（现有实现）：
- `POST/PUT/DELETE /api/collection...`（`CollectionResource`）
- `POST/PUT/DELETE /api/dashboard...`（`DashboardResource`）
- `POST/PUT/DELETE /api/card...`（`CardResource`）

## 4) P2（管理与企业扩展：平台融合）

### P2-Data：数据浏览与元数据
- [x] Databases 列表/详情（只读）。
- [ ] Tables/Fields 浏览（只读）（P0 已有最小表列表，待补齐交互）。
- [ ] Datasets/Segments/Metrics（只读 → 编辑）。

平台融合（占位）：
- [ ] 平台可见数据集/表：先做 dummy API（后续替换为 platform 真接口）。
- [ ] 平台指标列表：先做 dummy API（后续替换为 platform 真接口），analytics 本地指标仍保留。

相关后端：
- `DatabaseResource` / `TableResource` / `FieldResource` / `DatasetResource` / `SegmentResource` / `MetricResource`

### P2-Admin：用户/权限/设置
- [ ] 用户管理（只读/创建/禁用）。
- [ ] 权限图（permissions graph）与授权 UI（与 platform 策略对齐）。
- [ ] settings（站点名称/默认语言/邮件等）与平台配置的边界划分。

相关后端：
- `UserResource` / `PermissionsResource` / `SettingsResource` / `LoginHistoryResource`

### P2-Sharing：分享与嵌入
- [ ] dashboard/card 分享链接（创建/撤销/打开）。
- [x] 分享链接访问必须登录（禁止匿名）：`/api/public/**` 也要求登录态（embedded）。
- [ ] 分享权限：暂不允许跨部门/跨密级分享（平台接口未定，先预留）。

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

## 6) P3（对齐 v0.58.x：高级能力补齐清单）

说明：P3 属于“完整对齐 Metabase”阶段，按优先级推进；这里先列出缺口以免遗漏。

### P3-Query Builder / SQL Editor（问题创建能力）
- [ ] Query Builder（MBQL）完整编辑体验（表/字段选择、聚合、分组、过滤、排序、limit）。
- [ ] SQL Editor：
  - [ ] `{{param}}` / `[[optional]]` 参数输入 UI 与后端渲染一致。
  - [ ] 参数类型（文本/数字/日期/时间/下拉/字段过滤）与默认值。
  - [ ] 运行/保存/另存为/加入 dashboard。
- [ ] 可视化配置面板（图表类型 + 选项）并保存到 `visualization_settings`。

### P3-Admin（设置/用户/权限）
- [ ] 站点设置（站点名/默认语言/主题/Email/Slack/SSO 等）：边界与 platform 对齐。
- [ ] 用户/组管理（只读→管理）；与 platform 账号体系的映射策略。
- [ ] 权限图（DB/Table/Collection 级别）：最少可用配置 + 审计。

### P3-Sharing（嵌入/公开）
- [ ] Card/Dashboard public link：创建/撤销/访问。
- [ ] Embedding（signed）策略：与平台安全头兼容（X-Frame-Options/CSP）。

### P3-Alerts/Pulses（订阅告警）
- [ ] pulse：订阅列表/创建/发送测试/发送历史。
- [ ] alert：基于卡片结果阈值触发（至少结构与 UI 可对接）。
