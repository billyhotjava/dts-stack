# dts-analytics-webapp

本目录用于承载 `Metabase v0.58.x` 的前端静态资源（从 `metabase.jar` 一次性解压得到），并作为后续 **React 19 + Vite** 重写的起点。

## metabase.jar 下载链接（v0.58.x）

- `https://downloads.metabase.com/v0.58.0/metabase.jar`（示例，实际可替换为任意 `0.58.x`）

说明：本仓库以 `0.58.x` 作为 UI 基线做扫描与功能对齐；可通过 `MB_VERSION=0.58.?.?` 自行指定。

## 一次性解压（生成 legacy 静态资源）

在仓库根目录执行：

```bash
bash source/dts-analytics-webapp/scripts/extract-metabase-ui.sh
```

输出目录：

- `source/dts-analytics-webapp/legacy/frontend_client/`（Metabase UI 编译产物）

`metabase.jar` 不提交到 Git（已在 `.gitignore` 忽略）。

## 启动（本地 / dev）

legacy UI 的 `index.html/public.html/embed.html` 是模板文件（包含 `{{{baseHref}}}`、`{{{bootstrapJSON}}}` 等占位符），因此需要一个轻量 web server 来渲染它们（见 `source/dts-analytics-webapp/server.mjs`）。

在仓库根目录直接启动（需要 Node.js ≥ 18）：

```bash
node source/dts-analytics-webapp/server.mjs
```

可用环境变量：
- `PORT`：默认 `3001`
- `DTS_ANALYTICS_API_BASE`：默认 `http://dts-analytics:3000`

## 启动（容器）

构建镜像（一次）：

```bash
docker build --no-cache -t dts-analytics-webapp:1.0.0 -f source/dts-analytics-webapp/Dockerfile source/dts-analytics-webapp
```

然后启动（开发模式）：

```bash
./dev-up.sh --mode local
```

## UI 技术栈识别（扫描结论）

Metabase v0.58.x 的前端产物中包含明显的 ClojureScript 运行时代码特征（例如 `cljs` 协议/命名空间相关符号），并使用 React 生态（bundle 中包含 React 相关符号与运行逻辑）。这也是后续需要完整迁移到 **React 19** 的原因：保持 UI/交互一致的同时，彻底移除 Clojure 相关构建链路与运行时痕迹。

## 与后端关系

- `dts-analytics`（后端）仅提供 HTTP API（例如 `/api/**`），不再内嵌/渲染前端资源。
- `dts-analytics-webapp`（前端）独立维护，未来逐步替换 legacy 产物为 React 19 项目结构（参考 `dts-admin-webapp` 的架构与技术栈）。

## Embedded 模式（与 Platform 复用登录）

目标：用户已登录 `dts-platform` 后，访问 `https://bi.${BASE_DOMAIN}/analytics` 不再出现二次登录页面。

实现方式：
- Traefik 对 `https://bi.${BASE_DOMAIN}/analytics/api/**` 启用 `platform-forward-auth@file`，由 `dts-platform` 校验会话/Token 并注入 `X-DTS-*` 身份头。
- `dts-analytics` 依据 `X-DTS-*` 自动创建/刷新本地用户，并在需要时自动补发 `metabase.SESSION` Cookie 以满足 legacy Metabase UI 的会话模型。

排查要点：
- 如果浏览器控制台出现 `Failed to load resource: ... /analytics/api/user/current 401`：
  - 优先确认 Traefik router `dts-analytics-api@docker` 的 middlewares 包含 `platform-forward-auth@file`。
  - 确认 `dts-platform` 的 `/api/forward-auth` 返回 2xx（不返回 401），并能返回 `X-DTS-User` 等头。
