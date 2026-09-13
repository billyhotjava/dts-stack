# T03：修复 URL 上下文与前端质量门禁

**优先级**：P0  
**状态**：DONE  
**依赖**：F1/T01

## 目标

自动选择建设计划后，`planId` 成为规范 URL 状态并可刷新恢复；同时修复创建计划弹窗的无效嵌套 label 和本批次 Biome 红灯。

## 技术设计（Contract-first）

- **输入契约**：`/modeling/workbench?module=...`、可读计划列表、可选请求 `planId`。
- **输出契约**：默认计划确定后使用 replace 写入 `planId`；后续 module/workspaceView/asset 更新基于最新 URL，不得丢失计划上下文。
- **数据流**：URL search → 计划装载 → 选择/规范化 → Shell 导航 → 刷新恢复。
- **错误路径**：请求计划不存在时清除无效上下文并显示既有错误态；列表为空时不得生成空 `planId`。
- **UI 交互**：创建计划模式卡使用可访问的可点击容器，不产生 label 嵌套；键盘与 Radio 仍可操作。
- **复用点**：复用 `updateModelingWorkspaceSearch`，不新增路由状态源。

## 影响范围

- `ModelingWorkbenchPage.tsx` 与 route helper/tests
- `WarehousePlanCreateModal.tsx`
- 本批次 Biome 报错的 source-contract/E2E 文件

## 验证（RED→GREEN）

- [x] RED：自动选择计划后缺少 `planId` 的测试失败。
- [x] RED：卡片产生嵌套 label 的可访问性测试失败。
- [x] GREEN：刷新保持 `planId/module/workspaceView/asset`。
- [x] TypeScript 与 targeted Biome 全绿。

## Definition of Done

- [x] `planId` 不再是 E2E 可选值。
- [x] 创建模式卡满足键盘和 Chrome 95 基线。
- [x] 页面 source-contract 同步更新。

## 2026-07-31 验证证据

- 路由、页面与菜单 source-contract：45/45 GREEN。
- Vitest（兼容路由、模式卡、只读屏障）：14/14 GREEN。
- `tsc --noEmit`、targeted Biome、Chrome 95 兼容生产构建均 GREEN。
- 模式卡测试移至 `e2e/support/`，生产构建不再包含 5.6 MB 测试 chunk。
- 生产认证工作区旅程 1/1 PASS；刷新后保持 `planId/module/workspaceView`，请求失败与业务写请求均为 0。
