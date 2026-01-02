# dts-analytics-webapp

本目录用于承载 `Metabase 0.45.x` 的前端静态资源（从 `metabase.jar` 一次性解压得到），并作为后续 **React 19 + Vite** 重写的起点。

## metabase.jar 下载链接

- `https://downloads.metabase.com/v0.45.4.3/metabase.jar`

说明：`0.45.6` 在 `downloads.metabase.com` 上没有对应的 `metabase.jar`，所以使用 `0.45.4.3` 作为 0.45.x 的 UI 基线做扫描与功能对齐。

## 一次性解压（生成 legacy 静态资源）

在仓库根目录执行：

```bash
bash source/dts-analytics-webapp/scripts/extract-metabase-ui.sh
```

输出目录：

- `source/dts-analytics-webapp/legacy/frontend_client/`（Metabase UI 编译产物）

`metabase.jar` 不提交到 Git（已在 `.gitignore` 忽略）。

## UI 技术栈识别（扫描结论）

Metabase 0.45.x 的前端产物中包含明显的 ClojureScript 运行时代码特征（例如 `cljs` 协议/命名空间相关符号），并使用 React 生态（bundle 中包含 React 相关符号与运行逻辑）。这也是后续需要完整迁移到 **React 19** 的原因：保持 UI/交互一致的同时，彻底移除 Clojure 相关构建链路与运行时痕迹。

## 与后端关系

- `dts-analytics`（后端）仅提供 HTTP API（例如 `/api/**`），不再内嵌/渲染前端资源。
- `dts-analytics-webapp`（前端）独立维护，未来逐步替换 legacy 产物为 React 19 项目结构（参考 `dts-admin-webapp` 的架构与技术栈）。

