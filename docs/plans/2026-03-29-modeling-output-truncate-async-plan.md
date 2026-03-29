# 逻辑建模清空产出表异步执行 Implementation Plan

**Goal:** 把逻辑建模页 `清空产出表` 从 platform 直连 JDBC 的同步执行模式，重构为统一的 dbt / Airflow 后台任务模式。

**Architecture:** 前端仅提交任务并展示执行状态；后端生成 Airflow conf；dbt 容器通过 `run-operation truncate_relation` 宏执行真实 relation 检查和 truncate。

**Tech Stack:** React + TypeScript, Spring Boot, dbt macros, Airflow DAG BashOperator

---

### Task 1: 扩展 Sprint-24/F7 跟踪

**Files:**
- Modify: `worklog/v2.2.2/sprint-24-202603/README.md`
- Modify: `worklog/v2.2.2/sprint-24-202603/features/F7-工具栏动作与产出表回退补完/README.md`
- Create: `worklog/v2.2.2/sprint-24-202603/features/F7-工具栏动作与产出表回退补完/T05-清空产出表异步执行重构.md`
- Modify: `worklog/v2.2.2/sprint-queue.md`

### Task 2: 先写后端红灯测试

**Files:**
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtOutputRelationServiceTest.java`
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtDagServiceTest.java`
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtWorkspaceBootstrapTest.java`
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/EtlResourceTest.java`

**Step 1:** 为 `prepareTruncate()` 写“无 JDBC 依赖”的失败测试  
**Step 2:** 为 DAG 生成脚本写 `run-operation` 分支测试  
**Step 3:** 为 workspace bootstrap 写 `truncate_relation.sql` 存在性测试  
**Step 4:** 为 truncate 接口写 Airflow conf 提交测试

### Task 3: 实现后端与 dbt 宏

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtOutputRelationService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtDagService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtConfigService.java`
- Add: `services/dts-dbt/macros/truncate_relation.sql`

### Task 4: 先写前端红灯测试并切换页面行为

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/sqlModelOutputAction.helpers.ts`
- Modify: `source/dts-platform-webapp/src/pages/modeling/sqlModelOutputAction.helpers.test.ts`
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/components/OutputRelationModal.tsx`
- Modify: `source/dts-platform-webapp/src/api/platformApi.ts`

**Step 1:** 先写“truncate 预览不走 JDBC 检查”的 helper 测试  
**Step 2:** 前端确认按钮改成异步任务提交  
**Step 3:** 页面切换到执行日志视图，不再同步等待 truncate 完成

### Task 5: 验证与文档回写

**Files:**
- Modify: `worklog/v2.2.2/sprint-24-202603/it/README.md`
- Modify: `worklog/v2.2.2/sprint-24-202603/features/F7-工具栏动作与产出表回退补完/*.md`

**Verification:**
- `cd source/dts-platform && ./mvnw -q -Dtest=DbtOutputRelationServiceTest,DbtDagServiceTest,DbtWorkspaceBootstrapTest,EtlResourceTest test`
- `cd source/dts-platform && ./mvnw -q -DskipTests compile`
- `cd source/dts-platform-webapp && node --import ./node_modules/.pnpm/tsx@4.19.4/node_modules/tsx/dist/loader.mjs --test src/pages/modeling/modelingToolbar.helpers.test.ts src/pages/modeling/sqlModelBuild.helpers.test.ts src/pages/modeling/sqlModelOutputAction.helpers.test.ts`
