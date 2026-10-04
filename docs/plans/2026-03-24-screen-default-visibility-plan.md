# Screen Default Visibility Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让新建与历史已有的大屏默认对部门领导、部门数据管理员、研究所数据管理员、所级领导具备读取权限。

**Architecture:** 保持现有 `analytics_screen_acl` 模型不变，在 `ScreenAclService` 中增加默认角色级 `READ` ACL 的幂等补齐能力；创建接口显式补齐一次，列表接口在读取时为历史数据做惰性回填。

**Tech Stack:** Spring Boot, Spring Data JPA, MockMvc, Jackson

---

### Task 1: 先写 ACL 回填的失败测试

**Files:**
- Modify: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ScreenResourceIT.java`

**Step 1: Write the failing test**

- 增加一个用例，创建大屏后读取 `/api/screens/{id}/acl`，断言存在：
  - `ROLE_DEPT_LEADER -> READ`
  - `ROLE_DEPT_DATA_OWNER -> READ`
  - `ROLE_INST_DATA_OWNER -> READ`
  - `ROLE_INST_LEADER -> READ`

**Step 2: Run test to verify it fails**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ScreenResourceIT test`

**Step 3: Write minimal implementation**

- 仅新增常量和补齐方法签名，让测试能走到更深处。

**Step 4: Run test again**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ScreenResourceIT test`

### Task 2: 实现默认可读 ACL 补齐

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenAclService.java`
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java`

**Step 1: Add default role constants**

- 在 `ScreenAclService` 中定义默认可读角色集合。

**Step 2: Add idempotent backfill helper**

- 新增 `ensureDefaultReadRoles(screen, operatorId)` 之类的幂等方法。
- 若 `screenId + ROLE + READ` 已存在，则跳过。

**Step 3: Wire create path**

- 在 `ScreenResource.create()` 中保存 screen 后调用该 helper。

**Step 4: Wire historical backfill path**

- 在 `ScreenResource.list()` 中，对可访问 screen 做一次惰性补齐。

**Step 5: Run tests**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ScreenResourceIT test`

### Task 3: 补历史数据可见性的回归测试

**Files:**
- Modify: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ScreenResourceIT.java`

**Step 1: Add a second failing test**

- 构造一个仅有创建人 `MANAGE` 的历史 screen。
- 模拟带 `ROLE_DEPT_LEADER` 的请求访问 `/api/screens`。
- 断言能看到 screen，并且后续 ACL 中出现该角色 `READ`。

**Step 2: Run test to verify it fails**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ScreenResourceIT test`

**Step 3: Write minimal implementation**

- 让 `list()` 在判断权限前先做默认 ACL 补齐。

**Step 4: Run test to verify it passes**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ScreenResourceIT test`

### Task 4: 完整验证

**Files:**
- Modify: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ScreenResourceIT.java`

**Step 1: Run targeted backend tests**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ScreenResourceIT test`

**Step 2: Run broader analytics backend regression**

Run: `mvn -f source/dts-analytics/pom.xml -Dtest=ProjectCockpitResourceIT,ScreenResourceIT test`

**Step 3: Commit**

```bash
git add docs/plans/2026-03-24-screen-default-visibility-design.md \
        docs/plans/2026-03-24-screen-default-visibility-plan.md \
        source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenAclService.java \
        source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java \
        source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ScreenResourceIT.java
git commit -m "feat: add default screen read visibility for leadership roles"
```
