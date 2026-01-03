# dts-analytics：Metabase 后端 Java 重写 ToDo（更新于 2026-01-01）

## 目标与约束（已确认）
- **最终目标**：完全替换原 Metabase（后端 Clojure）为 **Java（Spring Boot/JHipster 风格）重写实现**，最终仓库内 **没有一行 Clojure 代码**。
- **UI**：继续使用 **Metabase UI v0.45.6**，并满足 **Chrome 98** 兼容性要求。
- **数据**：**不保留历史数据**；允许复用现有应用库（Postgres）但可按实现调整 schema。
- **上线方式**：一次性替换（当前 Metabase 未部署，实际可按“预发布验收 → 生产一次性上线”的节奏执行）。

> 重要澄清：**继续使用 Metabase UI 等价于必须满足 UI 对后端 API 的“契约”**（不一定与开源 Metabase 的实现一致，但行为/字段/错误码/鉴权流程必须让 UI 正常工作）。

## 当前实现约束（技术落地）
- UI 作为独立模块维护：`source/dts-analytics-webapp` 一次性解压 `metabase.jar` 得到 legacy UI（用于扫描/对齐），后续逐步迁移到 React 19；`dts-analytics` 后端不再内嵌/渲染 UI 静态资源。

## 当前已完成（基于 `source/dts-analytics`）
- 后端基础：Spring Boot 启动、基础配置、`/api/health`、`/api/info`、诊断接口与基础错误模型、MockMvc 集成测试。
- UI 解耦：移除后端构建阶段的 UI 抽取与 SPA fallback；前端静态资源由 `source/dts-analytics-webapp` 独立交付。
- 初始化/登录态：`/api/setup` 初始化管理员；`/api/session` 登录/退出；`/api/user/current` 当前用户；站点 settings 的最小读写。
- 数据源与元数据：`/api/database` CRUD（最小集）+ `POST /api/database/:id/sync_schema`（JDBC 元数据同步）；`/api/database/:id/metadata|schemas|schema/:schema|fields`、`/api/table`、`/api/table/:id`、`/api/table/:id/query_metadata`、`/api/field/:id` 最小可用。
- 连接校验与辅助：`POST /api/database/validate` 支持真实 JDBC 连通性校验；`GET /api/table/:id/fks` 返回外部库外键映射；`GET /api/field/:id/values` 返回 distinct values（含 `has_more_values`）。
- Dataset 兼容路径：补齐 `POST /api/dataset/native|pivot|duration`（与 UI bundle 预期路径一致）。
- 查询：`/api/dataset` 支持 `type=native` 与最小 `type=query`（MBQL：`source-table` + `fields` + `order-by` + `limit/page`）；card/dashboard query 路径同步支持 `type=query`。
- 内容组织：collection（含 personal collection）、card（CRUD + query/pivot query）、dashboard（CRUD + dashcard query/copy/save 等最小可用）、`/api/collection/:id/items` 与 `/api/search` 基础可用。
- 收藏（Bookmark/Favorite）：补齐 `POST/DELETE /api/bookmark/{card|dashboard|collection}/:id`、`PUT /api/bookmark/ordering`、`GET /api/bookmark`；card/dashboard 列表与 collection items 返回 `favorite` 状态；dashboard 的 `/favorite` 路径对接到同一收藏逻辑。
- 活动（Activity）：新增持久化 `analytics_activity`，补齐 `GET /api/activity`、`GET /api/activity/recent_views`、`GET /api/activity/popular_items`；在 `GET /api/card/:id`、`GET /api/dashboard/:id`、`GET /api/collection/:id` 自动记录 view，`recent_views` 返回带 `model_object` 的结构以满足 UI 展示。
- 订阅/告警（Pulse/Alert）：新增持久化 `analytics_pulse`/`analytics_alert`（含 subscription 表），补齐 `GET/POST/PUT /api/pulse`、`GET /api/pulse/:id`、`GET /api/pulse/form_input`、`GET /api/pulse/preview_card_info/:id`、`POST /api/pulse/test`、`DELETE /api/pulse/:id/subscription`；补齐 `GET/POST/PUT /api/alert`、`GET /api/alert/question/:questionId`、`GET /api/alert/:id`、`DELETE /api/alert/:id/subscription`（发送目前为 no-op，但 UI 可完成创建/编辑/取消订阅流程）。
- Segment/Metric：新增持久化 `analytics_segment`/`analytics_metric`，补齐 `GET/POST/PUT/DELETE /api/segment` 与 `GET/POST/PUT/DELETE /api/metric`（最小持久化 + archived 逻辑，供 Query Builder/管理页调用）。
- 用户/人员管理（People）：补齐 `GET/POST/PUT/DELETE /api/user`、`GET /api/user/:id`、`PUT /api/user/:id/password`、`PUT /api/user/:id/reactivate`、`POST /api/user/:id/send_invite`、`PUT /api/user/:id/modal/qbnewb`（最小可用，满足管理页流程）。
- 组与权限（Groups/Permissions）：新增持久化 `analytics_group`、`analytics_group_membership`、`analytics_permissions_graph`，补齐 `GET/POST/PUT/DELETE /api/permissions/group`、`GET/POST/PUT/DELETE /api/permissions/membership`、`GET/PUT /api/permissions/graph`（最小可用，满足管理页流程）。
- OIDC（Keycloak）入口（可选）：新增 `/auth/oidc/login` + `/auth/oidc/callback`（通过 code flow 换 token，创建 `metabase.SESSION` 会话并跳回 `/analytics/`；由 `dts.analytics.oidc.enabled` 控制）。
- 管理端集成配置（Email/Slack/Google/LDAP）：补齐 `GET/PUT /api/email`、`POST /api/email/test`、`GET/PUT /api/slack/settings`、`GET /api/slack/manifest`、`GET/PUT /api/google/settings`、`GET/PUT /api/ldap/settings`（最小持久化用 `analytics_setting`，供管理页加载/保存）。
- Public 分享（Public sharing）：新增 `analytics_public_link`；补齐 `POST/DELETE /api/card/:id/public_link`、`POST/DELETE /api/dashboard/:id/public_link`、`GET/POST /api/public/card/:uuid`、`GET/POST /api/public/dashboard/:uuid`、dashcard query 路径（public view 可完整访问与查询）。
- Dashboard 分享字段：dashboard 列表/详情返回 `public_uuid`；`GET /api/dashboard/public` 返回已开启 public 分享的 dashboards。
- Embedding（嵌入）：新增 `/api/embed`（enabled/secret_key/embedding_params）、`/api/preview_embed`（生成 preview token）；实现 HS256 JWT 校验；补齐 `/api/embed/card/:token`、`/api/embed/dashboard/:token` 及对应 query 路径。
- Revisions（版本历史/回滚）：新增 `analytics_revision`；补齐 `/api/revision`、`/api/revision/:id`、`POST /api/revision/revert`；在 card/dashboard create/update/save/delete 记录 revision。
- 测试：新增 `PublicSharingEmbeddingAndRevisionResourceIT` 覆盖 public share / embed / revision 的关键路径；`mvn -f source/dts-analytics/pom.xml test` 通过。
- 密码重置（最小流程）：补齐 `POST /api/session/forgot_password`、`GET/POST /api/session/password_reset_token_valid`、`POST /api/session/reset_password`（token 存储 `analytics_password_reset_token`，邮件发送暂不实现）。
- 登录历史（最小可用）：补齐 `GET /api/login-history/current`，并在 `POST /api/session` 登录成功时记录 `analytics_login_history`。

