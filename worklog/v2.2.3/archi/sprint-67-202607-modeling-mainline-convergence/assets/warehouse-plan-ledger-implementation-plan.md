# 建设规划台账与编辑归档实施计划

> **执行要求：** 使用 `superpowers:test-driven-development` 按任务先写失败测试；全部编码完成后再统一执行一次 production build，并在完成声明前使用 `dts-chrome95-regression` 与 `superpowers:verification-before-completion`。

**目标：** 为 canonical `WarehousePlan` 补齐可发现的台账、计划头编辑和归档入口，使新用户能修正已创建规划，多计划用户能查找和管理生命周期。

**架构：** 继续以 `/api/modeling/warehouse-plans` 为唯一规划聚合。前端新增一个共享计划头编辑抽屉和一个台账编排页；工作台、详情页与台账只共享 header/editor，不复制 StageProjection 或规划基线。所有写操作使用 `plan-head` ETag，列表、projection、编辑与归档分别管理请求时序。

**技术栈：** React 18、TypeScript、Ant Design、React Router、Axios、Node test、Spring Boot MockMvc、Liquibase、Chrome 95 Playwright。

## 全局约束

- 不调用旧 `/api/modeling/plans`，不复用旧“项目空间管理”聚合。
- 不新增或修改后端规划表；后端现有 PATCH/archive 契约是权威。
- 只允许编辑 `name/objective/scope/ownerId/ownerDepartmentId`。
- `code/onboardingMode/lifecycleStatus/tenantId/version` 只读。
- 无维护权限以及 `PUBLISHED`、`ARCHIVED` 状态不暴露计划头写动作。
- 409 必须保留编辑输入；归档冲突必须重新加载并再次确认。
- 工作台仍只有一个业务主动作；“全部规划”“编辑规划”均为次级动作。
- 390px 页面不能产生 document/body 水平滚动；不使用 Chrome 95 不支持的 API/CSS。
- 保留用户已有的 `AGENTS.md`、`CLAUDE.md` 改动，不做相邻清理。

## Task 1：补齐前端 API 与纯状态规则

**文件：**

- 修改：`source/dts-platform-webapp/src/api/warehousePlanApi.ts`
- 修改：`source/dts-platform-webapp/src/pages/modeling/warehousePlanViewModel.ts`
- 修改：`source/dts-platform-webapp/src/pages/modeling/warehousePlanViewModel.test.ts`
- 新增：`source/dts-platform-webapp/src/api/warehousePlanHeader.source-contract.test.ts`

**步骤：**

1. 先写 API source-contract 失败测试，要求 PATCH 与 archive 使用 canonical URL、`If-Match: "plan-head:{version}"`、`_skipErrorToast`，且 PATCH 输入不包含只读字段。
2. 先写 view-model 失败测试，覆盖生命周期客户语言、可编辑/可归档判定、默认隐藏归档、关键字/负责人/生命周期筛选和归档后列表行为。
3. 运行：

   ```bash
   node --test source/dts-platform-webapp/src/api/warehousePlanHeader.source-contract.test.ts source/dts-platform-webapp/src/pages/modeling/warehousePlanViewModel.test.ts
   ```

   确认因新契约/函数不存在而失败。
4. 最小实现 `UpdateWarehousePlanInput`、`updateWarehousePlan`、`archiveWarehousePlan` 和纯状态函数。
5. 重跑同一命令确认转绿。

## Task 2：实现共享计划头编辑器

**文件：**

- 新增：`source/dts-platform-webapp/src/pages/modeling/components/WarehousePlanHeaderEditor.tsx`
- 新增：`source/dts-platform-webapp/src/pages/modeling/components/WarehousePlanHeaderEditor.source-contract.test.ts`

**步骤：**

1. 先写失败测试，约束可编辑/只读字段、目录负责人选择、字段长度、保存 CAS、409 两种恢复动作、403/404/网络错误客户语言和迟到响应保护。
2. 运行：

   ```bash
   node --test source/dts-platform-webapp/src/pages/modeling/components/WarehousePlanHeaderEditor.source-contract.test.ts
   ```

3. 实现抽屉。打开时以传入 header 填充表单；负责人搜索使用 `searchUsers`，目录失败时保留当前负责人且禁止自由文本伪造账号。
4. 保存成功回传服务端最新 header；冲突时保留表单，允许基于 `currentVersion` 显式重试或 GET 最新版覆盖；组件卸载/切换计划后迟到响应失效。
5. 重跑测试确认转绿。

## Task 3：实现建设规划台账

**文件：**

