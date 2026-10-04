# F4：Hetu 遗留代理移除与内置 BI 收敛

**优先级**：P0
**状态**：DONE（2026-07-31 验收：IT-06/IT-07 PASS；浏览器证据段 GAP，见 it/baseline.md）

## 目标

2026-04 已决策的 Hetu 移除落地：compose 与 traefik file provider 中的 9 组 hetu 代理路由、host alias 与相关 middleware 全部删除，用户入口统一收敛到内置 `/bi`，旧路径行为可预期。

## 契约定义（Contracts）

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| 路由（移除前） | `/dashboards`、`/dashboard/hetu`、`/screen`、`/system`、`/tdv`、`/account`、`/hetu`、`/static`、`/core|/screen-|/default~|/vendors~|/runtime~` | 代理至 `hetu@file`（`hetu.upstream:7778`，账本#13/#14） |
| 路由（移除后） | 上述 PathPrefix | 无专属路由 → 回落平台 UI SPA（按既有主路由规则，账本#13 的排除项同步清理）；前端不再生成指向这些路径的入口 |
| 前端 | BI 引擎选项与链接生成 | 移除 HETU 引擎选项；`biLinkUrl.ts` 保留 `shouldRedirectHetuEntryToAnalytics` 的历史深链重定向（账本#15），收敛为纯兼容层 |
| 配置 | `HETU_UPSTREAM_IP` | init.sh/.env 模板标记退役（deprecated 注释）；不删除客户 .env 既有值（非目标） |
| 交付 | `services/dts-proxy/dynamic/traefik-dynamic.yml` | hetu service/routers/middlewares 段删除；`.bak.bi_yuzhicloud_20260412161329` 保留作历史对照 |

## UI/UX 规格（面向用户部分）

- **入口**：平台"数据分析/BI 链接"相关页面——引擎选项只剩内置 BI；用户从菜单进入的是 `/bi`。
- **四态**：空（无 BI 链接时既有空态）；加载（既有）；错误（访问已移除的旧路径 → SPA 404/引导页，文案指向新入口，不出现代理 502/504）；成功（`/bi` 正常打开）。
- **关键交互**：历史书签/深链命中 `shouldRedirectHetuEntryToAnalytics` → 重定向 `/bi`（账本#15 既有行为保留）。
- **操作走查**：1. 打开 BI 链接页面 → 2. 引擎选项无 HETU → 3. 点击链接进入内置 `/bi` → 4. 直接访问 `/dashboards` 旧地址 → 看到 SPA 引导而非网关错误。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | compose 与 file provider 的 Hetu 路由移除 | P0 | READY | - |
| T02 | 前端 HETU 引擎选项与链接收敛 | P0 | READY | T01 |
| T03 | 旧路径回落行为验证与升级说明 | P0 | READY | T01/T02 |

## Definition of Ready

- [x] 契约已钉死（移除清单逐条列出，回落行为明确）
- [x] 竖切片已画通（代理层删除 → 前端收敛 → 旧路径回落 → 验证）
- [x] UI 落点已命名（BI 链接页面、旧路径回落表现）
- [x] 依赖已就绪（重定向逻辑已存在，账本#15）
- [x] 验收可验证（IT-06；浏览器 smoke 段受共享基线约束，见 `it/baseline.md`）

## 完成标准

- [ ] compose 与 traefik-dynamic.yml 无 hetu 路由/service/host alias（IT-06）
- [ ] 前端无 HETU 引擎选项，历史深链重定向仍工作（IT-06）
- [ ] `docker compose config` 与 traefik 配置校验通过；旧路径回落行为按契约（IT-06）
- [ ] 升级说明含旧路径变更告知（IT-06）
