# Platform Admin Gateway Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a unified admin gateway inside `dts-platform`, migrate all current platform-to-admin integrations into it, and remove direct `dts-admin` calls from `dts-platform-webapp`.

**Architecture:** Keep platform APIs as the only frontend contract, move all upstream `dts-admin` communication behind typed gateway domains, and migrate existing callers domain by domain with tests. Use `worklog/v2.2.2/sprint-26-202603` as the tracking source of truth and keep the current external API surface stable where possible.

**Tech Stack:** Spring Boot, RestTemplate, JUnit, TypeScript, Vite, pnpm, worklog sprint artifacts

---

### Task 1: 建立 Sprint 与 Feature 跟踪骨架

**Files:**
- Create: `worklog/v2.2.2/sprint-26-202603/README.md`
- Create: `worklog/v2.2.2/sprint-26-202603/features/F1-Platform统一Admin Gateway/README.md`
- Create: `worklog/v2.2.2/sprint-26-202603/features/F1-Platform统一Admin Gateway/T01-Admin Gateway基础层.md`
- Create: `worklog/v2.2.2/sprint-26-202603/features/F1-Platform统一Admin Gateway/T02-目录域迁移.md`
- Create: `worklog/v2.2.2/sprint-26-202603/features/F1-Platform统一Admin Gateway/T03-认证与PKI迁移.md`
- Create: `worklog/v2.2.2/sprint-26-202603/features/F1-Platform统一Admin Gateway/T04-基础设施工作流菜单审计迁移.md`
- Create: `worklog/v2.2.2/sprint-26-202603/features/F1-Platform统一Admin Gateway/T05-前端直连清理与回归.md`
- Create: `worklog/v2.2.2/sprint-26-202603/it/README.md`
- Modify: `worklog/v2.2.2/sprint-queue.md`

**Step 1: Write the sprint artifacts**

记录 sprint 目标、feature、task、依赖和验收标准。

**Step 2: Verify the sprint structure**

Run: `find worklog/v2.2.2/sprint-26-202603 -maxdepth 3 -type f | sort`
Expected: 新 sprint、feature、task、it 文件全部存在

**Step 3: Commit**

```bash
git add worklog/v2.2.2/sprint-26-202603 worklog/v2.2.2/sprint-queue.md
git commit -m "docs(F1/T01): add sprint-26 admin gateway worklog"
```

### Task 2: 建立 Admin Gateway 公共基础层

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/support/AdminGatewayTransport.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/support/AdminGatewayHeaders.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/support/AdminGatewayException.java`
- Create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/admin/gateway/support/AdminGatewayTransportTest.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/DtsAdminProperties.java`

**Step 1: Write the failing test**

覆盖：
- 自动拼接 baseUrl + apiPath/adminApiPath
- bearer token 与 `X-DTS-Service`
- forwarded headers 透传
- envelope success / error 解析

**Step 2: Run test to verify it fails**

Run: `cd source/dts-platform && ./mvnw -q -Dtest=AdminGatewayTransportTest test`
Expected: FAIL because transport classes do not exist

**Step 3: Write minimal implementation**

实现统一 transport 和基础异常类型，不先迁业务。

**Step 4: Run test to verify it passes**

Run: `cd source/dts-platform && ./mvnw -q -Dtest=AdminGatewayTransportTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/DtsAdminProperties.java \
  source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/support \
  source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/admin/gateway/support/AdminGatewayTransportTest.java
git commit -m "feat(F1/T01): add admin gateway transport layer"
```

### Task 3: 迁移目录域到 Gateway

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/directory/AdminDirectoryGateway.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/DirectoryResource.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/OrganizationVisibilityService.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/GovernanceIndicatorDependencyResource.java`
- Deprecate or delete: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/directory/AdminDirectoryClient.java`
- Deprecate or delete: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/directory/AdminUserDirectoryClient.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/DirectoryResourceTest.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/admin/gateway/directory/AdminDirectoryGatewayTest.java`

**Step 1: Write the failing tests**

覆盖组织树、用户目录、角色目录三条路径。

**Step 2: Run tests to verify they fail**

Run: `cd source/dts-platform && ./mvnw -q -Dtest=DirectoryResourceTest,AdminDirectoryGatewayTest test`
Expected: FAIL because resource still depends on legacy clients

**Step 3: Write minimal implementation**

让 `DirectoryResource` 和目录相关业务依赖新 gateway。

**Step 4: Run tests to verify they pass**

Run: `cd source/dts-platform && ./mvnw -q -Dtest=DirectoryResourceTest,AdminDirectoryGatewayTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/directory \
  source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/DirectoryResource.java \
  source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/OrganizationVisibilityService.java \
  source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/GovernanceIndicatorDependencyResource.java \
  source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/DirectoryResourceTest.java \
  source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/admin/gateway/directory/AdminDirectoryGatewayTest.java
git commit -m "refactor(F1/T02): migrate directory calls to admin gateway"
```

### Task 4: 迁移认证与 PKI 域

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/auth/AdminAuthGateway.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/KeycloakAuthResource.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/AdminAuthClient.java`
- Add or modify platform-facing PKI resource if needed under `web/rest`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/KeycloakAuthResourceTest.java`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/admin/gateway/auth/AdminAuthGatewayTest.java`

