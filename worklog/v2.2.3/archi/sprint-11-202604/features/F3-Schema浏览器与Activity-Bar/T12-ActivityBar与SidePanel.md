# T12: Activity Bar + SidePanel 布局

**优先级**: P0
**状态**: READY
**依赖**: F1

## 目标

实现 VS Code 式左侧导航栏（44px icon 栏 + 可折叠面板容器），承载 Schema/History/Saved/Search/Copilot 五个面板入口。

## 技术设计

### ActivityBar

- 宽度固定 44px
- 5 个 icon：Schema / History / Saved / Search / Copilot
- 激活状态用左侧 2px 高亮条指示
- 点击同一 icon 收起面板
- 简洁模式只显示 Schema / History / Saved，Search 和 Copilot 隐藏

### SidePanel

- 宽度可拖拽（200–500px），默认 240px
- 顶部面板标题栏 + 收起按钮
- 内容 slot 动态渲染当前激活面板

### Activity 偏好持久化

- localStorage 存 `sqlide.activityId` 和 `sqlide.sidePanelWidth`
- 重新进入保持上次状态

### 文件结构

```
layout/
  ActivityBar.tsx        (约 120 行)
  SidePanel.tsx          (约 100 行)
  useLayoutStore.ts      (Zustand，UI 布局态)
```

### useLayoutStore

```typescript
interface LayoutStore {
  activeActivity: 'schema' | 'history' | 'saved' | 'search' | 'copilot' | null;
  sidePanelWidth: number;
  bottomPanelHeight: number;
  setActivity(id: string | null): void;
  setSidePanelWidth(w: number): void;
  setBottomPanelHeight(h: number): void;
}
```

## 影响范围

- 新增 `layout/ActivityBar.tsx`
- 新增 `layout/SidePanel.tsx`
- 新增 `layout/useLayoutStore.ts`
- `SqlIde.tsx` 使用上述组件组装骨架

## 验证

- [ ] 5 个 icon 可点击切换
- [ ] 宽度拖拽流畅，持久化生效
- [ ] 简洁模式只 3 个 icon
- [ ] 收起/展开动画不卡顿
- [ ] 键盘可聚焦切换面板（a11y）

## 完成标准

- [ ] 布局骨架完成
- [ ] 偏好持久化生效
