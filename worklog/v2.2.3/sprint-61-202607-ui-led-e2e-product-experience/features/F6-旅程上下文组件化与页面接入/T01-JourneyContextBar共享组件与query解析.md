# T01: JourneyContextBar 共享组件与 query 解析

**优先级**: P0
**状态**: DONE
**依赖**: F1/T01

## 目标

提供一个可复用的旅程上下文条组件和 query 解析工具，避免每个页面手写 `journey`、`modelId`、`standardDraftId` 的判断逻辑。

## 技术设计

- 新增 `JourneyContextBar` 或同等共享组件，放在现有前端共享组件目录中。
- 新增 `useDataProductJourneyContext` hook，解析 `journey`、`sourceId`、`standardDraftId`、`modelId`、`metricId`、`serviceId`。
- 输出统一结构：`enabled`、`stage`、`returnUrl`、`nextUrl`、`evidenceUrl`、`contextLabels`。
- 默认只在 `journey=e2e-data-product` 时渲染。

## 影响范围

- `source/dts-platform-webapp/src/components` 或既有共享组件目录
- `source/dts-platform-webapp/src/pages/*`
- 新增 source-contract 测试文件

## 验证

- [x] source-contract 断言组件包含返回工作台、继续下一步、当前阶段。
- [x] source-contract 断言 hook 解析并保留关键 query 参数。
- [x] `pnpm build` 通过。

## 完成标准

- [x] 页面接入方不需要重复解析 `journey`。
- [x] 组件支持空上下文、部分上下文和完整上下文。
- [x] 文案只描述用户动作，不放产品分析说明。

## 2026-07-10 证据

- 新增 `source/dts-platform-webapp/src/components/journey/JourneyContextBar.tsx`、`useDataProductJourneyContext.ts`、`journeyContext.ts`、`index.ts`。
- `node --test src/components/journey/JourneyContextBar.source-contract.test.ts`：6/6 通过。
- `pnpm build`：通过。