---

## 0. 基线冻结（启动重写前必须做完）
1. **冻结目标 Metabase UI 版本**
   - 明确要使用的 Metabase UI 版本（tag/commit）并记录到文档：这会直接决定 API 面、字段、前端打包链与 Chrome 98 兼容改动范围。
2. **定义“功能全集”清单（验收口径）**
   - 以 UI 可见功能为主线：连接管理、元数据浏览、提问/查询、图表、看板、集合与权限、分享/嵌入、订阅/告警、管理设置、审计等。
   - 明确必须支持的数据库/驱动范围（P0 驱动清单）。
3. **确定最终路由与 basePath**
   - 统一外部访问路径：`https://<host>/analytics/...`（Traefik `StripPrefix` + `X-Forwarded-Prefix=/analytics`）。
   - 统一 cookie path、重定向 URL、绝对链接生成规则（尤其 OIDC callback）。
4. **确定运行端口与容器形态**
   - 为减少 Traefik/compose 改动，建议 Java 服务内部端口直接使用 **3000**（替换当前 compose 里的 Metabase 端口约定）。

---

## 1. 构建/发布/部署（把 Java 服务变成可替换的 `dts-analytics`）
1. **构建输出**
   - 统一 Maven 构建、产出可运行 jar；决定是否纳入 `source/pom.xml` 聚合（或保持独立模块但提供顶层脚本/CI）。
