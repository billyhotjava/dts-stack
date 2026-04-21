# T04: Table 响应式（列宽 / 行高 / 分页）

**优先级**: P1
**状态**: READY
**依赖**: 无

## 目标

Table 组件在容器尺寸变化时：
- 列宽按容器宽度自动分配
- 列过多时支持**横向滚动**（而不是压缩到难以阅读）
- 分页大小根据容器高度动态计算

## 技术设计

### 列宽策略

1. 如果配置了固定 `columnWidth` → 尊重配置
2. 否则平均分配 `containerWidth / columnCount`
3. 最小列宽 = 80 px，小于这个就启用横向滚动

### 横向滚动

父容器 `overflow-x: auto`，Table 内部 `min-width: X`（X = columnCount × minColumnWidth）。

### 行高

行高固定 32 px（或由配置决定），**不自适应**（行高随容器变化会影响用户阅读节奏）。

### 动态分页大小

```ts
const pageSize = Math.max(1, Math.floor((containerHeight - headerHeight - footerHeight) / rowHeight))
```

组件内用 ResizeObserver 监听容器高度，重算 pageSize。

## 影响范围

- Table / DataTable 类组件
- 已有列宽配置不破坏

## 验证

- [ ] 容器变窄时 table 横向滚动出现
- [ ] 容器变高时每页行数增加
- [ ] 列宽不会压缩到不可阅读
- [ ] 行高保持一致

## 完成标准

- [ ] 列宽 auto / fixed 两种模式可用
- [ ] 分页大小跟随容器高
- [ ] 横向滚动体验流畅
