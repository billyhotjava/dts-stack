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

- 状态：`done`
- 已完成：
  - 运行态继续复用现有 breadcrumb / roll-up 机制
  - 新动作模型已支持 `drill-down` 与 `drill-up`
  - 项目管理模板已预置里程碑图的下钻和上卷相关动作
  - `table` 组件在绑定 card 数据源并配置 drillDown 后，也可直接通过行点击进入下一层
  - `table` / `scroll-board` 的 light-first runtime 现在都支持行点击动作与默认下钻入口
  - 静态模板组件也能保留 drill breadcrumb / roll-up 状态，不再被 card data source 前置条件完全拦住
  - 项目管理模板中的“风险与堵点”表已补 `drillDown + drill-down action`，可在预览态验证下钻和回卷
  - Playwright smoke 已验证项目管理模板的过滤、行点击下钻、breadcrumb 回卷和详情面板

## 风险

- 运行态需要保存额外路径状态，不能污染编辑态配置