2. **Docker 镜像**
   - 编写 `services/dts-analytics/Dockerfile`（多阶段：mvn build → jre runtime）。
   - 约定镜像 tag 与 `imgversion.conf`/`IMAGE_DTS_ANALYTICS` 的联动。
3. **compose 集成**
   - 将 `dts-analytics` 与 `dts-analytics-webapp` 合并到 `docker-compose.dev.yml` / `docker-compose-app.yml` / `docker-compose.legacy.yml`（端口、healthcheck、labels、env）。
   - 保留现有挂载约定：`/plugins`、`/var/log/...`、`/certs`。
4. **运行时配置体系**
   - 统一 `DTS_ANALYTICS_*`/`SPRING_*` 配置，避免沿用旧 Metabase 的 `MB_*` 环境变量体系。

---

## 2. Web 基座（完成 Batch 2：请求链路/错误/可观测性）
1. **Forwarded headers / basePath 支持**
   - 正确处理 `X-Forwarded-*` 与 `X-Forwarded-Prefix`，保证 UI 访问 `/analytics` 时：
     - 静态资源路径正确；
     - 后端生成的链接/重定向正确；
     - OIDC callback URL 正确。
2. **统一错误模型**
   - 统一 Security 与 Controller 异常输出（`ApiError`/ProblemDetails 二选一），保证 UI 能稳定解析：
     - status/error/message/path/requestId；
     - 业务错误码（如需）。
3. **观测性**
   - Actuator：liveness/readiness、info/health 细分；
   - metrics（Prometheus/OTel 视项目约定）。
4. **请求日志与审计日志**
   - 访问日志：requestId、用户、URI、耗时、状态码、客户端信息；
   - 审计日志：登录、权限变更、资源 CRUD、分享/嵌入、连接变更、查询执行等。

---

## 3. 前端集成（Metabase UI 静态交付 + Chrome 98）
1. **UI 交付方式**
   - 先把 legacy UI（从 `metabase.jar` 解压）冻结到 `source/dts-analytics-webapp/legacy/` 用于契约对齐与接口扫描。
   - 后续前端独立构建（React 19 + Vite），以 `dts-admin-webapp` 的方式交付（独立镜像/静态托管），并通过反代只与后端 API 交互。
2. **Chrome 98 兼容**
   - 调整前端构建目标（browserslist/webpack/babel/polyfills），并建立“Chrome 98 冒烟测试”用例（至少覆盖登录、进入首页、打开问题/看板）。
3. **路由与反代（/analytics）**
   - legacy UI 的请求路径包含绝对 `/api/**`，需要在反代层明确路由策略（避免与其他应用冲突）。
   - 目标形态：`/analytics` → webapp；API → `dts-analytics`（仅 HTTP；不引入文件级耦合）。


---

## 4. 认证与会话（Keycloak/OIDC，替换 Basic Auth 占位）
1. **OIDC 登录流**
   - 授权发起、callback 处理、token 交换、用户信息拉取、会话建立、退出登录。
   - 回调路径必须与 `/analytics` basePath 兼容（反代场景）。
2. **会话模型**
   - 选型：Spring Session（Redis/DB）或自研 DB session；
   - Cookie（SameSite/Secure/Path/Domain）策略；
   - CSRF 策略（需与 UI 请求方式匹配）。
3. **身份映射**
   - claim → 用户/组/角色映射（含 roles 过滤、dept_code、person_security_level 等扩展字段预留）。
4. **UI 所需 session API**
   - 补齐 UI 依赖的登录态相关接口（例如：current user、permissions、settings 等，具体以“UI 调用面清单”为准）。

---

## 5. 应用库（App DB）与初始化（不迁移历史，但要能从 0 跑通）
1. **迁移工具**
   - Flyway/Liquibase 二选一，建立 schema 版本管理与回滚策略。
2. **核心表设计（最小可用 → 覆盖全集）**
   - 用户/组/权限图；
   - 数据源连接（database）、schema/table/field 元数据；
   - 问题（card）、看板（dashboard）、集合（collection）、权限矩阵；
   - 分享/嵌入 token、订阅/告警（pulse/alert）；
   - 设置（settings）、审计事件（audit）。
3. **初始化流程**
   - 首次启动向导：创建管理员、配置站点 URL、连接数据源、首次 sync。

---

