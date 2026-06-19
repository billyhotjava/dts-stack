# T03: 高密度数据表 CompactTable

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

交付高信息密度数据表组件 CompactTable，统一全原型表格的分页/密度/数字对齐约定，命名与 API 对齐现网。

## 技术设计

- **文件**：`src/components/table/CompactTable.tsx` + `src/components/table/index.ts`，对齐现网 `source/dts-platform-webapp/src/components/table/CompactTable.tsx`。
- **基座**：封装 AntD `Table`，`CompactTableProps<T> extends Omit<TableProps<T>, "columns">` + `CompactColumns<T>`。
- **分页约定**（对齐 memory「分页统一约定」）：
  - 默认 `defaultPageSize = 10`（常量 `DEFAULT_PAGE_SIZE`），`pageSizeOptions` 收敛（如 `[10, 20, 50]`）。
  - **切换每页条数必刷新**：`onChange`/`onShowSizeChange` 触发数据重取；切 size 时重置到第 1 页。
  - 受控分页时 `useEffect` 依赖须含 `pageSize`；注意 `pageNum` 0/1-based 基准与现网一致。
- **密度与对齐**：紧凑行高（Swiss 高密度），数据/数值列应用 `tabular-nums`（取 T01 `--font-numeric` token）保证等宽对齐。
- **可选**：随手搬现网 `RecordDetailDrawer`（行详情抽屉），便于后续阶段表格复用——非必须，按工作量取舍。

## 影响范围

- 新增 `src/components/table/CompactTable.tsx`、`index.ts`（+ 可选 `RecordDetailDrawer`）。
- 依赖 F2-T01 token；被后续全部阶段列表视图消费（如 S4 列表视图、S5 资产目录）。

## 验证

- [ ] 预览页渲染一张 CompactTable，默认每页 10 条。
- [ ] 切换每页条数触发数据刷新且重置到第 1 页。
- [ ] 数值列 tabular-nums 等宽对齐。
- [ ] 类型 `CompactTableProps<T>` / `CompactColumns<T>` 与现网形状一致。

## 完成标准

- [ ] CompactTable 默认 10 条/页、切页刷新、tabular-nums 对齐三条约定全部满足。
- [ ] 命名/类型/默认值对齐现网，回植友好。
- [ ] 可作为后续所有 sprint 列表视图的统一表格基座。
