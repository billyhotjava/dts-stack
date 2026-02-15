# P1-02 全局筛选器与联动引擎

`status`: `in-progress`  
`priority`: `P1`  
`inspiration`: `Superset(Native Filter) + DataEase(低门槛联动配置)`

## 目标

把当前“变量能力”升级为可视化筛选器与统一联动中心。

## 子任务

1. 筛选器组件
- 新增 `filter-select`、`filter-date-range`、`filter-input`。

2. 联动协议
- `InteractionSpec` 支持 `source -> target -> mapping`。
- 内置循环检测与冲突告警。

3. 联动调试面板
- 展示变量值、触发链路、失败节点。

4. 钻取标准化
- 钻取、跳转、筛选刷新统一到同一事件总线。

## 验收标准

- 一个日期筛选器可同时控制多个图表刷新。
- 联动循环时给出可读告警，页面不崩溃。
- 设计态与运行态联动结果一致。

## 风险与回滚

- 风险：复杂联动导致性能抖动。  
- 回滚：加防抖与批量调度，默认串行关键链路。

## 实现记录（2026-02-14）

- 新增筛选器组件类型：`filter-input`、`filter-select`、`filter-date-range`。
- 组件库与设计器支持：
  - `componentLibrary` 新增“筛选器”分类；
  - `ComponentLibraryPanel` 白名单放开；
  - `LayerPanel` 新增图标映射；
  - `specV2` 与类型系统补齐新组件类型。
- 渲染器能力：
  - 三类筛选器组件可在运行态直接写入全局变量；
  - `filter-select` 支持配置选项；
  - `filter-date-range` 支持开始/结束变量双通道输出。
- 联动调试台：
  - `ScreenRuntimeContext` 新增变量事件流（最近100条，含来源/时间）；
  - 新增 `InteractionDebugPanel`，展示实时变量值、循环告警、事件链路。
- 联动图检测增强：
  - 循环检测补充 `sql` 参数绑定消费边；
  - 筛选器组件视作变量发射源参与拓扑检测。
