# T03: 4 类右键菜单（画布 / 节点 / 连线 / 多选）

**优先级**: P0
**状态**: PARTIAL
**依赖**: F4-T01

## 目标

抄 Dify 4 类上下文菜单：

| 触发位置 | 菜单项 |
|---------|--------|
| 画布空白 | 粘贴、全选、fit view、添加便签 |
| 节点右键 | 复制、剪切、删除、重命名、查看详情、添加便签 |
| 连线右键 | 删除、改类型（直线/贝塞尔/折线） |
| 多选右键 | 复制、删除、对齐（左/右/上/下/居中）、组合（创建 group） |

## 技术设计

### 文件

```
src/components/workflow/context-menu/
├── ContextMenu.tsx              # 通用容器
├── PaneContextMenu.tsx
├── NodeContextMenu.tsx
├── EdgeContextMenu.tsx
├── MultiSelectionContextMenu.tsx
└── menu-items.ts                # 共用 item 配置
```

### 触发逻辑

```tsx
// WorkflowCanvas
<ReactFlow
  onPaneContextMenu={(e) => openMenu('pane', e)}
  onNodeContextMenu={(e, node) => openMenu('node', e, node)}
  onEdgeContextMenu={(e, edge) => openMenu('edge', e, edge)}
  onSelectionContextMenu={(e, nodes) => openMenu('multi', e, nodes)}
/>
```

state 在 ui-slice：

```ts
type UiSlice = {
  // ...
  contextMenu: { type: 'pane' | 'node' | 'edge' | 'multi'; x: number; y: number; payload: any } | null;
  setContextMenu: (m: ContextMenuState | null) => void;
};
```

### 菜单实现

```tsx
function ContextMenu() {
  const menu = useWorkflowStore(s => s.contextMenu);
  const close = () => useWorkflowStore.getState().setContextMenu(null);

  useEffect(() => {
    if (!menu) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && close();
    const onClick = () => close();
    window.addEventListener('keydown', onKey);
    window.addEventListener('click', onClick);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('click', onClick);
    };
  }, [menu]);

  if (!menu) return null;
  const items = MENU_ITEMS[menu.type](menu.payload);
  return (
    <ul className="context-menu" style={{ top: menu.y, left: menu.x }} role="menu">
      {items.map(item =>
        item === '---' ? <li className="divider" /> : <li role="menuitem" onClick={() => { item.onClick(); close(); }}>{item.label}</li>
      )}
    </ul>
  );
}
```

## 影响范围

- 新增 6 个文件
- ui-slice 增 contextMenu state
- WorkflowCanvas 注入 4 个 onContextMenu

## 验证

- [x] 4 个上下文均已接入触发：pane / node / edge / multi-selection
- [x] ESC + 点击外部均能关闭
- [x] 菜单超出视口边界时做基础 clamp，避免溢出窗口
- [x] 复制/粘贴/删除等操作走共享 editor action（与 T04 快捷键共享）
- [x] role/aria 完整：role="menu" / "menuitem"
- [x] Chrome 95 禁用 API/CSS grep 无命中

## 完成标准

- [x] 4 类菜单结构清晰，互不干扰
- [ ] 单元测试：每类菜单 ≥ 2 用例

## 当前实现说明

- 已新增 `context-menu/ContextMenu.tsx`，由 `WorkflowCanvas` 注入 ReactFlow 右键事件。
- 已覆盖画布粘贴/全选/fit view/添加便签，节点复制/剪切/删除/重命名/详情/添加便签，连线删除，多选复制/删除/对齐。
- 未完成项：连线类型切换、组合 group、菜单专项单测矩阵。
