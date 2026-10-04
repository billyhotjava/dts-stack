# P1-02 全局筛选器与联动引擎

`status`: `done`  
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
- 事件总线标准化增量（2026-02-15）：
  - `ScreenRuntimeContext` 事件流升级为统一事件类型：`variable/filter/interaction/drill-down/drill-up/jump`。
  - `InteractionDebugPanel` 新增事件类型筛选与类型列，并补充 `meta` 详情列，支持按链路快速定位问题。
  - 图表联动新增“点击跳转”能力：
    - 属性面板支持配置 `jumpUrlTemplate/jumpOpenMode`；
    - 支持模板占位符：`{{name}}/{{seriesName}}/{{value}}/{{data.name}}`。
  - 钻取链路补充事件埋点：
    - 下钻与回退均写入统一事件流，便于排障与行为回放。
- 筛选器数据源绑定增强（2026-02-16）：
  - `filter-select` 新增选项来源模式：`manual`（手工配置）/`data`（从组件数据源自动提取）；
  - 支持配置值字段、标签字段、最大选项数；
  - 运行时自动基于当前组件 `CardData` 生成去重选项列表，便于做“维度即筛选项”的联动配置。
- 联动稳定性增强（2026-02-16）：
  - `filter-input` 新增 `debounceMs` 配置（0-5000ms，默认 300ms）；
  - 渲染器引入输入防抖写变量机制（包含 blur 强制 flush），降低高频输入导致的查询抖动；
  - 防抖提交事件标记 `:debounced`，便于在联动调试台区分实时写入与延迟写入。
- 变量驱动显隐增强（2026-02-17）：
  - 组件新增“变量可见条件”配置（`visibilityRuleEnabled/visibilityVariableKey/visibilityMatchMode/visibilityMatchValues`）；
  - 支持 `等于/不等于/为空/非空` 四种显隐规则；
  - 规则在预览/公开/导出模式生效，可与 `tab-switcher` 组合实现“Tab 切场景”。
  - `tab-switcher` 新增联动提效工具：
    - 一键将 Tab 选项分配到图表/表格组件显隐规则（批量写入 `visibilityRule*`）；
    - 一键清理该 Tab 变量下的显隐规则，便于快速回退。
- 显隐规则匹配模式扩展（2026-02-20）：
  - 新增 `contains/not-contains/starts-with/ends-with` 四种匹配模式；
  - 属性面板与运行时规则解析同步升级，支持文本变量按包含/前后缀快速联动；
  - 兼容既有 `equals/not-equals/empty/not-empty` 规则，不破坏旧屏稿行为。
- 显隐规则前后端一致性校验（2026-02-20）：
  - 前端 `specV2.validateScreenPayload` 增加 `visibilityMatchMode` 白名单校验与 `visibilityVariableKey` 必填校验；
  - 后端 `ScreenSpecValidator` 增加同口径校验，防止绕过前端提交非法模式；
  - 新增 `ScreenSpecValidatorTest` 覆盖“非法模式拒绝/合法 contains 模式通过”。
- 联动映射协议增强（2026-02-20）：
  - `ComponentInteractionMapping` 新增 `transform/fallbackValue`：
    - 支持 `raw/string/number/lowercase/uppercase` 值转换；
    - 支持空值回退默认值，降低点击参数缺失导致的联动失效。
  - 联动配置面板升级：
    - `sourcePath` 改为可输入（含候选路径），支持 `data.code` 等自定义路径；
    - 新增“值转换”“默认值”配置项。
  - 运行时执行升级：
    - 图表点击联动写变量前先执行转换和回退策略，保持运行态行为可预期。
- 联动校验与循环检测补强（2026-02-20）：
  - 前端 `specV2` 增加 `interaction.mappings` 基础校验（`variableKey/sourcePath/transform`）；
  - `interactionGraph.consumeVariables` 补齐 `metric` 数据源参数绑定消费边，循环检测结果更贴近真实运行链路。
- 联动协议前后端一致性补齐（2026-02-21）：
  - 后端 `ScreenSpecValidator` 新增 `interaction` 对象校验（`mappings` 数组、`variableKey/sourcePath` 必填）；
  - 后端新增 `transform` 白名单校验（`raw/string/number/lowercase/uppercase`），与前端 `specV2` 口径一致；
  - 新增 `ScreenSpecValidatorTest` 用例覆盖“非法 transform 拒绝 / 合法映射通过”。
