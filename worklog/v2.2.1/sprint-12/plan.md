# Modeling Action Unification Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让逻辑建模页只保留顶部工具栏作为执行入口，流水线退化为只读状态展示，并按方案 1 将主流程与辅助动作分层。

**Architecture:** 保留 `SqlModelingPage` 顶部工具栏承担执行动作，简化 `ModelPipeline` 为纯展示组件；顶部主流程收敛为 `编译 / 测试 / 上线`，将 `提交变更 / 同步模型 / 文档 / 回退` 合并进 `更多` 菜单，底部 Git 面板去掉重复提交入口。先不改后端 dbt 行为，只收口前端入口与交互。

**Tech Stack:** React 18, TypeScript, antd, Vite

---

### Task 1: 为只读流水线补失败测试

**Files:**
- Create: `source/dts-platform-webapp/src/pages/modeling/modelPipeline.helpers.ts`
- Create: `source/dts-platform-webapp/src/pages/modeling/modelPipeline.helpers.test.ts`

**Step 1: Write the failing test**

- 验证流水线步骤配置全部标记为只读
- 验证顶部工具栏保留执行入口时，流水线不需要 selector/action 依赖

**Step 2: Run test to verify it fails**

Run: `cd source/dts-platform-webapp && pnpm exec tsx --test src/pages/modeling/modelPipeline.helpers.test.ts`
Expected: FAIL because helper does not exist yet

**Step 3: Write minimal implementation**

- 提供流水线步骤元信息 helper
- 明确四个阶段只用于展示，不暴露执行动作

**Step 4: Run test to verify it passes**

Run: `cd source/dts-platform-webapp && pnpm exec tsx --test src/pages/modeling/modelPipeline.helpers.test.ts`
Expected: PASS

### Task 2: 将 ModelPipeline 改为只读状态组件

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/ModelPipeline.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- Test: `source/dts-platform-webapp/src/pages/modeling/modelPipeline.helpers.test.ts`

**Step 1: Write the failing test**

- 校验新的 helper 契约不再要求执行入口依赖

**Step 2: Run test to verify it fails**

Run: `cd source/dts-platform-webapp && pnpm exec tsx --test src/pages/modeling/modelPipeline.helpers.test.ts`

**Step 3: Write minimal implementation**

- 从 `ModelPipeline` 中移除：
  - `triggerDbtRun`
  - `triggerDbtTest`
  - `commitDbtChanges`
  - `syncDbtModels`
  - `modelSelector`
  - `onStatusChange`
  - `onRefresh`
  - 弹窗和点击执行逻辑
- 保留步骤状态渲染与最终状态 tag
- 在 `SqlModelingPage` 中按新接口传参

**Step 4: Run tests to verify they pass**

Run: `cd source/dts-platform-webapp && pnpm exec tsx --test src/pages/modeling/modelPipeline.helpers.test.ts`
Expected: PASS

### Task 3: 顶部主流程与更多菜单重构

**Files:**
- Create: `source/dts-platform-webapp/src/pages/modeling/modelingToolbar.helpers.ts`
- Create: `source/dts-platform-webapp/src/pages/modeling/modelingToolbar.helpers.test.ts`
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`

**Step 1: Write the failing test**

- 校验顶部主流程按钮顺序为 `编译 / 测试 / 上线`
- 校验 `更多` 菜单包含 `提交变更 / 同步模型 / 文档 / 回退`
- 校验底部 Git 面板不再渲染内联提交入口

**Step 2: Run test to verify it fails**

Run: `cd source/dts-platform-webapp && pnpm exec tsx --test src/pages/modeling/modelingToolbar.helpers.test.ts`
Expected: FAIL because helper does not exist yet

**Step 3: Write minimal implementation**

- 新增顶部主流程与次级动作 helper
- 在 `SqlModelingPage` 顶部渲染三个主流程按钮
- 将 `提交变更 / 同步模型 / 文档 / 回退` 收入 `更多`
- 复用现有 Git 提交逻辑，通过顶部弹窗提交
- 底部 Git 面板去掉内联输入和提交按钮

**Step 4: Run tests to verify they pass**

Run: `cd source/dts-platform-webapp && pnpm exec tsx --test src/pages/modeling/modelingToolbar.helpers.test.ts`
Expected: PASS

### Task 4: 构建验证与收口

**Files:**
- Verify: `source/dts-platform-webapp/src/pages/modeling/ModelPipeline.tsx`
- Verify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`

**Step 1: Run build**

Run: `cd source/dts-platform-webapp && pnpm build`
Expected: PASS

**Step 2: Manual verification checklist**

- 顶部存在 `编译 / 测试 / 上线`
- `更多` 中包含 `提交变更 / 同步模型 / 文档 / 回退`
- 流水线仍显示四阶段状态
- 流水线不再是按钮，不可点击
- 页面上不再存在第二套“发布上线”执行入口
- 底部 Git 区不再出现内联“提交信息 + 提交”入口
