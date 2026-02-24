# P3-04 可视化字段映射面板

`status`: `done`
`priority`: `P3`
`sprint`: `Sprint 2 - 体验优化`
`inspiration`: `DataEase(拖拽字段映射) + Superset(Explore 列映射) + Metabase(字段选择器)`

## 目标

提供可视化的"字段 → 图表轴/维度/度量"拖拽映射面板，替代当前手动输入字段名的配置方式，降低非技术用户配置门槛。

## 当前问题

1. 数据绑定后，`_sourceColumns` 自动识别列信息，但用户仍需在属性面板手动输入字段名。
2. 图表 series 配置为 JSON 数组结构，对业务用户不直观。
3. 字段改名/删除后，图表无法自动感知并提示修复。

## 子任务

### 1. 字段映射面板 UI

**新增组件**: `components/FieldMappingPanel.tsx`

**布局设计**:
```
┌─────────────────────────────────────┐
│ 数据字段           图表映射          │
│ ┌─────────┐       ┌──────────────┐  │
│ │ 名称    │──拖──▶│ X 轴 (维度)  │  │
│ │ 数量    │──拖──▶│ Y 轴 (度量)  │  │
│ │ 日期    │       │ 颜色 (分组)  │  │
│ │ 类别    │       │ 大小         │  │
│ │ 地区    │       │ 排序         │  │
│ └─────────┘       └──────────────┘  │
└─────────────────────────────────────┘
```

**交互**:
- 左侧"数据字段"列表自动从 `_sourceColumns` 生成。
- 字段标签显示列名 + 类型图标（文本/数字/日期）。
- 支持拖拽到右侧目标槽位，也支持点击选择。
- 目标槽位根据图表类型动态变化（见下表）。
- 支持一个字段映射到多个位置（如 X 轴 + 排序）。

### 2. 图表类型与映射槽位

| 图表类型 | 维度(X) | 度量(Y) | 分组(颜色) | 大小 | 角度 |
|---------|:---:|:---:|:---:|:---:|:---:|
| line-chart | ✅ | ✅ (多系列) | ✅ | - | - |
| bar-chart | ✅ | ✅ (多系列) | ✅ | - | - |
| pie-chart | ✅ (名称) | ✅ (值) | - | - | - |
| scatter-chart | ✅ (X) | ✅ (Y) | ✅ | ✅ | - |
| radar-chart | ✅ (指标) | ✅ (值) | ✅ | - | - |
| gauge-chart | - | ✅ (单值) | - | - | - |
| funnel-chart | ✅ (阶段) | ✅ (值) | - | - | - |
| map-chart | ✅ (区域) | ✅ (值) | - | - | - |
| table | ✅ (全列) | - | - | - | - |

### 3. 字段映射到 config 转换器

**新增**: `hooks/fieldMappingTransform.ts`

- 输入：字段映射声明 + CardData
- 输出：生成对应的 `config.xAxisData`、`config.series`、`config.data` 等
- 需处理：
  - 单维度多度量 → 多 series
  - 分组字段 → 按值拆 series
  - 聚合（sum/count/avg/min/max）→ 前端分组聚合
  - 数值格式化

### 4. 字段映射持久化

**存储位置**: `component.config._fieldMapping`

```typescript
interface FieldMapping {
  dimension?: string;       // 维度列名
  measures?: string[];      // 度量列名（多系列）
  groupBy?: string;         // 分组列名
  sizeField?: string;       // 大小映射列名
  aggregation?: 'sum' | 'count' | 'avg' | 'min' | 'max';
  sortField?: string;
  sortOrder?: 'asc' | 'desc';
}
```

### 5. 属性面板集成

**文件**: `PropertyPanel.tsx`

- 在"数据"标签页新增"字段映射"折叠区。
- 当组件绑定了数据源且 `_sourceColumns` 非空时展示。
- 与现有手动配置互斥：使用字段映射后隐藏 series JSON 编辑器。
- 提供"切换到高级模式"入口回到 JSON 编辑。

### 6. 字段变更检测

- 当数据源返回的列发生变化时（`_sourceColumns` diff），检测失效的映射。
- 在属性面板展示警告："字段 '销量' 已不存在于数据源中，请重新映射"。

## Chrome 95 兼容性

- 拖拽使用 `react-dnd`（已在项目中），Chrome 95 ✅。
- 无新 API 依赖。

## 验收标准

- 字段列表正确显示数据源列名和类型。
- 拖拽映射后图表实时更新。
- 映射配置持久化到 config 并可恢复。
- 高级模式与可视化模式可切换。
- 字段删除后给出警告提示。
- Chrome 95 下拖拽交互正常。

## 风险与回滚

- 风险：字段映射与现有手动 config 冲突。
- 回滚：字段映射为可选增强层，不影响现有直接编辑 config 的方式。
