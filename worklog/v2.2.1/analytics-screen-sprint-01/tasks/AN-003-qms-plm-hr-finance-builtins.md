# AN-003

## 标题

新增 `QMS / PLM / HR / 财务` 4 套内置模板。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/screens/screenTemplates.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/componentLibrary.ts`

## 目标

- 提供客户可直接选用的业务模板，而不是继续依赖泛行业模板
- 模板结构默认内置全局过滤区、主 KPI 区、趋势/排行/表格混合布局

## 模板边界

- `QMS`：质量事件、CAPA、偏差、稽核、整改闭环
- `PLM`：研发阶段门、变更单、BOM 影响、发布节奏
- `HR`：编制、到岗、培训、绩效、离职风险
- `财务`：预算、执行、成本、回款、应收应付

## 验收

- 4 套模板都能从模板库创建
- 页面布局完整，不依赖未实现组件
- 模板预览态具备基础过滤与下钻入口

## 当前进度

- 状态：`done`
- 完成项：
  - 已新增 `qms-cockpit`
  - 已新增 `plm-cockpit`
  - 已新增 `hr-cockpit`
  - 已新增 `finance-execution-cockpit`

## 风险

- `screenTemplates.ts` 体积会继续增长，必要时要抽 helper 组织结构
