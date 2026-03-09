# AN-007

## 标题

实现动作入口模型 `v1`。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/screens/types.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/PropertyPanel.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/ScreenRuntimeContext.tsx`

## 目标

- 让卡片、表格、看板拥有统一的动作入口，而不是只有跳 URL
- 为项目管理模板预留上行操作入口，但不在本轮接真实后端

## `v1` 动作类型

- `set-variable`
- `drill-down`
- `drill-up`
- `jump-url`
- `open-panel`
- `emit-intent`

## `emit-intent` 约束

- 只负责发出事件名与参数
- 不直接调用业务接口
- 允许后续由宿主或业务层接管

## 验收

- 设计器中可配置动作入口
- 预览态点击后可触发对应动作
- `jump-url` 与旧 `interaction` 跳转保持兼容

## 当前进度

- 状态：`pending`
- 依赖：`AN-001`

## 风险

- 如果直接重写旧 `interaction`，容易造成历史模板跳转回归
