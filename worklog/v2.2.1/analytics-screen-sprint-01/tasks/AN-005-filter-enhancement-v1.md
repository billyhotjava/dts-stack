# AN-005

## 标题

实现过滤增强 `v1`。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/screens/types.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/ScreenRuntimeContext.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/hooks/useCardDataSource.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/PropertyPanel.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx`

## 目标

- 把现有单点过滤能力提升为模板可复用的全局过滤能力
- 让项目管理与业务模板可以共用一套过滤条和变量绑定规则

## `v1` 范围

- 过滤组件可声明作用变量
- 支持更明确的默认值、占位提示、作用范围文案
- 支持按模板预置常见过滤项组合
- 支持过滤变更后刷新依赖卡片

## 验收

- 过滤组件在设计器可配置
- 预览态变量更新后，绑定卡片能响应
- 现有过滤组件不回归

## 当前进度

- 状态：`pending`
- 依赖：`AN-001`

## 风险

- 查询参数合并链路已经存在，增强时不能破坏旧模板的数据取数
