# T10: Tab 切换/关闭/冲突处理 UI

**优先级**: P0
**状态**: READY
**依赖**: T09

## 目标

实现 Tab 栏 UI 交互：点击切换、拖拽重排、右键菜单、dirty 关闭确认、数量超限提示。

## 技术设计

### TabBar 组件

```
[Query 1]  [Query 2 ●]  [+]         （右侧 active 的带高亮下边框）
```

- `●` 代表 dirty（有未同步改动）
- 关闭按钮悬浮显示 `✕`
- 当前 active tab 底部 2px `#7c83ff` 边框

### 交互细节

| 动作 | 效果 |
|---|---|
| 单击 Tab | 切换为 active，Zustand `setActive` |
| 双击 Tab 标题 | 进入重命名状态（inline input） |
| 右键 Tab | 菜单：重命名 / 关闭 / 关闭其他 / 关闭全部 / 固定到左侧 |
| 拖拽 Tab | 重排顺序，`reorder()` 更新 `sort_order` |
| 点击 `+` | 新开 Tab，默认 title = `Query {N+1}`，继承当前 active Tab 的 `datasourceId` |
| Ctrl+W | 关闭当前 Tab |
| Ctrl+T | 新开 Tab |
| Ctrl+Tab | 切到下一个 Tab |

### Dirty 关闭确认

```typescript
async function closeTab(id: string) {
  const tab = getTab(id);
  if (tab.dirty) {
    const ok = await Modal.confirm({
      title: '关闭 Tab',
      content: '该 Tab 有未同步的修改，确认关闭吗？',
      okText: '关闭',
      cancelText: '取消',
    });
    if (!ok) return;
  }
  await store.closeTab(id);
}
```

### 数量超限

- `+` 按钮在数量 ≥30 时 disabled + 提示 "最多 30 个 Tab，请关闭部分后再新开"
- 后端 429 时前端 fallback 提示

### 冲突刷新提示

- 收到 409 时顶部出现黄色 banner：`⚠ 该 Tab 在其他设备被修改，已刷新。查看差异`（后者预留，不做实现）

## 影响范围

- 新增 `tabs/TabBar.tsx`、`tabs/TabItem.tsx`
- 快捷键注册到全局（而非 Monaco 内）

## 验证

- [ ] 5 种交互（点击/双击/右键/拖拽/快捷键）全部生效
- [ ] Dirty Tab 关闭有确认弹框
- [ ] 30 个 Tab 时无法新开
- [ ] 冲突时 banner 正确显示
- [ ] 无障碍：键盘可 Tab 聚焦，Enter 激活

## 完成标准

- [ ] 所有交互通过手动测试与组件测试
- [ ] 文件行数 ≤ 300
