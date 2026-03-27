# SQL Model Delete Async Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让逻辑建模页面删除模型在局域网场景下快速返回，把文件清理移到事务提交后的异步执行，并阻止删除后自动触发详情请求风暴。

**Architecture:** 后端复用现有 `afterCommit + taskExecutor` 模式，把 dbt 文件删除从同步事务中移出；前端删除后只刷新列表并清空当前选中，通过一次性保护标记阻止 auto-select 再次触发详情联动。

**Tech Stack:** Spring Boot, Spring TransactionSynchronization, Mockito, React, TypeScript, node:test

---

### Task 1: 写后端异步删除的失败测试

**Files:**
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelServiceTest.java`

**Step 1: Write the failing test**

- 增加一个用例：
  - 初始化一个可见模型和可删除路径
  - 让 `fileService.canDeleteModelPathAfterRemoving(...)` 返回 `true`
  - 手动开启 `TransactionSynchronizationManager.initSynchronization()`
  - 调用 `service.delete(modelId, "D1")`
  - 断言此时还没有调用 `fileService.deleteFileIfChanged(...)`
  - 取出注册的 synchronization，触发 `afterCommit()`
  - 断言任务被投递到 `taskExecutor`
  - 手动执行捕获到的 `Runnable`
  - 断言最终调用 `fileService.deleteFileIfChanged(modelPath, null)`

**Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=ModelingSqlModelServiceTest test`

**Step 3: Write minimal implementation**

- 先只给服务增加异步调度依赖和空壳方法，让测试能走到正确失败点。

**Step 4: Run test again**

Run: `./mvnw -q -Dtest=ModelingSqlModelServiceTest test`

### Task 2: 实现后端提交后异步文件清理

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`

**Step 1: Inject async executor**

- 给 `ModelingSqlModelService` 增加 `Executor` 或 `TaskExecutor` 依赖，复用平台已有异步线程池。

**Step 2: Extract scheduling helper**

- 新增一个私有方法，例如 `scheduleFileDeletionAfterCommit(String modelPath)`。
- 逻辑：
  - 如果事务同步激活，注册 `TransactionSynchronization.afterCommit()`
  - 否则直接 `taskExecutor.execute(...)`
  - `Runnable` 内只做 `fileService.deleteFileIfChanged(modelPath, null)` 并捕获日志

**Step 3: Wire delete path**

- 在 `delete()` 中保留权限校验和 `repo.delete(model)`。
- 如果 `deleteFile == true`，改为调用异步调度 helper，不再同步删文件。

**Step 4: Run targeted backend tests**

Run: `./mvnw -q -Dtest=ModelingSqlModelServiceTest,ModelFileServiceTest test`

### Task 3: 写前端删除后选择保护的失败测试

**Files:**
- Create: `source/dts-platform-webapp/src/pages/modeling/sqlModelDeleteFlow.helpers.ts`
- Create: `source/dts-platform-webapp/src/pages/modeling/sqlModelDeleteFlow.helpers.test.ts`

**Step 1: Write the failing test**

- 增加用例覆盖：
  - 删除当前激活模型后，返回 `nextActiveModelKey = null`
  - 同时返回 `suppressAutoSelect = true`
  - 用户手动选择模型后，保护标记清除

**Step 2: Run test to verify it fails**

Run: `node --import ./node_modules/.pnpm/tsx@4.19.4/node_modules/tsx/dist/loader.mjs --test src/pages/modeling/sqlModelDeleteFlow.helpers.test.ts`

**Step 3: Write minimal implementation**

- 实现最小 helper，负责删除后状态与手动选择后的状态转换。

**Step 4: Run test again**

Run: `node --import ./node_modules/.pnpm/tsx@4.19.4/node_modules/tsx/dist/loader.mjs --test src/pages/modeling/sqlModelDeleteFlow.helpers.test.ts`

### Task 4: 把前端删除流改成“只刷新列表，不自动补选”

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- Modify: `source/dts-platform-webapp/src/api/platformApi.ts` (only if current timeout helper wiring still需要补齐删除后的列表相关请求)

**Step 1: Add suppress-auto-select state**

- 在页面中增加一个布尔状态或 ref，表示“删除后本轮列表刷新不要自动选中”。

**Step 2: Use helper in delete flow**

- 删除成功后：
  - 清空当前 `activeModelKey`
  - 打开 suppress 标记
  - 刷新模型列表

**Step 3: Gate auto-select effects**

- 修改两个 auto-select `useEffect`：
  - 只有在 suppress 标记关闭时才允许自动补选

**Step 4: Restore normal behavior on manual select**

- 在树节点手动选择模型时，关闭 suppress 标记。

**Step 5: Run frontend tests and build**

Run: `node --import ./node_modules/.pnpm/tsx@4.19.4/node_modules/tsx/dist/loader.mjs --test src/pages/modeling/sqlModelDeleteFlow.helpers.test.ts`

Run: `pnpm build`

### Task 5: 做端到端回归验证

**Files:**
- No code changes required unless regression found

**Step 1: Run focused backend verification**

Run: `./mvnw -q -Dtest=ModelingSqlModelServiceTest,ModelFileServiceTest test`

**Step 2: Run frontend regression gate**

Run: `pnpm build`

**Step 3: Summarize residual risk**

- 记录异步文件清理失败仍可能留下残留文件，但不再阻塞删除接口。
