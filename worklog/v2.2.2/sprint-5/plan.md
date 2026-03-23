# Platform Backend Refactor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 收口 `source/dts-platform` 当前 review 已确认的 3 个高优先级问题，并恢复最小后端回归测试面为绿色。

**Architecture:** 以 `AirflowClient -> EtlResource`、`DbtOutputRelationService -> EtlResource`、`RollbackCascadeService -> RollbackProxyResource` 三条链路为主线推进。先修错误传播和契约一致性，再补测试，最后整理对外文案和回归门禁。

**Tech Stack:** Java 21, Spring Boot, Maven Surefire, Mockito, JUnit 5, Airflow REST integration

---

### Task 1: 收口 Airflow DAG ready 错误传播

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/AirflowClient.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/EtlResourceTest.java`

**Step 1: Write the failing test**

- 覆盖以下场景：
  - `404` 时仍返回“未注册”
  - `401/403/500` 时不能伪装成“未注册”
  - `waitForDagRegistration` 不应把真实错误吞掉

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=EtlResourceTest test`
Expected: FAIL on current implementation

**Step 3: Write minimal implementation**

- 让 `AirflowClient.getDag()` 区分：
  - `404`
  - 其他 HTTP 错误
  - 网络错误
- 让 `EtlResource.waitForDagRegistration()` 只对真正的“not found”走等待逻辑

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=EtlResourceTest test`
Expected: PASS

### Task 2: 收口产出表重建契约与测试

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtOutputRelationService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtOutputRelationServiceTest.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/EtlResourceTest.java`

**Step 1: Write the failing test**

- 明确 `prepareRebuild()` 现在的真实语义：
  - 是否仍视为“executed”
  - `message` 是否表示已执行 DROP
  - `rebuildDbtOutputRelation` 是否必须带 `full_refresh`

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=DbtOutputRelationServiceTest,EtlResourceTest test`
Expected: FAIL on stale assertions

**Step 3: Write minimal implementation**

- 统一 service / controller / response payload 的语义
- 更新测试断言，避免继续按旧 DROP 流程验证

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=DbtOutputRelationServiceTest,EtlResourceTest test`
Expected: PASS

### Task 3: 收口回滚后 full-refresh 触发失败契约

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/RollbackCascadeService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/RollbackProxyResource.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/` 新增或扩展对应测试

**Step 1: Write the failing test**

- 回滚主流程成功但 dbt rebuild 触发失败时：
  - 接口应返回明确失败
  - 或返回部分成功状态，不能伪装成完全成功

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=*Rollback* test`
Expected: FAIL because current code swallows the error

**Step 3: Write minimal implementation**

- 选定一种明确契约：
  - 失败即失败
  - 或主流程成功、rebuild 失败时返回部分成功状态并携带 warning
- 去掉当前双层吞异常

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=*Rollback* test`
Expected: PASS

### Task 4: 恢复最小回归面并补文案整理

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/AirflowProperties.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtDagServiceTest.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtOutputRelationServiceTest.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/EtlResourceTest.java`

**Step 1: Write the failing test**

- 文案不再硬编码“通常需要 30 秒”
- 配置值变化时 message 与行为一致
- 最小回归组全部应绿

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=DbtDagServiceTest,DbtOutputRelationServiceTest,EtlResourceTest test`
Expected: FAIL until all behavior and assertions are aligned

**Step 3: Write minimal implementation**

- 文案改为配置驱动
- 清理误导性 message
- 让最小回归组重新成为可靠门禁

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=DbtDagServiceTest,DbtOutputRelationServiceTest,EtlResourceTest test`
Expected: PASS
