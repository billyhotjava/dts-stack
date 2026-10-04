# T01: checkbox 组件选择列表

**优先级**: P0  
**状态**: DONE  
**依赖**: F3-T01/F3-T02

## 目标

在工作台右侧抽屉中展示当前用户可选择的组件列表，用 checkbox 控制是否显示。

## 技术设计

抽屉列表每行包含：

- checkbox: 控制 `visible`。
- 组件名称: 来自注册表或后端 `availableComponents.title`。
- 组件说明: 用客户语言说明用途。
- 状态标签: 可用、暂不可用、无权限隐藏。
- 顺序操作区域: 预留上移/下移按钮。

交互规则：

- 未授权组件不展示。
- 暂不可用组件可以展示，但 checkbox disabled，并显示 `disabledReason`。
- 勾选变化先写入草稿，不立即调用保存接口。
- 用户取消全部组件是允许行为，首页展示空态。

## 影响范围

- `source/dts-platform-webapp/src/pages/workbench/**`
- `source/dts-platform-webapp/src/components/**`

## 验证

- [x] RED: 测试断言 checkbox 勾选只更新草稿，确认失败。
- [x] GREEN: 抽屉列表实现后测试通过。
- [x] 不可用组件 checkbox disabled 且展示原因。

## 完成标准

- [x] checkbox、名称、说明和状态完整。
- [x] 草稿和已保存状态可区分。
