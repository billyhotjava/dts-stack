# T03: 浏览器 smoke 与文档验收

**优先级**: P0  
**状态**: DONE  
**依赖**: T02

## 目标

证明拖拽工作台在 Chrome 95 构建目标下可构建、可渲染，并在桌面/窄屏布局中不出现明显破版。

## 技术设计

- 使用 `pnpm build` 验证 legacy browser build。
- 使用 Vite preview + Playwright mock 会话和语义建模 API。
- 验证桌面 1366x768 和窄屏 390x844。

## 影响范围

- `source/dts-platform-webapp`
- `worklog/v2.2.3/sprint-56-202607/assets`
- `worklog/v2.2.3/sprint-56-202607/it/README.md`

## 验证

- [x] `pnpm build`
- [x] Playwright 桌面 smoke
- [x] Playwright 窄屏 smoke

## 完成标准

- [x] 页面非空白。
- [x] React Flow 节点和边渲染。
- [x] 未绑定指标分组渲染。
- [x] 窄屏无横向撑破。
