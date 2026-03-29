# T03: Grid 拖拽布局集成

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
集成 react-grid-layout 实现卡片拖拽调整位置和大小，实时渲染卡片内容

## 技术设计

### Grid 配置
- 列数: 12（标准响应式网格）
- 行高: 80px
- margin: [16, 16]
- 可拖拽: isEditing 模式下
- 可调整大小: isEditing 模式下
- minW: 3, minH: 2（最小 3 列宽 × 160px 高）

### Layout ↔ DashboardCard 映射
```typescript
// DashboardCard → react-grid-layout Layout
function toGridLayout(dc: DashboardCard): Layout {
  return {
    i: String(dc.id || dc.card_id),
    x: dc.col ?? 0,
    y: dc.row ?? 0,
    w: Math.min(dc.size_x ?? 6, 12),  // 24列→12列映射: size_x/2
    h: dc.size_y ?? 4,
    minW: 3,
    minH: 2,
  };
}

// react-grid-layout Layout → DashboardCard 更新
function fromGridLayout(layout: Layout, dc: DashboardCard): DashboardCard {
  return { ...dc, col: layout.x, row: layout.y, size_x: layout.w, size_y: layout.h };
}
```

### 卡片渲染
每个 Grid item 内部渲染 `DashboardEditorCard`:
- 编辑模式: 顶部标题栏（卡片名 + 删除按钮），下方 ChartRenderer
- 预览模式: 无标题栏操作，纯 ChartRenderer
- 数据加载: 对每张卡片调用 analyticsApi.queryCard(cardId)
- 加载/错误状态显示

### 性能考虑
- 卡片数据按需加载（可见时加载）
- 拖拽过程中不重新查询数据
- onLayoutChange 只更新位置，不触发重新渲染图表

## 影响范围
- 新增: `src/pages/dashboard/DashboardEditorGrid.tsx`
- 新增: `src/pages/dashboard/DashboardEditorCard.tsx`
- 修改: `DashboardEditorPage.tsx` — 集成 Grid

## 验证
- [ ] 卡片可拖拽移动
- [ ] 卡片可调整大小（右下角拖拽手柄）
- [ ] 拖拽后位置正确保存
- [ ] 卡片内容实时渲染
- [ ] 最小尺寸限制有效

## 完成标准
- [ ] react-grid-layout 集成完成
- [ ] 拖拽体验流畅
- [ ] 布局变更正确同步到 DashboardCard 数据