- 新增：`source/dts-platform-webapp/src/pages/modeling/WarehousePlanLedgerPage.tsx`
- 新增：`source/dts-platform-webapp/src/pages/modeling/WarehousePlanLedgerPage.source-contract.test.ts`

**步骤：**

1. 先写失败测试，覆盖 canonical list、客户端筛选、默认隐藏归档、独立 StageProjection 降级、权限/生命周期行操作、二次确认归档、409 重新确认、空态/初始错误/保留数据重试和无旧 API。
2. 运行：

   ```bash
   node --test source/dts-platform-webapp/src/pages/modeling/WarehousePlanLedgerPage.source-contract.test.ts
   ```

3. 实现台账页：页面主动作“新建规划”跳转工作台创建入口；表格列为名称/编码、开始方式、负责人/部门、生命周期、当前阶段/首要阻塞、操作。
4. StageProjection 使用 `Promise.allSettled` 独立加载；失败只将对应行显示为“证据未知”。归档成功后更新当前行并按筛选移除，不物理删除。
5. 在窄屏让筛选区与行操作可换行，Table 使用受控水平滚动而非撑开 body。
6. 重跑测试确认转绿。

## Task 4：接入工作台、详情、路由和菜单

**文件：**

- 修改：`source/dts-platform-webapp/src/pages/modeling/ModelingWorkbenchPage.tsx`
- 修改：`source/dts-platform-webapp/src/pages/modeling/WarehousePlanDetailPage.tsx`
- 修改：`source/dts-platform-webapp/src/pages/modeling/ModelingWorkbenchPage.source-contract.test.ts`
- 修改：`source/dts-platform-webapp/src/pages/modeling/WarehousePlanDetailPage.source-contract.test.ts`
- 修改：`source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx`
- 修改：`source/dts-platform-webapp/src/routes/sections/dashboard/portalGoldenLineMenu.source-contract.test.ts`
- 修改：`source/dts-admin/src/main/resources/config/data/portal-menu-seed.json`
- 修改：`source/dts-admin/src/main/resources/config/data/role-menu-defaults.json`
- 修改：`source/dts-platform-webapp/src/locales/lang/zh_CN/sys.json`
- 修改：`source/dts-platform-webapp/src/locales/lang/en_US/sys.json`

**步骤：**

1. 先扩展现有 source-contract 测试，要求精确 `/modeling/plans` 台账路由、数仓规划下“建设规划”菜单、role default/locales，以及工作台/详情共享编辑器入口。
2. 运行：

   ```bash
   node --test source/dts-platform-webapp/src/pages/modeling/ModelingWorkbenchPage.source-contract.test.ts source/dts-platform-webapp/src/pages/modeling/WarehousePlanDetailPage.source-contract.test.ts source/dts-platform-webapp/src/routes/sections/dashboard/portalGoldenLineMenu.source-contract.test.ts
   ```

3. 在静态路由中把精确 `modeling/plans` 放在 `modeling/plans/:planId/*` 前；菜单增加稳定 code `sys.nav.portal.warehousePlans`，并依赖现有 seed upsert/default-binding 机制保留已有绑定。
4. 工作台增加“全部规划”和摘要中的“编辑规划”；编辑成功只更新当前 header。详情标题区增加相同编辑器；保存不改变 Tab、planId、baseline 或 projection。
5. 重跑测试确认转绿。

## Task 5：统一验证、Chrome95 与 Sprint 收口

**文件：**

- 修改：`worklog/v2.2.3/sprint-67-202607-modeling-mainline-convergence/it/manual-e2e-guide.md`
- 修改：`worklog/v2.2.3/sprint-67-202607-modeling-mainline-convergence/it/README.md`
- 修改：`worklog/v2.2.3/sprint-67-202607-modeling-mainline-convergence/features/F2-规划输入与分阶段门禁/T05-补齐建设规划台账与编辑归档.md`
- 视真实结果更新：Sprint/F2 README、`sprint-queue.md` 与 evidence 文件。

**步骤：**

1. 运行全部本 Task 定向 Node 测试，以及现有 WarehousePlan Resource/ApplicationService 定向后端测试。
2. 对前端改动运行一次格式/静态检查；然后仅执行一次：

   ```bash
   cd source/dts-platform-webapp && pnpm build
   ```

3. 使用真实 Chrome 95 验证 1366x768 与 390px：台账空态/列表、编辑成功、409 保留输入、归档、只读、错误恢复；检查 console/network 并保存截图。
4. 运行 GitNexus `detect_changes`，确认只影响规划管理、静态路由和菜单预期范围。
5. 只根据真实证据勾选验收项；若浏览器或真实权限链未验证，保持 `IN_PROGRESS` 并明确剩余门禁，不伪报 Sprint 完成。
