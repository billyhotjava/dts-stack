# T06: Operator 工具栏

**优先级**: P0
**状态**: READY
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

- [ ] 工具栏样式不抢主画布注意力（半透明 / 浮动右下）
- [ ] 截图体积合理（< 500KB / 中等 graph）
- [ ] 单元测试：缩放联动 + 截图调用 mock
