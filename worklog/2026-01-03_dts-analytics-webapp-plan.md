# 2026-01-03：dts-analytics-webapp（legacy UI 冻结 → React 19 重写）计划

## 已完成（本次提交范围）
- 后端 `source/dts-analytics` 取消“构建期内嵌 Metabase UI 静态资源”（不再下载/解压 `metabase.jar`，不再做 SPA fallback 渲染）。
- 新增 `source/dts-analytics-webapp`：用于一次性解压 `metabase.jar` 的 legacy UI（`legacy/frontend_client/`）并提交到 Git，后续作为扫描与对齐基线。
- 输出 UI 调用面初稿：`source/dts-analytics-webapp/legacy/api-endpoints.txt`（由 `scripts/scan-legacy-api.sh` 生成）。

## 关键事实（扫描结论）
- Metabase 0.45.x 的前端产物是编译后的 bundle，内部包含明显的 **ClojureScript 运行时特征**，并使用 React 生态。
- 产物中存在大量绝对路径调用（例如 `\"/api/session/properties\"`），因此 **/analytics 子路径部署需要额外的反代路由设计**（否则请求会落到站点根 `/api`）。

## ToDo（详细清单）

### A. 反代与路由（让 legacy UI 在 `/analytics` 可用）
1. 明确线上域名与路由约束：`https://bi.iae.caep/analytics` 是否必须保留（确认与其他 `/api` 路径是否冲突）。
2. Traefik/网关规则：
   - `/analytics/**` → `dts-analytics-webapp`
   - `/api/**`（仅对 `bi.iae.caep` 生效）→ `dts-analytics`（或转发到 webapp 由其反向代理到后端）
3. 统一 `X-Forwarded-*`/`X-Forwarded-Prefix=/analytics` 的注入策略，确保后端生成 `site-url`、跳转与 cookie path 正确。
4. 增加最小健康检查：
   - webapp：返回 `200`（index.html）
   - backend：`GET /api/health`

### B. Webapp 项目化（从 legacy 过渡到 React 19）
1. 在 `source/dts-analytics-webapp` 内新增 React 19 + Vite 工程骨架（参考 `source/dts-admin-webapp`）：
   - TypeScript + ESLint/Prettier（与 dts-admin-webapp 保持一致）
   - Vite build target 兼容 Chrome 98（browserslist + polyfills 策略）
2. 增加 API 访问层（仅 HTTP）：
   - 统一 `baseURL`（支持 `/analytics` 子路径）
   - 统一鉴权（Keycloak/OIDC + 会话）
3. 规划迁移策略：
   - 先按页面/模块替换：登录/初始化 → 首页 → collection → card → dashboard → admin
   - 每替换一块，减少对 legacy 静态资源的依赖，最终移除 `legacy/`

### C. 后端契约与鉴权（配合 UI）
1. 定版“UI 调用面清单”（从 legacy bundle 扫描出所有 `/api/**` 依赖，并与现有 Java 实现对比缺口）。
2. 统一认证入口：`dts-system` 作为 Keycloak client（`dts-admin`/`dts-platform`/`dts-analytics` 一致）。
3. 会话与 cookie：
   - cookie name/path/domain/samesite/secure 策略统一
   - `/analytics` 子路径场景下保持登录态稳定
4. 冒烟测试（dev 模式）：
   - 启动后端 + webapp
   - `curl` 校验 `/api/health`
   - 浏览器（Chrome 98）验证：能打开页面、能获取 `/api/session/properties`
