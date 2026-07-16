# T03: 客户验收包 UI 汇总

**优先级**: P1
**状态**: DONE
**依赖**: T02

## 目标

在工作台提供客户验收包视图，汇总模型、指标、服务、质量、权限、运行和审计证据。

## 技术设计

- 工作台证据区域增加“生成客户验收包”动作。
- 初期生成 UI 聚合视图，不要求立即导出 PDF。
- 每条证据都链接回真实页面，不放静态截图或无法追踪说明。

## 影响范围

- `DataManagementWorkbenchPage.tsx`
- `ApiServicesPage.tsx`
- `DataProductsPage.tsx`
- `OpsInstancesPage.tsx`
- `AuditEvidencePage.tsx`

## 验证

- [x] source-contract 断言验收包包含模型、指标、服务、质量、权限、运行、审计。
- [ ] Playwright smoke 截图验收包区域。

## 完成标准

- [x] 客户能用 DTS 页面证明一个数据产品已经从接入走到可消费和可运维。

## 实施证据

- 工作台验收包模型聚合九类证据分组、缺失项和真实页面链接，支持复制 Markdown/下载 JSON。
- `DataProductAcceptancePackage.source-contract.test.ts` 已覆盖证据结构；浏览器截图归入 F9。
