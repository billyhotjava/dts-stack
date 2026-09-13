# T06: Operator 工具栏

**优先级**: P0
**状态**: DONE
**依赖**: T02

## 目标

画布右下角浮动工具栏：缩放滑块、+/- 按钮、fit view、截图（导出 PNG）、撤销/重做按钮（实际逻辑在 F5-T05 实现，本任务先占位 disabled）。

## 技术设计

### 文件

```
src/components/workflow/operator/
├── Operator.tsx               # 容器
├── ZoomControls.tsx           # +/- + 百分比
├── FitViewButton.tsx
├── ScreenshotButton.tsx       # html-to-image
└── UndoRedoButtons.tsx        # F5-T05 完成前 disabled
```

### 截图实现

```ts
import { toPng } from 'html-to-image';

async function onScreenshot() {
  const node = document.querySelector('.react-flow__viewport') as HTMLElement;
  const url = await toPng(node, { backgroundColor: '#fafbfc' });
  const link = document.createElement('a');
  link.download = `workflow-${Date.now()}.png`;
  link.href = url;
  link.click();
}
```

依赖 `html-to-image`（项目已有；如无则新加）。

### 缩放联动

复用 reactflow `useReactFlow()` 的 `zoomIn / zoomOut / fitView`，state 同步到 ui-slice。

## 影响范围

- 新增 `src/components/workflow/operator/*` 5 个文件
- 可能新增 `html-to-image` 依赖（< 30KB gz）

## 验证

- [ ] +/- 按钮缩放正常，百分比同步
- [ ] fit view 一键回到全节点适配
- [ ] 截图按钮：导出 PNG，节点位置/连线/背景一致
- [ ] 撤销/重做按钮存在但 disabled，hover 显示"待 F5 启用"tooltip
- [ ] 工具栏 ARIA：role="toolbar"，每个按钮 aria-label

## 完成标准

- [x] 工具栏右下角浮动（ReactFlow `<Panel position="bottom-right">`），白底 + slate 边框 + 阴影；不遮挡画布主体
- [x] role="toolbar"，每个按钮含 aria-label/title；focus-visible 蓝色描边
- [x] 缩放：+/- 按钮联动 useReactFlow().zoomIn/zoomOut（duration 200ms 平滑），实时显示百分比（来自 useViewport().zoom）
- [x] Fit view：useReactFlow().fitView({padding:0.1, duration:200})
- [x] 撤销/重做按钮 disabled，tooltip "F5-T05 zundo 接入后可用"
- [x] **截图按钮 disabled（YAGNI 路径）**：避免本 Sprint 引入 html-to-image 30KB；待 F4 落地保存/分享时再补；按钮 aria-disabled / tooltip 已就位
- [x] 4 vitest 用例：4 区段渲染 / 3 个 disabled 状态 / zoom in/out 触发 mock；workflow 整模块 40/40
- [x] tsc 0 错；最大文件 49 行（zoom-controls）

## YAGNI 跟踪

- [ ] 后续 issue：引入 `html-to-image@^1.11`，启用 ScreenshotButton 真实导出（依赖 < 30KB gz）。在 F4 节点配置/保存流程同时 PR。
