# T02: 接入 legacy 工具链（plugin-legacy + postcss + browserslist + 别名）

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

把现网已验证的 Chrome 95 兼容工具链搬进原型工程，保证构建产物可在 Chrome 95 运行。

## 技术设计

照搬现网 `source/dts-platform-webapp/vite.config.ts` 的 legacy 配置，按原型需要裁剪：

- **`@vitejs/plugin-legacy ^5.4`**：
  ```ts
  legacy({
    targets: ["chrome >= 95", "edge >= 95", "firefox >= 102", "safari >= 15.4"],
    modernPolyfills: true,
    renderLegacyChunks: false,
  })
  ```
  默认开启 legacy 构建（与现网一致，`LEGACY_BROWSER_BUILD` 默认 `"1"`），`buildTarget = "chrome95"`。
- **PostCSS 兜底**：搬现网 `tools/postcss/legacy-css-fallbacks.ts`（oklch→hsl 安全网）与 `unwrap-css-layers.ts`（按需）。**注意**：兜底仅作安全网，源码层仍手写 HSL/hex token（见 F2），不依赖兜底产出。
- **browserslist**：新增 `.browserslistrc` 含 `Chrome >= 95`（与 legacy targets 一致），供 postcss/autoprefixer 取值。
- **路径别名**：`@/*` → `src/*`，在 `vite.config.ts`（`resolve.alias` 或 `vite-tsconfig-paths`）与 `tsconfig.json`（`paths`）两侧一致配置。
- **禁用清单守门**：约定（写进 README/注释）源码 CSS 禁用 `oklch` / `:has()` / 容器查询(`@container`) / `subgrid`；可选加一条 grep 校验脚本（不强制）。

## 影响范围

- `worklog/prototype/app/vite.config.ts`（plugin-legacy + postcss 插件 + alias）。
- `worklog/prototype/app/tools/postcss/legacy-css-fallbacks.ts`（+ 视需要 `unwrap-css-layers.ts`）。
- `worklog/prototype/app/.browserslistrc`、`tsconfig.json`（paths）、`package.json`（新增 devDeps）。

## 验证

- [ ] `pnpm build` 触发 legacy 构建，产物含 legacy chunk / polyfill（按 `renderLegacyChunks` 设置）。
- [ ] `@/...` 别名在 TS 类型检查与 Vite 构建两侧都解析成功。
- [ ] legacy-css-fallbacks postcss 在构建管线中执行（含一个 oklch 样例验证被改写为 hsl 兜底）。
- [ ] 源码层 grep 无 `oklch(` / `:has(` / `@container` / `subgrid`。

## 完成标准

- [ ] legacy 工具链全部接入且构建通过。
- [ ] browserslist 与 plugin-legacy targets 一致，均含 Chrome 95。
- [ ] 别名两侧一致；Chrome 95 禁用特性清单有明文约定。
