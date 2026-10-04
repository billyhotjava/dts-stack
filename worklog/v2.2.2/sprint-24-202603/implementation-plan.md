# Sprint-24: 逻辑建模主工作流补完 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐逻辑建模主工作流，完成模型唯一性、文件浏览器、导入治理、归档删除、ODS 模板生成以及编译测试上线闭环。

**Architecture:** 后端以 `ModelingSqlModelService / ModelGenerationService / ModelingSqlModelResource` 为主链，前端以 `SqlModelingPage` 为入口收口文件浏览器、批量选择和生命周期操作。旧的“从 ODS 一键生成”直接下线，替换为新的模板生成链路。

**Tech Stack:** Spring Boot, JPA, React, TypeScript, Ant Design

---

## 执行顺序

- [ ] Task 1: 收口模型唯一性
- [ ] Task 2: 收口批量选择语义
- [ ] Task 3: 强化生命周期操作
- [ ] Task 4: 补完导入与批量导入
- [ ] Task 5: 用新模板生成链替换 ODS 一键生成
- [ ] Task 6: 编译、测试、上线与验收收口

## Task 1: 收口模型唯一性

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelingSqlModelRepository.java`
- Test: `source/dts-platform/src/test/java/...`

- [ ] 写唯一性失败测试，覆盖同一 `planId + name` 的 create/update/import 冲突
- [ ] 跑测试确认先红
- [ ] 实现统一唯一性校验方法
- [ ] 移除 `list()` 中的静默去重
- [ ] 跑测试确认转绿

## Task 2: 收口批量选择语义

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/sqlModelBulkSelection.helpers.ts`
- Modify: `source/dts-platform-webapp/src/pages/modeling/components/GovernanceModal.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/components/ModelFileBrowser.tsx`

- [ ] 写前端选择语义失败测试
- [ ] 跑测试确认先红
- [ ] 调整 `tree / governance / list` source bucket 行为
- [ ] 去掉入口打开时的无条件全清空
- [ ] 跑测试确认转绿

## Task 3: 强化生命周期操作

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/components/BatchDeleteResultModal.tsx`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`

- [ ] 写删除/批删/治理/归档结果回执测试
- [ ] 跑测试确认先红
- [ ] 收口结果反馈与编辑态回收
- [ ] 跑测试确认转绿

## Task 4: 补完导入与批量导入

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelGenerationService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelFileService.java`
- Modify: `source/dts-platform-webapp/src/pages/modeling/BatchImportModal.tsx`

- [ ] 写导入冲突和批量导入明细测试
- [ ] 跑测试确认先红
- [ ] 实现唯一性、写文件边界、结果反馈
- [ ] 跑测试确认转绿

## Task 5: 用新模板生成链替换 ODS 一键生成

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelGenerationService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelingSqlModelResource.java`
- Modify: `source/dts-platform-webapp/src/pages/modeling/components/OdsGenerateModal.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`

- [ ] 写“旧入口下线 + 新模板生成可用”失败测试
- [ ] 跑测试确认先红
- [ ] 删除旧语义并实现模板生成链路
- [ ] 跑测试确认转绿

## Task 6: 编译、测试、上线与验收收口

**Files:**
- Modify: `worklog/v2.2.2/sprint-24-202603/it/README.md`
- Modify: `worklog/v2.2.2/sprint-24-202603/README.md`

- [ ] 收口后端编译与单测命令
- [ ] 收口前端构建与关键页面验证命令
- [ ] 梳理上线步骤与回滚点
- [ ] 更新 IT 清单和人工验收记录