## 6. 元数据同步与任务系统（metabase/sync + metabase/task/task_history）
1. **Scheduler**
   - Quartz 或 Spring Scheduler + 持久化 job store；
   - 任务注册/启停/重试/并发控制。
2. **Sync 引擎**
   - schema/table/field 拉取与差异更新；
   - 字段类型推断、样本统计、指纹/缓存策略；
   - Sync 历史与可观测性（任务历史表 + UI 展示所需 API）。

---

## 7. 查询引擎（metabase/query_processor 全量替换）
1. **查询模型**
   - MBQL 支持范围定版（Metabase UI 会生成 MBQL/原生查询，两条路都要通）；
   - 参数系统（parameters/native snippets）。
2. **执行管线**
   - 解析 → 计划 → 权限校验 → SQL/driver translate → 执行 → 结果格式化 → 缓存；
   - 结果分页/行数限制/超时/取消执行；
   - 查询缓存与隔离（按用户/权限/参数）。
3. **安全**
   - 行列权限、RLS/segment、字段脱敏；
   - 防止 SQL 注入（native query 的参数绑定策略要明确）。

---

## 8. API 面补齐（以“UI 调用面清单”为驱动）
1. **建立 UI 调用面清单**
   - 通过抓包/日志把 UI 在关键路径下调用的 endpoints 全量列出（含 method、params、payload、返回结构、错误码）。
2. **按功能域补齐**
   - setting/config
   - session/auth/user/permissions/groups
   - database + table/field metadata
   - card/question + query endpoints
   - dashboard + parameters
   - collection + sharing/public links/embedding
   - search
   - pulse/alert + channels（email/webhook 等）
   - admin（people、permissions matrix、audit）
3. **兼容策略**
   - 返回字段命名、分页格式、时区/locale 行为、错误结构保持 UI 可用；
   - 对 UI 不再使用的历史接口允许不实现，但必须通过“UI 调用面清单”验证无调用。

---

## 9. Drivers / 插件体系（替换 `modules/drivers/**`）
1. **Driver SPI 设计**
   - capability、DDL/DML、类型映射、schema introspection、parameter binding、limit/offset 方言等。
2. **优先驱动实现（按业务定版）**
   - 至少：Postgres（用于自测/应用库）、以及业务主数据源（如 DM/Inceptor/Hive/ClickHouse 等）。
3. **插件目录**
   - 兼容 `services/dts-analytics/plugins/`：JDBC jar 自动加载、版本冲突隔离策略。

---

## 10. 企业级功能（在“先对齐 Metabase 全量功能”之后追加）
1. **权限增强**：密级/部门范围的细粒度策略、审计增强、支持访问授权流程。
2. **数据治理集成**：数据集发布/审批/血缘/质量指标对接。
3. **AI/语义检索等**：在 UI 入口与权限范围内逐步落地。

---

## 11. 测试、验收与上线
1. **测试矩阵**
   - 单元测试（service/driver/permission）；
   - 集成测试（Postgres + 至少 1 个业务数据源）；
   - API 合约测试（基于 UI 调用面清单）；
   - E2E：固定使用 **Chrome 98**（容器化跑 UI 冒烟：登录 → 建库 → 同步 → 提问 → 看板）。
2. **性能与容量**
   - 并发查询、慢查询隔离、连接池、缓存命中、长任务调度；
   - 日志轮转与磁盘占用策略（100MB 分片）。
3. **上线清单**
   - compose/Traefik 配置、证书信任链、Keycloak client/mapper、默认管理员初始化策略；
   - 一次性上线前的“预发布验收脚本”（自动跑完关键路径并出报告）。

---

## 12. 去 Clojure（最终收尾，确保“没有一行 Clojure”）
1. 移除 `source/dts-bi-analytics`（或移至外部独立仓库，仅保留前端构建产物流程）。✅（已将 `source/dts-bi-analytics` 从 git 跟踪中移除；工作区残留目录为未跟踪并已加入 `.gitignore`）
2. 移除 `services/dts-analytics-dev` 及所有 clojure/dev 脚本依赖（node-only 构建若仍需要则保留，但不得包含 clj 代码）。
3. 清理 compose/dev-up 里的 analytics 独立 overlay 路径与说明，确保主流程只依赖 Java + webapp。✅（已合并到 `docker-compose.dev.yml`/`docker-compose-app.yml`/`docker-compose.legacy.yml`，`dev-up.sh` 不再引用 `docker-compose.analytics*.yml`）
