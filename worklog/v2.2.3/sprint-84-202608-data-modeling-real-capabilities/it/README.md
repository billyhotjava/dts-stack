# Sprint-84 集成验收

| ID | 旅程 | 状态 |
|---|---|---|
| IT-84-01 | 真实规划上下文 → 建模概览投影 | PENDING |
| IT-84-02 | 标准目录 → CRUD/导入 → 模型字段引用 | PENDING |
| IT-84-03 | 指标目录 → 编辑校验 → 发布/版本 | PENDING |
| IT-84-04 | 真实关系组合 → 筛选跳转；真实工具入口/历史 | PENDING |
| IT-84-05 | Sprint-83 + Sprint-84 七入口联合 Chrome 95/E2E | PENDING |

不得用 mock、占位截图或组件存在替代真实运行证据。

## E2E 前置状态（2026-08-03）

- 最终 TypeScript、集中测试、生产构建、独立 Review、GitNexus 变更检测和目标前端镜像部署均已完成；`dts-platform-webapp:1.0.0` 当前 digest 为 `sha256:2bf603f5ce642319182ae4a89cc26e32063bc0ce910d9a7e6783f5b9f7bdc767`，内外入口 HTTP 200。
- IT-84-01～05 均保持 `PENDING`；当前环境未提供 `E2E_USERNAME`/`E2E_PASSWORD`，可写测试租户、清理边界和 Chrome 95 执行环境也未确认，因此真实 E2E fail-closed，不能沿用历史登录状态。
- 最终联合命令所需的只读 27 菜单 smoke 与可写 Sprint-83/84 roundtrip 均已就绪；新增 roundtrip 的 Biome、源契约 4/4、TypeScript、Playwright 发现和独立 Review 已通过。可写用例强制 `E2E_MODELING_WRITE_ALLOWED=true`、精确计划选项、`E2E_` 前缀和 `retain` 证据策略，并按本次资源 ID 校验公共审计，不接受历史 Candidate 或并发审计记录冒充。
- `sprint84-data-modeling-real.spec.ts` 只作为零 mock、生产只读 smoke；它不得把菜单/API/布局 smoke 冒充 CRUD、发布、物化或审计闭环。
- 获得授权输入后执行一次联合 E2E，并按旅程逐项登记通过、失败原因、截图、脱敏 API 与公共审计证据。

## 概念维度与维度表拆分验证（2026-08-04）

- 概念维度已按原型拆为独立 `DimensionDefinition` 流程：页面仅保留数仓分层、业务分类、数据域、系统编码、中文名称和描述；系统编码只展示服务端返回值，不进入创建请求。
- 概念维度保存重试复用草稿级幂等键；保存后可通过右侧“版本管理”确认 `DRAFT → CURRENT`，确认后的定义可进入维度表既有 `CURRENT` 绑定流程。“发布记录”保持只读，并明确服务端尚无独立记录契约。
- 最终聚焦验证：Biome 检查通过；7 个测试文件、48 项用例通过；`LEGACY_BROWSER_BUILD=1 pnpm build` 通过（10,555 个模块，Vite 与 TypeScript 均 exit 0）；Chrome 95 禁用语法扫描无命中。
- 真实只读 E2E 未执行：当前环境未提供 `E2E_BASE_URL`、`E2E_USERNAME`、`E2E_PASSWORD`、`E2E_STORAGE_STATE` 或默认 storage state，且本机浏览器为 Chrome 150。IT-84-05 继续保持 `PENDING`，不得将代码/构建结果标记为真实页面验收。
