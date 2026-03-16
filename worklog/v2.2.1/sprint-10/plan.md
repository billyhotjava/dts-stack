# dbt 工作区自举与产出表管理修复 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让空目录 `services/dts-dbt` 自动变成最小可运行 dbt 项目，并修复逻辑建模中的批量导入与产出表维护。

**Architecture:** 后端新增统一的 dbt workspace bootstrap 与模型产出 relation 管理能力；批量导入改成“预检 + 真实落盘一致性”；前端替换当前 rollback 交互，改为 dbt 原生产出表维护弹窗和更明确的导入/初始化反馈。

**Tech Stack:** Spring Boot 3 / Java 21, React / TypeScript / antd, dbt, Airflow, PostgreSQL

---

### Task 1: 统一 dbt workspace bootstrap

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtConfigService.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtWorkspaceBootstrapTest.java`

**Step 1: Write failing tests**

- 空目录加载配置后应自动生成最小工作区骨架
- 已有 `dbt_project.yml` 不应被覆盖
- 缺失目录再次加载应自动补齐

**Step 2: Run tests to verify failure**

Run: `cd source/dts-platform && mvn -Dtest=DbtWorkspaceBootstrapTest test`

**Step 3: Implement bootstrap**

- 在 `DbtConfigService` 中新增最小骨架初始化
- 补齐 `dbt_project.yml`、`models/*`、`macros/`、`target/`、`logs/` 等
- 保持幂等，不覆盖非空用户文件

**Step 4: Run tests to verify pass**

Run: `cd source/dts-platform && mvn -Dtest=DbtWorkspaceBootstrapTest test`

**Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtConfigService.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtWorkspaceBootstrapTest.java
git commit -m "feat(platform): bootstrap minimal dbt workspace"
```

---

### Task 2: 在建模与执行入口接入 bootstrap

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelServiceTest.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/EtlResourceTest.java`

**Step 1: Write failing/adjusted tests**

- 空工作区下 `create/import/batch-import/build/docs` 不应因缺骨架失败

**Step 2: Run targeted tests**

Run: `cd source/dts-platform && mvn -Dtest=ModelingSqlModelServiceTest,EtlResourceTest test`

**Step 3: Implement**

- 在 `ensureWorkspaceWritable()` 之前或其中补 bootstrap
- 在 `compile/test/build/docs` 触发前确保工作区已初始化

**Step 4: Re-run tests**

Run: `cd source/dts-platform && mvn -Dtest=ModelingSqlModelServiceTest,EtlResourceTest test`

**Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelServiceTest.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/EtlResourceTest.java
git commit -m "fix(platform): self-heal dbt workspace before modeling operations"
```

---

### Task 3: 批量导入预检与失败类型细分

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelServiceTest.java`

**Step 1: Write failing tests**

- ZIP 缺 `models.tsv` 时返回校验失败
- 缺 SQL 文件时返回 `validation_failed`
- 非法 `layer/materialized` 时返回 `validation_failed`

**Step 2: Run tests**

Run: `cd source/dts-platform && mvn -Dtest=ModelingSqlModelServiceTest test`

**Step 3: Implement**

- 为 `batchImportFromArchive()` 增加 manifest 预检
- 细化 `BatchImportDetail.status`
- 保留逐条导入，但把预检错误与导入错误区分开

**Step 4: Re-run tests**

Run: `cd source/dts-platform && mvn -Dtest=ModelingSqlModelServiceTest test`

**Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelServiceTest.java
git commit -m "fix(platform): validate batch-import archives before model creation"
```

---

### Task 4: 模型落盘失败升级为真实失败

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelServiceTest.java`

**Step 1: Write failing tests**

- `writeModelFile()` 写失败时导入必须失败
- `writeCsvSidecar()` 写失败时批量导入返回 `write_failed`

**Step 2: Run tests**

Run: `cd source/dts-platform && mvn -Dtest=ModelingSqlModelServiceTest test`

**Step 3: Implement**

- 调整写文件逻辑，不再吞异常
- 让 `importFromFiles()` 与 `batchImportFromArchive()` 正确感知落盘失败

**Step 4: Re-run tests**

Run: `cd source/dts-platform && mvn -Dtest=ModelingSqlModelServiceTest test`

**Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelServiceTest.java
git commit -m "fix(platform): fail imports when dbt files cannot be written"
```

---

### Task 5: 新增模型产出 relation 管理后端

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/SqlModelOutputService.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/SqlModelOutputResource.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/SqlModelOutputServiceTest.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/SqlModelOutputResourceIT.java`

**Step 1: Write failing tests**

- table relation 可 truncate
- view relation 拒绝 truncate
- relation 不存在时返回可理解结果

**Step 2: Run tests**

Run: `cd source/dts-platform && mvn -Dtest=SqlModelOutputServiceTest,SqlModelOutputResourceIT test`

**Step 3: Implement**

- 统一解析模型 relation
- 提供 `analyze/truncate/rebuild` API
- 与当前 target 数据源 JDBC 连接配合执行

**Step 4: Re-run tests**

Run: `cd source/dts-platform && mvn -Dtest=SqlModelOutputServiceTest,SqlModelOutputResourceIT test`

**Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/SqlModelOutputService.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/SqlModelOutputResource.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/SqlModelOutputServiceTest.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/SqlModelOutputResourceIT.java
git commit -m "feat(platform): add dbt model output relation management APIs"
```

---

### Task 6: 重建产出表改为 drop relation + dbt build

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/SqlModelOutputService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/SqlModelOutputServiceTest.java`

**Step 1: Write failing tests**

- rebuild 先 drop，再触发 `dbt build`
- rebuild 后会同步 manifest/run_results

**Step 2: Run tests**

Run: `cd source/dts-platform && mvn -Dtest=SqlModelOutputServiceTest,EtlResourceTest test`

**Step 3: Implement**

- 复用现有 build 触发逻辑
- 统一 selector 解析
- 重建后同步 dbt 产物状态

**Step 4: Re-run tests**

Run: `cd source/dts-platform && mvn -Dtest=SqlModelOutputServiceTest,EtlResourceTest test`

**Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/SqlModelOutputService.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/SqlModelOutputServiceTest.java
git commit -m "fix(platform): rebuild dbt model outputs via dbt build"
```

---

### Task 7: 前端替换 rollback 交互

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- Create: `source/dts-platform-webapp/src/pages/modeling/sqlModelOutput.helpers.ts`
- Test: `source/dts-platform-webapp/src/pages/modeling/sqlModelOutput.helpers.test.ts`

**Step 1: Write failing tests**

- 产出表动作不再调用 rollback API
- 根据 relation 类型渲染正确提示

**Step 2: Run tests**

Run: `cd source/dts-platform-webapp && pnpm exec tsx --test src/pages/modeling/sqlModelOutput.helpers.test.ts`

**Step 3: Implement**

- 移除逻辑建模页对 rollback modal 的依赖
- 接入新的 output APIs
- 优化成功/失败提示与工作区初始化状态

**Step 4: Re-run tests**

Run: `cd source/dts-platform-webapp && pnpm exec tsx --test src/pages/modeling/sqlModelOutput.helpers.test.ts && pnpm build`

**Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx \
        source/dts-platform-webapp/src/pages/modeling/sqlModelOutput.helpers.ts \
        source/dts-platform-webapp/src/pages/modeling/sqlModelOutput.helpers.test.ts
git commit -m "fix(platform-webapp): manage dbt outputs without rollback proxy"
```

---

### Task 8: 收口验证与文档更新

**Files:**
- Modify: `worklog/v2.2.1/sprint-10/README.md`
- Modify: `worklog/v2.2.1/sprint-10/it/README.md`

**Step 1: Run full verification**

```bash
cd source/dts-platform && mvn -Dtest=DbtWorkspaceBootstrapTest,ModelingSqlModelServiceTest,SqlModelOutputServiceTest,EtlResourceTest test
cd source/dts-platform-webapp && pnpm build
```

**Step 2: Manual checks**

- 空目录初始化
- ZIP 批量导入
- truncate output
- rebuild output

**Step 3: Record results**

- 更新 sprint README 状态
- 在 `it/README.md` 记录验证命令和预期结果

**Step 4: Commit**

```bash
git add worklog/v2.2.1/sprint-10/README.md worklog/v2.2.1/sprint-10/it/README.md
git commit -m "docs(worklog): record dbt workspace and output-management verification"
```
