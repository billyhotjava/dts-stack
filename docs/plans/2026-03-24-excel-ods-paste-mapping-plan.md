# Excel ODS 字段粘贴映射 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 Excel 入湖创建页增加“粘贴 ODS 字段列表后按顺序自动映射 Excel 列”的能力。

**Architecture:** 保持后端协议不变，只扩展文件入湖步骤的前端状态和交互。核心逻辑下沉到独立 helper，页面只负责弹窗输入、触发映射和展示结果，避免继续把规则堆进大组件。

**Tech Stack:** React 18, TypeScript, Ant Design, node:test, tsx

---

### Task 1: 提取粘贴字段解析与映射 helper

**Files:**
- Create: `source/dts-platform-webapp/src/pages/explore/etl/fileOdsPasteMapping.helpers.ts`
- Test: `source/dts-platform-webapp/src/pages/explore/etl/fileOdsPasteMapping.helpers.test.ts`

**Step 1: Write the failing test**

覆盖：
- 多分隔符解析
- 顺序覆盖字段
- Excel 多余列标记 `_odsExtra`
- ODS 多余字段输出未匹配列表

**Step 2: Run test to verify it fails**

Run: `pnpm -C source/dts-platform-webapp exec tsx --test src/pages/explore/etl/fileOdsPasteMapping.helpers.test.ts`

Expected: FAIL with `ERR_MODULE_NOT_FOUND`

**Step 3: Write minimal implementation**

实现：
- `parsePastedOdsFields(text)`
- `applyPastedOdsFieldsToFileColumns(columns, pastedFields)`

**Step 4: Run test to verify it passes**

Run: `pnpm -C source/dts-platform-webapp exec tsx --test src/pages/explore/etl/fileOdsPasteMapping.helpers.test.ts`

Expected: PASS

### Task 2: 接入 FileBasicStep UI

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/steps/FileBasicStep.tsx`
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/steps/types.ts`

**Step 1: Write the failing test**

补 helper 层行为测试，锁定 UI 接入需要的数据结构。

**Step 2: Run test to verify it fails**

Run: `pnpm -C source/dts-platform-webapp exec tsx --test src/pages/explore/etl/fileOdsPasteMapping.helpers.test.ts`

Expected: FAIL because the returned shape does not yet support UI needs

**Step 3: Write minimal implementation**

在文件步骤中：
- 新增“粘贴 ODS 字段”按钮
- 新增弹窗输入框
- 调用 helper 后更新 `fileUploadResult.columns`
- 展示未匹配字段

**Step 4: Run test to verify it passes**

Run: `pnpm -C source/dts-platform-webapp exec tsx --test src/pages/explore/etl/fileOdsPasteMapping.helpers.test.ts src/pages/explore/etl/transformCreateAsyncRun.helpers.test.ts`

Expected: PASS

### Task 3: 回归验证与文档同步

**Files:**
- Modify: `worklog/v2.2.2/sprint-11-202603/README.md`
- Modify: `worklog/v2.2.2/sprint-11-202603/features/F1-创建页与表单状态优化/README.md`
- Modify: `worklog/v2.2.2/sprint-11-202603/features/F1-创建页与表单状态优化/T04-支持粘贴ODS字段列表自动映射Excel列.md`

**Step 1: Run focused tests**

Run:

```bash
pnpm -C source/dts-platform-webapp exec tsx --test \
  src/pages/explore/etl/fileOdsPasteMapping.helpers.test.ts \
  src/pages/explore/etl/transformCreateAsyncRun.helpers.test.ts
```

**Step 2: Run build sanity check**

Run: `pnpm -C source/dts-platform-webapp build`

Expected: PASS, unless blocked by unrelated concurrent workspace changes; if blocked, record the exact external file.

**Step 3: Update sprint docs**

同步记录设计、实现和验证结果。
