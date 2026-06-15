# T01: 主题聚合模型与 TDD 契约

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: 无

## 目标

先用测试定义主题看板的业务口径：业务主题优先，链路状态、治理、消费和运维为主题状态来源。

## 技术设计

- 新增 `dataManagementThemeModel.ts`，把 `GoldenChainSummary` 和 `GoldenChainDetail` 聚合为主题卡片。
- 默认主题为经营分析、质量管理、项目交付、客户服务。
- 链路根据 displayName/source/失败原因等业务词归入主题；无法识别时归入经营分析。
- 主题状态字段包括数据可用、治理状态、消费状态、运行健康、最近更新、下一步动作。

## 影响范围

- `source/dts-platform-webapp/src/pages/workbench/dataManagementThemeModel.ts`
- `source/dts-platform-webapp/src/pages/workbench/dataManagementThemeModel.test.ts`

## 验证

- [ ] `pnpm exec vitest run src/pages/workbench/dataManagementThemeModel.test.ts`

## 完成标准

- [ ] RED 测试先失败。
- [ ] GREEN 后覆盖主题归类、阻断状态、空态主题和业务动作。
