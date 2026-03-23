# dbt Package P0 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复 dbt 离线交付相关的三个 P0 问题：逻辑建模页面隐式工作区同步、项目空间同名创建、dbt source/发布状态不稳定。

**Architecture:** 先关掉错误的隐式路径，再补最小的显式保护。后端优先：阻断页面加载即写元数据、拦截同名 plan、收敛 source 与提前发布状态；前端只做必要的接口适配或提示，不先做大改版。

**Tech Stack:** Spring Boot, JPA, Liquibase, React, TypeScript, dbt workspace files

---

### Task 1: 固化 spec 文档

**Files:**
- Create: `worklog/v2.2.2/spec/dbt-package/README.md`
- Create: `worklog/v2.2.2/spec/dbt-package/01-design.md`
- Create: `worklog/v2.2.2/spec/dbt-package/02-cli.md`
- Create: `worklog/v2.2.2/spec/dbt-package/03-sop.md`
- Create: `worklog/v2.2.2/spec/dbt-package/04-gaps.md`

**Step 1: 写入固定文档**

将 dbt package 的设计、CLI、SOP 和平台欠账固化到 `worklog/v2.2.2/spec/dbt-package/`。

**Step 2: 自检**

确认文档与当前代码现状一致，不描述未实现的接口为“已存在”。

### Task 2: 移除逻辑建模列表接口的隐式工作区同步

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelServiceTest.java`

**Step 1: 写失败测试**

增加测试，验证 `list()` 不再因为调用而隐式写入工作区发现模型。

**Step 2: 跑测试确认失败**

Run: `mvn -f source/dts-platform/pom.xml -Dtest=ModelingSqlModelServiceTest test`

**Step 3: 实现最小修复**

- 从 `list()` 移除 `syncWorkspaceModels(activeDeptHeader)`
- 保留现有工作区扫描实现，后续再决定是否暴露显式入口

**Step 4: 跑测试确认通过**

Run: `mvn -f source/dts-platform/pom.xml -Dtest=ModelingSqlModelServiceTest test`

### Task 3: 增加项目空间同名保护

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/modeling/ModelingPlanRepository.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelingAuxResource.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/ModelingAuxResourceIT.java`

**Step 1: 写失败测试**

增加集成测试，验证创建或更新为重复名称时返回 `400`。

**Step 2: 跑测试确认失败**

Run: `mvn -f source/dts-platform/pom.xml -Dtest=ModelingAuxResourceIT test`

**Step 3: 实现最小修复**

- repository 增加按名称查询能力
- create/update 前做大小写不敏感的重名校验

**Step 4: 跑测试确认通过**

Run: `mvn -f source/dts-platform/pom.xml -Dtest=ModelingAuxResourceIT test`

### Task 4: 收敛 source 权威并去掉提前发布状态

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtSourceService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- Modify: `services/dts-dbt/models/dwd/prj1/biz_dwd_project_node.sql`
- Modify: `services/dts-dbt/models/dwd/prj1/pm_dim_major_project.sql`
- Modify: `services/dts-dbt/models/dwd/prj1/pm_dim_subproject.sql`
- Modify: `services/dts-dbt/models/dwd/prj1/pm_map_node_subject.sql`
- Delete: `services/dts-dbt/models/pm_ods_sources.yml`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtSourceServiceTest.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/EtlResourceTest.java`

**Step 1: 写失败测试**

- `DbtSourceServiceTest` 覆盖同一 `(schema, table)` 的重复映射只生成一次
- `EtlResourceTest` 覆盖 trigger 后不再直接把模型状态改成 `PUBLISHED/TESTED`

**Step 2: 跑测试确认失败**

Run: `mvn -f source/dts-platform/pom.xml -Dtest=DbtSourceServiceTest,EtlResourceTest test`

**Step 3: 实现最小修复**

- `ods_sources.yml` 生成按 `(schema, table)` 去重
- 移除 `trigger` 后的乐观状态升级
- 项目管理模型统一引用单一 source 权威
- 删除遗留 `pm_ods_sources.yml`

**Step 4: 跑测试确认通过**

Run: `mvn -f source/dts-platform/pom.xml -Dtest=DbtSourceServiceTest,EtlResourceTest test`

### Task 5: 前端最小适配与回归

**Files:**
- Modify: `source/dts-platform-webapp/src/api/platformApi.ts`（仅在需要新增显式接口时）
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`（仅在需要前端提示或入口调整时）

**Step 1: 评估是否必须前端改动**

如果后端改动不破坏现有页面，则前端不做额外修改。

**Step 2: 如需改动，先补最小测试或手工验证点**

- 页面加载不再隐式发现工作区模型
- “同步模型”现有行为不受影响

**Step 3: 跑前端验证**

Run: `pnpm -C source/dts-platform-webapp build`

### Task 6: 总体验证

**Step 1: 跑后端定向测试**

Run: `mvn -f source/dts-platform/pom.xml -Dtest=ModelingSqlModelServiceTest,ModelingAuxResourceIT,DbtSourceServiceTest,EtlResourceTest test`

**Step 2: 跑前端构建**

Run: `pnpm -C source/dts-platform-webapp build`

**Step 3: 手工检查**

- `services/dts-dbt/models/pm_ods_sources.yml` 不再存在
- 项目管理模型只引用统一 source
- 打开逻辑建模页不再自动写入工作区模型
- 重复项目空间名称被拒绝

**Step 4: 总结结果**

记录通过项、未做项、遗留风险，并回填到 `worklog/v2.2.2/spec/dbt-package/04-gaps.md` 对应状态。
