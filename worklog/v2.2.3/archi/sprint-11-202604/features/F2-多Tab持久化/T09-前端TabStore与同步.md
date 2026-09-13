# T09: 前端 Zustand tab store + localStorage + 防抖同步

**优先级**: P0
**状态**: READY
**依赖**: T08

## 目标

建立前端 Tab 状态管理，支持内存态、localStorage 兜底、后端防抖同步三层持久化。

## 技术设计

### Zustand store

```typescript
// tabs/useTabStore.ts
interface TabState {
  id: string;
  title: string;
  sqlText: string;
  engine: Engine;
  datasourceId: string | null;
  schemaContext: string | null;
  cursor: { line: number; column: number };
  selection: MonacoRange | null;
  lastExecutionId: string | null;
  resultSnapshot: ResultSnapshot | null;  // 内存态，不持久化
  dirty: boolean;
  updatedAt: number;
}

interface TabStore {
  tabs: TabState[];
  activeTabId: string;
  hydrated: boolean;

  hydrate(): Promise<void>;              // 启动时拉后端
  openTab(init?: Partial<TabState>): string;
  closeTab(id: string): Promise<void>;
  updateTab(id: string, patch: Partial<TabState>): void;
  setActive(id: string): void;
  reorder(from: number, to: number): void;
  syncDirty(): Promise<void>;            // 批量推送
}
```

### 三层持久化

1. **内存**：Zustand store 运行时状态，含 `resultSnapshot`
2. **localStorage**：每次 `updateTab` 立即写轻量字段（不含 resultSnapshot），防刷新丢失
3. **后端**：防抖 2s 批量 PATCH/batch；关闭 Tab 时立即 DELETE

### 防抖同步

```typescript
const debouncedSync = debounce(() => store.syncDirty(), 2000);
store.subscribe((state, prev) => {
  if (state.tabs !== prev.tabs) debouncedSync();
});
```

### Hydrate 逻辑

1. 启动时调 `GET /api/sql/v2/tabs` 拉服务端 Tab 列表
2. 合并 localStorage 里的未同步数据：
   - `local.updatedAt > remote.updatedAt` → push 到服务端
   - 否则用 remote 覆盖 local
3. 设置 `hydrated = true`，UI 渲染 Tab 栏

### 冲突处理

- 收到 409 Conflict：弹 toast "Tab 在其他设备被修改，已刷新"
- 自动把服务端版本覆盖本地，不丢失用户当前打字（通过 Monaco 的 `undo` 保留）

## 影响范围

- 新增 `tabs/useTabStore.ts`
- 新增 `api/sqlIdeTabs.ts`（HTTP 封装）
- 全局在 `SqlIde` 根组件 mount 时调 `hydrate()`

## 验证

- [ ] 打开 2 个 Tab，刷新浏览器后恢复
- [ ] 关闭浏览器前 1s 内修改，下次打开仍在（localStorage）
- [ ] A 设备改 Tab，B 设备进入能看到最新版本
- [ ] A/B 同时改同一 Tab，后改的胜出，先改的收到冲突提示
- [ ] 单元测试覆盖 hydrate 合并逻辑、防抖同步

## 完成标准

- [ ] Store 实现完成，通过上述验证
- [ ] Devtools 可见状态变化