**Step 1: Write the failing tests**

覆盖登录、刷新、登出、PKI challenge、PKI login。

**Step 2: Run tests to verify they fail**

Run: `cd source/dts-platform && ./mvnw -q -Dtest=KeycloakAuthResourceTest,AdminAuthGatewayTest test`
Expected: FAIL until auth gateway and PKI proxies exist

**Step 3: Write minimal implementation**

把认证/PKI 上游访问统一迁入 auth gateway，并补齐平台代理接口。

**Step 4: Run tests to verify they pass**

Run: `cd source/dts-platform && ./mvnw -q -Dtest=KeycloakAuthResourceTest,AdminAuthGatewayTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/auth \
  source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest \
  source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/KeycloakAuthResourceTest.java \
  source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/admin/gateway/auth/AdminAuthGatewayTest.java
git commit -m "refactor(F1/T03): migrate auth and pki admin calls to gateway"
```

### Task 5: 迁移基础设施、工作流、菜单、审计域

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/infra/AdminInfraGateway.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/workflow/AdminWorkflowGateway.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/menu/AdminMenuGateway.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/audit/AdminAuditGateway.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/AdminInfraClient.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/workflow/AdminWorkflowConfigClient.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/menu/PortalMenuClient.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/SecurityAuditLogProxyResource.java`
- Test: corresponding resource/service tests under `src/test/java`

**Step 1: Write the failing tests**

按域补 transport 与资源层回归。

**Step 2: Run tests to verify they fail**

Run: `cd source/dts-platform && ./mvnw -q -Dtest=SecurityAuditLogProxyResourceTest,PortalMenuClientTest,AdminWorkflowGatewayTest,AdminInfraGatewayTest test`
Expected: FAIL until the new gateways are wired

**Step 3: Write minimal implementation**

把剩余业务域全部切到统一 gateway。

**Step 4: Run tests to verify they pass**

Run: `cd source/dts-platform && ./mvnw -q -Dtest=SecurityAuditLogProxyResourceTest,PortalMenuClientTest,AdminWorkflowGatewayTest,AdminInfraGatewayTest test`
Expected: PASS

**Step 5: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway \
  source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/AdminInfraClient.java \
  source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/workflow/AdminWorkflowConfigClient.java \
  source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/menu/PortalMenuClient.java \
  source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/SecurityAuditLogProxyResource.java \
  source/dts-platform/src/test/java/com/yuzhi/dts/platform
git commit -m "refactor(F1/T04): migrate remaining admin domains to gateway"
```

### Task 6: 清理前端直连 admin 调用

**Files:**
- Modify: `source/dts-platform-webapp/src/api/services/deptService.ts`
- Modify: `source/dts-platform-webapp/src/api/services/adminService.ts`
- Modify: `source/dts-platform-webapp/src/api/services/roleService.ts`
- Modify: `source/dts-platform-webapp/src/api/services/pkiService.ts`
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`
- Test: `source/dts-platform-webapp/src/api/**/*.test.ts`

**Step 1: Write the failing tests**

覆盖：
- service 不再引用 `adminApiBaseUrl`
- “归属部门”只走平台目录接口
- whoAmI / roles / PKI 都从平台侧获取

**Step 2: Run tests to verify they fail**

Run: `cd source/dts-platform-webapp && node --import ./node_modules/.pnpm/tsx@4.19.4/node_modules/tsx/dist/loader.mjs --test src/api/**/*.test.ts`
Expected: FAIL until services are rewired

**Step 3: Write minimal implementation**

让前端所有 admin 相关 service 只调用平台 API。

**Step 4: Run tests to verify they pass**

Run: `cd source/dts-platform-webapp && node --import ./node_modules/.pnpm/tsx@4.19.4/node_modules/tsx/dist/loader.mjs --test src/api/**/*.test.ts`
Expected: PASS

**Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/api/services \
  source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx \
  source/dts-platform-webapp/src/api
git commit -m "refactor(F1/T05): remove direct admin calls from platform webapp"
```

### Task 7: 全量回归与 Sprint 收口

**Files:**
- Modify: `worklog/v2.2.2/sprint-26-202603/README.md`
- Modify: `worklog/v2.2.2/sprint-26-202603/features/F1-Platform统一Admin Gateway/README.md`
- Modify: `worklog/v2.2.2/sprint-26-202603/it/README.md`
- Modify: `worklog/v2.2.2/sprint-queue.md`

**Step 1: Run backend verification**

Run: `cd source/dts-platform && ./mvnw -q -DskipTests compile`
Expected: PASS

**Step 2: Run focused backend tests**

Run: `cd source/dts-platform && ./mvnw -q -Dtest=DirectoryResourceTest,KeycloakAuthResourceTest,SecurityAuditLogProxyResourceTest test`
Expected: PASS

**Step 3: Run frontend verification**

Run: `cd source/dts-platform-webapp && pnpm build`
Expected: PASS or only existing known warnings

**Step 4: Update sprint statuses and IT evidence**

把执行结果、验证命令、残余风险写回 sprint 与 `it/README.md`。

**Step 5: Commit**

```bash
git add worklog/v2.2.2/sprint-26-202603 worklog/v2.2.2/sprint-queue.md
git commit -m "docs(F1/T05): close sprint-26 admin gateway verification"
```
