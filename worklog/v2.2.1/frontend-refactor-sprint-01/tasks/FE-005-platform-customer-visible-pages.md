# FE-005

## 标题

重构 `platform-webapp` 客户可见页面。

## 范围

- `source/dts-platform-webapp/src/pages/foundation/TaskSchedulingPage.tsx`
- 平台工作台、治理、开发相关客户入口的页面头与卡片结构

## 目标

- 清掉客户可见的预留、噪音和假内容
- 统一平台页的 header、metric card、信息卡片、状态区
- 让平台页和 admin 页形成同一产品语言

## 交付

- 先处理调度与工作台类页面
- 后续同样的视觉 contract 可以继续横向套用到其他平台页面

## 当前进度

- 已新增共享页面骨架 `src/components/console-page.tsx`
- `TaskSchedulingPage.tsx` 已重构为任务运维中心入口页
- `workbench/index.tsx` 已重构为工作台总览页
- `workbench/WorkflowCenterPage.tsx` 已重构为统一待办中心
- `explore/etl/TransformPage.tsx` 已改成统一 hero + summary + section card 结构
- `explore/etl/TransformDetailPage.tsx` 已改成任务详情控制台页，并清掉“实时状态（预留）”文案
- `explore/etl/TransformExecutionHistoryPage.tsx` 已改成统一筛选条 + 执行记录面板
- `modeling/DbtFileBrowserPage.tsx` 已改成统一工作区壳层与浅色编辑面板
- `modeling/SqlModelingPage.tsx` 已切到 light-first 入口壳层，编辑区从深色工作台收敛回统一控制台风格
- `governance/GovernanceCenterPage.tsx` 已改成统一治理总览页
- `governance/GlossaryPage.tsx` 已改成标准管理控制台页
- `governance/QualityRulesPage.tsx` 已改成规则中心控制台页
- `governance/TemplatesPage.tsx` 已改成统一标准模板控制台页
- `governance/SubjectAreasPage.tsx` 已改成统一主题域管理控制台页
- `governance/ReferenceCodesPage.tsx` 已改成统一公共码表控制台页
- `catalog/DatasetsPage.tsx` 已改成统一资产门户页
- `catalog/LineagePage.tsx` 已改成统一血缘分析页
- `catalog/MetadataPage.tsx` 已改成统一元数据采集控制台页
- `pnpm -C source/dts-platform-webapp build` 已通过
- 本轮目标页已全部横向套用统一页面 contract

## 验收

- `pnpm -C source/dts-platform-webapp build`
- 页面信息组织明显优于当前旧后台式布局

## 风险

- 平台页面信息密度通常比 admin 更高，不能只做“换皮”，还要处理信息分区

## 本轮验证

- `pnpm -C source/dts-platform-webapp build`
