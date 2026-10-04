# PMS-013

## 标题

沉淀项目管理组件包，形成树状进度、甘特、趋势、健康卡等可复用元素。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/project-cockpit/components/`
- 必要的 `source/dts-analytics-webapp/modern/src/pages/screens/` 共享能力

## 目标

- 避免五个主题视图各自重复造轮子
- 为后续升级成更通用的大屏元素打基础

## 交付

- 健康度卡
- 趋势图封装
- 甘特数据适配增强
- 树状进度看板
- 延期归因矩阵

## 验收

- 各视图共享统一组件契约
- 不出现大量视图内重复实现

## 当前进度

- 状态：DONE

## 风险

- 若过度追求一次性通用化，会拉大本 Sprint 范围
