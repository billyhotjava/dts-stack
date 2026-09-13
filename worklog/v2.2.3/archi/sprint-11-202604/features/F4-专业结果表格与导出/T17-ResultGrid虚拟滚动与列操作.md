# T17: ResultGrid（虚拟滚动 + 列操作）

**优先级**: P0
**状态**: READY
**依赖**: T16

## 目标

用 `@tanstack/react-table v8` + `@tanstack/react-virtual` 重写结果表格，达到专业数据表格体验。

## 技术设计

### 依赖

```
npm i @tanstack/react-table@^8 @tanstack/react-virtual@^3
```

### 功能清单

| 能力 | 说明 |
|---|---|
| **虚拟滚动** | 行级虚拟化（行高 28px 固定）；列数 >50 时开列级虚拟化 |
| **列宽拖拽** | 每列可调整，宽度存 Tab store `columnWidths: Record<string, number>` |
| **列排序** | 客户端排序；结果 >10k 行提示改 ORDER BY 重查询 |
| **列过滤** | 每列列头下拉：文本模糊 / 数值区间 / 日期范围 / 空值过滤 |
| **列冻结** | 右键列头"Pin Left/Right"，类 Excel |
| **列隐藏/重排** | 右键菜单或列管理器 |
| **单元格选择** | 单选/多选矩形，Ctrl+C 复制为 TSV |
| **NULL 展示** | 灰色斜体 `NULL`，区分空字符串 |
| **类型对齐** | 数值右对齐、日期图标前缀、布尔 ✓/✗ |
| **长文本** | 超出宽度省略 + hover tooltip 完整展开 |
| **行号列** | 固定最左，显示全局行号（含分页偏移） |

### 组件接口

```typescript
interface ResultGridProps {
  executionId: string;
  columns: ColumnMeta[];
  rowCount: number;
  pageSize?: number;
  mode: 'simple' | 'advanced';
}
```

- 内部用 React Query 分页拉数据（游标 or page+size）
- 虚拟滚动到页边界时自动 prefetch 下一页

### 子组件

```
result/
  ResultGrid.tsx                 (主组件，约 250 行)
  ResultGridCell.tsx             (单元格渲染，含类型格式化)
  ResultGridHeader.tsx           (列头，含拖拽/排序/过滤图标)
  ResultGridFilterPopover.tsx    (列过滤弹层)
  ResultGridColumnMenu.tsx       (列右键菜单)
  hooks/useColumnWidths.ts
  hooks/useColumnFilters.ts
```

### 状态管理

- 列宽/冻结/隐藏/过滤 持久化到当前 Tab store（不跨 Tab 共享）
- 用户体验：关闭 Tab 再打开列配置保留

## 影响范围

- 新增 `result/ResultGrid*.tsx` 系列组件
- Tab store 扩展 `gridState`

## 验证

- [ ] 10 万行滚动 60fps（性能录屏）
- [ ] 列宽拖拽流畅
- [ ] 冻结列水平滚动时保持固定
- [ ] 列过滤+排序组合正确
- [ ] 单元格多选 Ctrl+C 得到 TSV
- [ ] 简洁/高级模式下列菜单项数不同
- [ ] 快照测试覆盖 NULL / 长文本 / 数值格式

## 完成标准

- [ ] 所有功能通过手动测试
- [ ] 性能指标达标
- [ ] 单元/组件测试绿
