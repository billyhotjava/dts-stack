# 逻辑建模工具栏与产出表回退补完 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 补齐逻辑建模页 `提交变更`、`同步模型`、`文档`、`回退` 的能力定义、错误语义和验收闭环

**Architecture:** 以 `Sprint-24/F7` 为载体，保持现有工具栏结构不变，分别收口前端动作定义、后端接口语义和产出表检查链路。重点不是重做整套工具栏，而是把当前半完成能力补到可交付状态。

**Tech Stack:** React + TypeScript + Ant Design，Spring Boot + JHipster，dbt / Airflow 集成，Sprint Worklog 文档

---

### Task 1: 建立 Sprint-24/F7 跟踪结构

**Files:**
- Modify: `worklog/v2.2.2/sprint-24-202603/README.md`
- Create: `worklog/v2.2.2/sprint-24-202603/features/F7-工具栏动作与产出表回退补完/README.md`
- Create: `worklog/v2.2.2/sprint-24-202603/features/F7-工具栏动作与产出表回退补完/T01-收口工具栏动作能力定义与验收语义.md`
- Create: `worklog/v2.2.2/sprint-24-202603/features/F7-工具栏动作与产出表回退补完/T02-补齐文档动作闭环与反馈语义.md`
- Create: `worklog/v2.2.2/sprint-24-202603/features/F7-工具栏动作与产出表回退补完/T03-修复产出表检查链路与连接失败语义.md`
- Create: `worklog/v2.2.2/sprint-24-202603/features/F7-工具栏动作与产出表回退补完/T04-补自动化验证与人工验收清单.md`
- Modify: `worklog/v2.2.2/sprint-24-202603/it/README.md`
- Modify: `worklog/v2.2.2/sprint-queue.md`

**Step 1: 更新 Sprint README**

- 在 `Sprint-24` 增加 `F7-工具栏动作与产出表回退补完`
- 保持 Sprint 状态为 `IN_PROGRESS`

**Step 2: 新建 Feature README 和 4 个 Task 文档**

- 让 `F7` 只覆盖这轮问题，不和 `F3/F6` 混写

**Step 3: 更新 IT README**

- 添加工具栏动作和回退链路验收项

**Step 4: 更新 sprint queue**

- 把 `Sprint-24` 的 feature 统计同步更新

### Task 2: 收口提交变更 / 同步模型 / 文档的页面与接口语义

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/modelingToolbar.helpers.ts`
- Modify: `source/dts-platform-webapp/src/pages/modeling/sqlModelBuild.helpers.ts`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/DbtGitResource.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`

**Step 1: 明确 `提交变更` UI 文案**

- 确认其文案表达为 Git commit 语义，不误导成审批/提交流程

**Step 2: 明确 `同步模型` 结果反馈**

- 保证成功/空结果/manifest 缺失都有稳定提示

**Step 3: 明确 `文档` 结果反馈**

- 若仅支持 `dbt docs generate`，在 UI 上说明是“生成文档产物”

**Step 4: 补接口层语义对齐**

- 确认 resource 返回 message 可被前端直接消费

### Task 3: 修复产出表检查链路

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtOutputRelationService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtTargetConnectionFactory.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- Modify: `source/dts-platform-webapp/src/pages/modeling/components/OutputRelationModal.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`

**Step 1: 先写失败场景测试**

- 覆盖“目标数仓连接失败时 analyze 返回稳定、可解释错误”

**Step 2: 改造连接失败语义**

- 将 `The connection attempt failed.` 包装成平台侧明确信息
- 至少带出“目标数仓连接失败”上下文，而不是裸 JDBC 报错

**Step 3: 校验前端错误展示**

- 确保 modal 打不开时，用户能看到明确错误，而不是静默关闭

**Step 4: 验证 truncate / rebuild 共用检查链路**

- 两条菜单共用同一检查逻辑，但错误提示保持可解释

### Task 4: 补测试与人工验收

**Files:**
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtOutputRelationServiceTest.java`
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/EtlResourceTest.java`
- Modify: `source/dts-platform-webapp/src/pages/modeling/*.test.ts*`
- Modify: `worklog/v2.2.2/sprint-24-202603/it/README.md`

**Step 1: 后端单测**

- `DbtOutputRelationServiceTest`
- `EtlResourceTest`

**Step 2: 前端最小回归**

- 工具栏动作 helper
- 输出表 modal 错误反馈或页面行为

**Step 3: 写人工验收项**

- `提交变更`
- `同步模型`
- `文档`
- `回退 -> 清空产出表`
- `回退 -> 重建产出表`

**Step 4: 记录实际验证命令与结果**

- 写回 `Sprint-24/it/README.md`
