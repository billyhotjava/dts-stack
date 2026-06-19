# T03: 构建与 Chrome 95 产物冒烟验证

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

验证 legacy 构建产物在 Chrome 95 环境可加载、首屏渲染、控制台无致命错误，给整条关键路径一个可信地基。

## 技术设计

- **构建产物检查**：`pnpm build` 后检查 `dist/` 含 legacy 入口与 polyfill 注入，确认 `buildTarget = chrome95` 生效。
- **Chrome 95 模拟**：原型阶段不强求真实 Chrome 95 实机，可用以下任一证据：
  1. Playwright 以接近 Chrome 95 的 UA / 旧版 Chromium channel 加载 `pnpm preview` 产物并截图；
  2. 或对 `dist` 产物做语法/特性扫描（无未降级的现代 CSS/JS 特性）。
- **冒烟范围**：仅验证地基——页面挂载、AntD 样式生效、字体加载、控制台无 `SyntaxError`/`ReferenceError`。业务交互留给后续 sprint。
- **证据归档**：截图 / 扫描日志存入 `it/evidence/F1/`，供 sprint IT 引用。

## 影响范围

- 无源码改动（纯验证）；产出验证证据文件到 `it/evidence/F1/`。
- 可选新增 `preview` 脚本或一个轻量冒烟脚本。

## 验证

- [ ] `pnpm build && pnpm preview` 启动预览服务。
- [ ] Chrome 95（或等效模拟）加载预览页，首屏渲染外壳/占位页。
- [ ] 控制台无 `SyntaxError` / `ReferenceError` / 未降级特性报错。
- [ ] 截图或扫描日志已归档 `it/evidence/F1/`。

## 完成标准

- [ ] 产物确认开启 legacy 且 target=chrome95。
- [ ] Chrome 95 加载冒烟通过，证据已归档。
- [ ] 地基可信，可解锁 F2/F3/F4 与后续 sprint。
