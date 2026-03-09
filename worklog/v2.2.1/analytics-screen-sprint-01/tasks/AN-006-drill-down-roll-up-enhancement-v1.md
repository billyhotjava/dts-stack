# AN-006

## 标题

实现下钻 / 上卷增强 `v1`。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/screens/types.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/ScreenRuntimeContext.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/PropertyPanel.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx`

## 目标

- 让模板可以表达“点击图表进入下一层，再从运行态返回上一层”
- 为项目管理模板提供阶段 -> 风险 -> 问题 / 交付物的层级联动

## `v1` 范围

- 设计器内继续保留现有下钻配置
- 补充运行态的上卷返回状态
- 增加面包屑或当前钻取路径表达
- 支持详情面板式下钻入口

## 验收

- 预览态点击可下钻图表后，变量与路径状态会更新
- 用户可从当前层级返回上一层
- 旧模板无下钻时不受影响

## 当前进度

- 状态：`pending`
- 依赖：`AN-001`

## 风险

- 运行态需要保存额外路径状态，不能污染编辑态配置
