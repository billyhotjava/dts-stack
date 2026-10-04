# T01: 验收包数据模型与 UI 容器

**优先级**: P1
**状态**: DONE
**依赖**: F7/T01

## 目标

定义客户验收包的数据结构和工作台 UI 容器，为后续证据聚合、导出和客户演示打基础。

## 技术设计

- 新增 `DataProductAcceptancePackage` 前端类型。
- 工作台增加验收包区域或抽屉入口。
- 验收包分为：来源、标准、模型、指标、服务、质量、权限、运行、审计。
- 初期以 UI 聚合已有链接为主，不阻塞在复杂后端汇总 API。

## 影响范围

- `DataManagementWorkbenchPage.tsx`
- journey 状态模型
- `assets/page-capability-matrix.md`

## 验证

- [x] source-contract 断言验收包包含 9 类证据分组。
- [x] `pnpm build` 通过。

## 完成标准

- [x] 客户能在一个入口看到数据产品交付证据。
- [x] 缺失证据不显示为空白，而是显示下一步补齐动作。
