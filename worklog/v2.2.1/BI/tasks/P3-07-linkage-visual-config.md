# P3-07 联动可视化配置简化

`status`: `done`
`priority`: `P3`
`sprint`: `Sprint 2 - 体验优化`
`inspiration`: `DataEase(同数据集字段自动关联) + Superset(Native Filter 联动面板)`

## 目标

将组件间联动配置从"手动填写变量 Key 和 sourcePath"简化为"可视化连线 + 自动推荐"，降低联动配置的理解成本。

## 当前问题

1. 配置联动需理解"全局变量 → 组件交互映射 → 参数绑定"三层概念。
2. `sourcePath` 需要用户了解 ECharts 回调 params 结构（如 `name`、`seriesName`、`data.value`）。
3. 联动关系不可视化，难以调试和维护。
4. 无法自动推荐相同数据源的组件之间的关联。

## 子任务

### 1. 联动关系可视化面板

**新增组件**: `components/LinkageGraphPanel.tsx`

**布局**: 从 CanvasToolbar 或 ScreenHeader 打开的右侧面板。

**展示内容**:
- 简化的组件拓扑图（节点 = 组件，边 = 变量依赖）。
- 节点显示：组件名 + 类型图标。
- 边显示：变量 Key + 方向箭头（写入 → 消费）。
- 高亮当前选中组件的上下游关系。
- 已有 `interactionGraph.ts` 的循环检测结果展示。

**技术方案**: 基于 SVG 绘制简单拓扑图（无需引入 D3 等重依赖）。

### 2. 快捷联动配置向导

**触发方式**: 在 PropertyPanel 的"交互"区域新增"快速联动"按钮。

**流程**:
1. 展示当前大屏中所有已绑定数据源的组件列表。
2. 选择目标组件 → 自动推荐关联字段。
3. 推荐规则：
   - 同数据源的组件：自动找到相同维度字段。
   - 过滤器组件：自动匹配变量 Key。
   - 地图组件：自动推荐区域名字段。
4. 一键创建：自动生成全局变量 + 交互映射 + 参数绑定。

### 3. sourcePath 智能提示

**文件**: `PropertyPanel.tsx` (交互映射配置区)

- `sourcePath` 输入框改为下拉选择。
- 根据组件类型预置常用路径选项：

| 组件类型 | 常用 sourcePath |
|---------|----------------|
| bar/line/scatter | `name`、`seriesName`、`value`、`data.name` |
| pie/funnel | `name`、`value`、`percent` |
| map | `name`、`data.name`、`data.value` |
| table | `row[0]`、`row[1]`（按列号） |

### 4. 联动调试增强

**增强现有**: `InteractionDebugPanel.tsx`

- 实时展示变量变更流（时间线视图）。
- 点击变量变更事件 → 高亮源组件和目标组件。
- 新增"模拟点击"按钮 → 手动触发特定组件的交互映射。

## Chrome 95 兼容性

- SVG 拓扑图 Chrome 95 ✅。
- 无新 API 依赖。

## 验收标准

- 联动关系图正确展示组件间的变量依赖。
- 快捷联动向导可一键创建完整联动链路。
- sourcePath 下拉选择正确覆盖常用场景。
- 循环依赖在图中以红色标记。
- Chrome 95 下联动面板正常显示。

## 风险与回滚

- 风险：拓扑图在组件数量多时（50+）性能问题。
- 回滚：超过 30 个节点时切换为列表视图。
