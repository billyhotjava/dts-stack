# PMS-009

## 标题

实现计划执行视图，并增强项目管理场景下的甘特图表达。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/project-cockpit/views/`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.ts`

## 目标

- 让计划执行、节点推进和责任负载可以直观看到
- 复用并增强现有 gantt 能力

## 交付

- 计划执行主题视图
- 项目计划甘特图
- 节点 KPI / 即将到期 / 已超期清单
- 甘特映射增强

## 验收

- gantt 可展示项目节点计划与实际
- 节点状态与里程碑推进能联动筛选
- 现有 gantt 映射测试保持通过

## 当前进度

- 状态：DONE

## 风险

- 若 gantt 数据结构与树状视图字段不统一，会导致双份适配成本
