# T01: 引入 react-grid-layout + Chrome 95 验证

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

引入响应式网格布局库作为 v2 的布局引擎，并在 Chrome 95 做兼容性验证 — 不通过则换候选。

## 技术设计

### 候选库评估

| 库 | 版本 | 依赖 | Chrome 95 | 备注 |
|---|---|---|---|---|
| `react-grid-layout` | 1.4.x | React 16+, lodash | 需验证 | 主推 |
| `@dnd-kit/sortable` + CSS Grid 自研 | — | dnd-kit | — | 备选，工作量大 |
| `react-mosaic-component` | — | — | — | 偏向 IDE 分栏 |

主选 `react-grid-layout@1.4.x`（当前最新 major），需验证：
1. 安装后 bundle 体积（要 < 100KB gzipped）
2. 在 Chrome 95 能正常拖放 + resize
3. 源码里无 `:has()` / `container queries` / `structuredClone` / 其他 Chrome 95 不支持的 API
4. ResizeObserver 在 Chrome 95 原生支持（验证）

### 安装

```bash
cd source/dts-platform-webapp
pnpm add react-grid-layout@^1.4.0
pnpm add -D @types/react-grid-layout
```

### CSS 导入

```css
/* src/analytics/pages/screens/v2/responsiveLayout.css */
@import 'react-grid-layout/css/styles.css';
@import 'react-resizable/css/styles.css';
```

### Chrome 95 验证步骤

1. 新建 `src/analytics/pages/screens/v2/__dev__/GridLayoutSmoke.tsx` 样例页
2. 放 3 个 demo 组件用 `react-grid-layout` 布局
3. 在 Chrome 95 环境（docker 镜像或 BrowserStack）打开 vite dev
4. 验证：
   - 页面正常渲染，组件可见
   - viewport resize 时 layout 重排
   - 无 console error
   - Vite legacy 构建产物（`pnpm build`）在 Chrome 95 也能跑

## 影响范围

- `source/dts-platform-webapp/package.json` — 新增 deps
- `source/dts-platform-webapp/pnpm-lock.yaml`
- `source/dts-platform-webapp/src/analytics/pages/screens/v2/` — 新目录（本 Sprint 所有 v2 代码放这）
- `source/dts-platform-webapp/src/analytics/pages/screens/v2/__dev__/GridLayoutSmoke.tsx` — 临时 smoke 页（本任务完成后删除）

## 验证

- [ ] `pnpm install` 无错误
- [ ] Smoke 页面在 Chrome 109+ 可运行
- [ ] Smoke 页面在 Chrome 95 可运行（手动截图为证）
- [ ] `pnpm build` 通过（legacy target chrome95）
- [ ] 检查 bundle 分析，react-grid-layout 大小符合预期
- [ ] 若 Chrome 95 不通过：记录失败原因，切备选方案并更新本任务

## 完成标准

- [ ] 依赖已 install 并 commit
- [ ] Chrome 95 通过证据已保存到 `worklog/v2.2.3/sprint-12-202604/it/chrome-95-evidence/T01-grid-layout/`
- [ ] Smoke 页面删除或加路由保护（仅 dev 可见）
