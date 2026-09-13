# Sprint-33 角色管理成员分配重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace role member one-by-one assignment with a paged, searchable user table that defaults existing members to selected and submits only approval deltas.

**Architecture:** Backend exposes a role-scoped assignment-user query returning `inRole` with paged user data. Frontend consumes that query in a dedicated assignment table and keeps cross-page `pendingAdds` / `pendingRemovals` as the only submission delta.

**Tech Stack:** Spring Boot 3, Spring Data JPA, React 18, TanStack Query, Ant Design Table, Vitest source-level contracts.

---

## File Structure

- Modify `source/dts-admin/src/main/java/com/yuzhi/dts/admin/web/rest/AdminApiResource.java`: add role assignment user endpoint or delegate endpoint handling.
- Modify `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/user/AdminUserService.java`: add paged query method that accepts username, fullName, department, role.
- Modify `source/dts-admin/src/main/java/com/yuzhi/dts/admin/repository/AdminKeycloakUserRepository.java`: add repository query supporting username/fullName filters.
- Modify `source/dts-admin/src/main/java/com/yuzhi/dts/admin/repository/AdminRoleMemberRepository.java`: add role member lookup by role and page usernames if needed.
- Modify `source/dts-admin-webapp/src/admin/types.ts`: add assignment-user and query types.
- Modify `source/dts-admin-webapp/src/admin/api/adminApi.ts`: add `getRoleAssignmentUsers`.
- Modify `source/dts-admin-webapp/src/admin/views/role-detail.tsx`: split render into basic info and member assignment table, remove all-user fetch from edit flow.
- Add `source/dts-admin-webapp/src/admin/views/role-detail.assignment-table.source-contract.test.ts`: source contract for API/table/delta behavior.

## Task 1: Backend Contract

- [x] Run GitNexus impact for `listUsers`, `roleMembers`, `listSnapshots`, and repository query symbols before editing.
- [x] Write failing backend test for username/fullName/department filtering and `inRole`.
- [x] Run focused Maven test and confirm failure is due to missing query contract.
- [x] Implement minimal repository/service/resource changes.
- [x] Re-run focused Maven test until green.

## Task 2: Frontend API Contract

- [x] Write failing source-level test asserting `getRoleAssignmentUsers` path and `RoleAssignmentUser.inRole`.
- [x] Run Vitest and confirm failure.
- [x] Add types and API method.
- [x] Re-run Vitest until green.

## Task 3: Role Edit Table

- [x] Extend source-level test to assert role edit no longer calls `getAllAdminUsers` for member assignment.
- [x] Add source-level assertions for Table `rowSelection`, department/name/username query inputs, and `pendingAdds` / `pendingRemovals`.
- [x] Run Vitest and confirm failure.
- [x] Refactor `role-detail.tsx` with the smallest viable component extraction.
- [x] Re-run Vitest until green.

## Task 4: Verification

- [x] Run backend focused test.
- [x] Run frontend source-level test.
- [x] Run `pnpm build` in `source/dts-admin-webapp`.
- [x] Write evidence files under `worklog/v2.2.3/sprint-33-202605/it/evidence/`.
- [x] Update task and sprint statuses based on actual command results.
