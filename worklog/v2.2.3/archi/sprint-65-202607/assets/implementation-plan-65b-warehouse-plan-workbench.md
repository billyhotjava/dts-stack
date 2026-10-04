# Sprint-65b WarehousePlan Workbench Implementation Plan

**Goal:** 让用户从 `/modeling/workbench` 以业务目标或现有数据两个入口创建同一种 canonical WarehousePlan，并用 `planId + StageProjection` 驱动一个主动作、九站状态和六个编辑 Tab。

**Sprint 归属:** [Sprint-65：经典数仓规划内核与黄金主线重构](../README.md)

## 不变量

- 两个起点只写 `onboardingMode`，不产生两类计划、两套表或两套流程。
- `StageProjection` 是唯一完成状态源；URL、session、浏览记录和前端推断不得写成完成状态。
- 建模域新链路只新增 `planId`；`planningId/processId/warehouseLayer/modelingMode` 仅兼容读取和透传。
- 空计划页面只呈现一个主动作；有计划时唯一主动作来自 `StageProjection.nextAction`。
- 六个 Tab 只组织编辑与专业入口，不自持完成度。

## Task 1：类型化 API 与纯投影模型

**Files**

- Create: `source/dts-platform-webapp/src/api/warehousePlanApi.ts`
- Create: `source/dts-platform-webapp/src/pages/modeling/warehousePlanViewModel.ts`
- Create: `source/dts-platform-webapp/src/pages/modeling/warehousePlanViewModel.test.ts`

**TDD**

1. 先断言九个稳定 `StageCode`、状态文案和下一步路径保留 `planId`。
2. 再实现类型、API 读取/创建和纯映射。

## Task 2：双起点数据建设工作台

**Files**

- Modify: `source/dts-platform-webapp/src/pages/modeling/ModelingWorkbenchPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/ModelingWorkbenchPage.source-contract.test.ts`

**TDD**

1. 先把旧“自动跳主题域”契约改为工作台契约并观察失败。
2. 实现计划选择、双起点创建、当前阶段、唯一阻塞、唯一主 CTA 和九站只读轨迹。
3. 无计划、加载失败与 UNKNOWN 均不伪造完成状态。

## Task 3：六 Tab 计划详情与深链

**Files**

- Create: `source/dts-platform-webapp/src/pages/modeling/WarehousePlanDetailPage.tsx`
- Create: `source/dts-platform-webapp/src/pages/modeling/WarehousePlanDetailPage.source-contract.test.ts`
- Modify: `source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx`

**TDD**

1. 断言六个 Tab、`modeling/plans/:planId/*` 路由和专业入口。
2. 实现计划摘要、规划基线与专业页面深链；Tab 不保存完成状态。

## Task 4：planId 收敛与退役守卫

**Files**

- Modify: `source/dts-platform-webapp/src/components/journey/journeyContext.ts`
- Modify: `source/dts-platform-webapp/src/pages/modeling/businessModelingContext.ts`
- Modify: `source/dts-platform-webapp/src/pages/modeling/modelingJourneyContext.ts`
- Create: `source/dts-platform-webapp/src/pages/modeling/warehousePlanJourneyConvergence.source-contract.test.ts`

**TDD**

1. 先断言 `planId` 是新建模链路唯一必要上下文，旧四参数仅兼容。
2. 禁止 `/modeling/workbench` 调用旧前端完成度解析器。
3. 冻结旧 context 消费者基线，避免出现第五套旅程骨架。

## Verification

```bash
cd source/dts-platform-webapp
node --test --import tsx \
  src/pages/modeling/warehousePlanViewModel.test.ts \
  src/pages/modeling/ModelingWorkbenchPage.source-contract.test.ts \
  src/pages/modeling/WarehousePlanDetailPage.source-contract.test.ts \
  src/pages/modeling/warehousePlanJourneyConvergence.source-contract.test.ts
pnpm build
```

最后执行 Chrome 95 空计划、阻塞计划、UNKNOWN 证据和窄屏 smoke，并更新 Sprint-65 IT 证据。
