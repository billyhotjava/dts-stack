# T05: 筛选器栏 UI 与参数定义

**优先级**: P0
**状态**: READY
**依赖**: F1-T01

## 目标
在看板编辑器顶部添加筛选器栏，编辑模式下可添加/编辑/删除参数

## 技术设计

### 筛选器栏布局
```
┌──────────────────────────────────────────────┐
│ [日期范围▾ 2026-01~2026-03] [部门▾ 全部] [+ 添加] │
└──────────────────────────────────────────────┘
```

### 参数数据结构
复用已有 DashboardDetail.parameters:
```typescript
type DashboardParameter = {
  id: string;        // 参数 ID（如 "date_range"）
  name: string;      // 显示名（如 "日期范围"）
  type: "date" | "string" | "category";  // 类型
  default?: string;  // 默认值
  slug: string;      // URL slug
};
```

### 组件
- `DashboardFilterBar.tsx` (新) — 筛选器栏容器
- 编辑模式: 每个参数显示为可编辑的 Tag（点击编辑名称/类型/默认值），末尾有"+"按钮
- 预览/查看模式: 参数渲染为 AntD Select/DatePicker/Input 控件

## 影响范围
- 新增: `src/pages/dashboard/DashboardFilterBar.tsx`
- 修改: `DashboardEditorPage.tsx` — 集成筛选器栏
- 修改: `DashboardDetailPage.tsx` — 渲染筛选器控件

## 验证
- [ ] 编辑模式下可添加/删除参数
- [ ] 参数类型正确渲染对应控件
- [ ] 参数保存到 dashboard.parameters

## 完成标准
- [ ] 筛选器栏 UI 可用
