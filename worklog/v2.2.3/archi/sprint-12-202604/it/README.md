# Sprint-12 集成测试

## 目标

验证 v2 响应式大屏在多种 viewport 和 **Chrome 95**（客户约束）下的端到端行为。

## 任务引用约定

跨 Feature 引用统一使用 `F#/T#`。
例如：`F1/T01` 表示“响应式布局引擎”的第一项任务；裸 `T01` 只在单个 Feature 目录内使用。

## 测试矩阵

| 维度 | 覆盖 |
|---|---|
| 浏览器 | **Chrome 95**（硬约束）+ Chrome 109+ + Firefox 102+ |
| Viewport | 1366×768（笔记本）/ 1920×1080（标准）/ 2560×1440（2K）/ 3840×2160（4K） |
| 设备 | PC（主） / Tablet（可选） |
| 大屏场景 | 空大屏 / 含 5 个图表 / 含 20+ 组件（密集） |
| 数据版本 | v1（只读） / v2（全功能） |

## 必测用例

### TC-01：Chrome 95 新建 v2 大屏
1. 在 Chrome 95 打开 `/analytics/screens/new`
2. 输入名称 → 创建
3. 跳转到编辑器
4. 从组件库拖入 3 个图表
5. 保存
6. 打开预览
7. **期望**：无 console error，组件按 grid 布局可见，viewport 变化时组件位置自动重排

### TC-02：Chrome 95 viewport resize
1. 打开一个含多种组件的 v2 大屏预览
2. 浏览器窗口在 1366×768 / 1920×1080 / 3840×2160 间切换
3. **期望**：组件**位置相对重新分配**（而非整体 scale），图表内部文字/图形清晰，无溢出黑边

### TC-03：v1 大屏只读兼容
1. 打开任一旧 v1 大屏编辑页面
2. **期望**：显示"v1 只读" banner，画布只读
3. 点"转为 v2"→ 确认 → 创建副本 → 编辑副本
4. **期望**：副本是 v2，组件位置近似匹配

### TC-04：组件内部响应式
1. v2 大屏，拖 ECharts line-chart、Text 标题、Image 组件、Table
2. 拖大图表组件宽度从 3→8→3 列
3. **期望**：图表自动 resize（chart.resize 生效），文字字号按容器宽度调整，图片不变形，Table 列宽适应

### TC-05：Chrome 95 性能 smoke
1. 大屏含 20+ 组件在 Chrome 95
2. 持续 resize 浏览器窗口 10 秒
3. **期望**：无 UI 冻结 / 帧率合理，ResizeObserver 回调未失控

## 证据目录

每个 TC 通过后在此留截图或视频：

```
it/chrome-95-evidence/
  TC-01-new-screen/
    screenshot-before-drag.png
    screenshot-after-drag.png
  TC-02-resize-1366.png
  TC-02-resize-3840.png
  TC-03-legacy-v1-readonly.png
  TC-04-component-resize.mp4
  TC-05-stress-resize.mp4
```

## 自动化（可选）

- Playwright 跑 TC-01/02/03 覆盖多 viewport
- 放 `worklog/v2.2.3/sprint-12-202604/it/playwright/`
- 与主仓库的 e2e 套件独立，只跑大屏场景

## 退出标准

- [ ] 所有 TC 在 Chrome 95 通过（有证据）— 待客户侧实机
- [ ] 所有 TC 在 Chrome 109+ 通过 — 待 dev 端跑 Playwright
- [ ] 无 P0/P1 bug
- [ ] 性能：20 组件大屏 resize 时 FPS ≥ 30

## 当前进展（2026-04-22 收尾）

- [x] 代码层面 Chrome 95 兼容 — `scripts/chrome-95-smoke.md` grep 通过
- [x] TS 类型检查 — `npx tsc --noEmit` 仅留 screenTemplates.ts 的预存问题
- [x] vitest — `schema.test.ts`（16 cases）+ `migrate.test.ts`（8 cases）全过
- [x] 路由 `/bi/screens/:id/designer-v2` 已注册
- [x] v1 编辑器 banner「转为 v2」已可用，不破坏旧数据
- [ ] Chrome 95 真机 5 个 TC evidence（需客户侧或 BrowserStack 补齐）
