# Sprint-33 大屏权限本地化重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将大屏权限系统从平台 API 查询路径完全迁移到本地 `analytics_screen_access` 表，消除 granteeId 类型不一致 bug，实现零默认权限、零硬编码角色的权限控制。

**Architecture:** 新建 `analytics_screen_access` 表存储所有大屏授权记录，`granteeId` 统一使用 analytics 数字 ID（USER 类型）或角色名字符串（ROLE 类型）。`ScreenPermissionService` 和 `ScreenOwnershipService` 完全脱离 `PlatformPermissionClient` 和 `RestTemplate`，直接查询本地表。superuser bypass 通过 `analytics_user.superuser` 字段实现。

**Tech Stack:** Spring Boot 3.4.5, JPA/Hibernate, PostgreSQL 17.6, Liquibase, JUnit 5, AssertJ, Mockito

---

## 文件结构

### 新建文件
- `source/dts-analytics/src/main/resources/config/liquibase/changelog/0043_screen_access.xml`
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/domain/AnalyticsScreenAccess.java`
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/repository/AnalyticsScreenAccessRepository.java`
- `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/service/ScreenPermissionServiceTest.java`
- `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/service/ScreenOwnershipServiceLocalTest.java`

### 修改文件
- `source/dts-analytics/src/main/resources/config/liquibase/master.xml` — 追加 include
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenPermissionService.java` — 完全重写
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenOwnershipService.java` — 完全重写
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java` — 多处修改

### 删除（或清空引用）
- `ScreenPermissionService` 中对 `PlatformPermissionClient` 的所有依赖
- `ScreenOwnershipService` 中的 `RestTemplate`、`platformBaseUrl`、`registerOwnership`、`transferOwnership`
- `ScreenResource` 中的 `resolveGranteeUsername`、`PlatformContext` 权限相关用法
- `PlatformPermissionClient.java` — 确认无其他引用后删除整个文件

---

## 权限模型速查

| permission | canRead | canEdit | isOwner (管理授权) | 谁可拥有 |
|---|---|---|---|---|
| OWNER | ✓ | ✓ | ✓ | 创建者（自动） |
| MANAGER | ✓ | ✗ | ✓ | 被授权 MANAGER 的用户/角色 |
| VIEWER | ✓ | ✗ | ✗ | 被授权 VIEWER 的用户/角色 |

- superuser=true → 跳过表查询，返回全量访问（哨兵 `List.of(-1L)`）
- 除 superuser 字段外，**零硬编码角色，零默认权限**

---

## Task 1: Liquibase 迁移 — 建表

**Files:**
- Create: `source/dts-analytics/src/main/resources/config/liquibase/changelog/0043_screen_access.xml`
- Modify: `source/dts-analytics/src/main/resources/config/liquibase/master.xml`

- [ ] **Step 1: 新建 changelog 文件**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="0043-screen-access" author="dts">
        <preConditions onFail="MARK_RAN">
            <not>
                <tableExists tableName="analytics_screen_access"/>
            </not>
        </preConditions>
        <createTable tableName="analytics_screen_access">
            <column name="id" type="bigint" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="screen_id" type="bigint">
                <constraints nullable="false"/>
            </column>
            <column name="grantee_type" type="varchar(10)">
                <constraints nullable="false"/>
            </column>
            <column name="grantee_id" type="varchar(200)">
                <constraints nullable="false"/>
            </column>
            <column name="permission" type="varchar(10)">
                <constraints nullable="false"/>
            </column>
            <column name="granted_by" type="bigint"/>
            <column name="granted_at" type="timestamptz" defaultValueComputed="now()">
                <constraints nullable="false"/>
            </column>
        </createTable>
        <addUniqueConstraint
            tableName="analytics_screen_access"
            columnNames="screen_id, grantee_type, grantee_id"
            constraintName="uq_screen_access"/>
        <createIndex tableName="analytics_screen_access" indexName="idx_screen_access_screen_id">
            <column name="screen_id"/>
        </createIndex>
        <createIndex tableName="analytics_screen_access" indexName="idx_screen_access_grantee">
            <column name="grantee_type"/>
            <column name="grantee_id"/>
        </createIndex>
        <rollback>
            <dropTable tableName="analytics_screen_access"/>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: 在 master.xml 追加 include**

在 `master.xml` 最后一个 `<include>` 行（即 `0042_user_platform_username.xml` 所在行）之后追加：

```xml
    <include file="config/liquibase/changelog/0043_screen_access.xml" relativeToChangelogFile="false"/>
```

- [ ] **Step 3: 验证 XML 格式正确**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics
mvn liquibase:validate -q 2>&1 | tail -5
```

期望无错误输出（或 BUILD SUCCESS）。

- [ ] **Step 4: Commit**

```bash
git add source/dts-analytics/src/main/resources/config/liquibase/changelog/0043_screen_access.xml
git add source/dts-analytics/src/main/resources/config/liquibase/master.xml
git commit -m "feat(sprint-33): add analytics_screen_access table migration"
```

---

## Task 2: Entity + Repository

**Files:**
- Create: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/domain/AnalyticsScreenAccess.java`
- Create: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/repository/AnalyticsScreenAccessRepository.java`

- [ ] **Step 1: 创建 Entity**

```java
package com.yuzhi.dts.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "analytics_screen_access")
public class AnalyticsScreenAccess implements Serializable {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "screen_id", nullable = false)
    private Long screenId;

    /** "USER" or "ROLE" */
    @Column(name = "grantee_type", nullable = false, length = 10)
    private String granteeType;

    /** For USER: String.valueOf(analyticsUser.id). For ROLE: role name string. */
    @Column(name = "grantee_id", nullable = false, length = 200)
    private String granteeId;

    /** "OWNER", "MANAGER", or "VIEWER" */
    @Column(name = "permission", nullable = false, length = 10)
    private String permission;

    @Column(name = "granted_by")
    private Long grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getScreenId() { return screenId; }
    public void setScreenId(Long screenId) { this.screenId = screenId; }

    public String getGranteeType() { return granteeType; }
    public void setGranteeType(String granteeType) { this.granteeType = granteeType; }

    public String getGranteeId() { return granteeId; }
    public void setGranteeId(String granteeId) { this.granteeId = granteeId; }

    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }

    public Long getGrantedBy() { return grantedBy; }
    public void setGrantedBy(Long grantedBy) { this.grantedBy = grantedBy; }

    public Instant getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }
}
```

- [ ] **Step 2: 创建 Repository**

```java
package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsScreenAccessRepository extends JpaRepository<AnalyticsScreenAccess, Long> {

    List<AnalyticsScreenAccess> findByScreenId(Long screenId);

    Optional<AnalyticsScreenAccess> findByScreenIdAndGranteeTypeAndGranteeId(
            Long screenId, String granteeType, String granteeId);

    @Modifying
    @Query("DELETE FROM AnalyticsScreenAccess a WHERE a.screenId = :screenId")
    void deleteByScreenId(@Param("screenId") Long screenId);

    /**
     * Returns screen IDs where the user has a direct USER grant
     * OR the user holds one of the given roles with a ROLE grant.
     * Pass roles = ["__NO_ROLE__"] when the user has no roles, to avoid empty IN clause.
     */
    @Query("SELECT DISTINCT a.screenId FROM AnalyticsScreenAccess a WHERE " +
           "(a.granteeType = 'USER' AND a.granteeId = :userId) OR " +
           "(a.granteeType = 'ROLE' AND a.granteeId IN :roles)")
    List<Long> findAccessibleScreenIds(
            @Param("userId") String userId,
            @Param("roles") List<String> roles);

    /**
     * Returns matching grant rows for a single screen, for a given user + roles.
     * Used by ScreenPermissionService.snapshot().
     */
    @Query("SELECT a FROM AnalyticsScreenAccess a WHERE a.screenId = :screenId AND " +
           "((a.granteeType = 'USER' AND a.granteeId = :userId) OR " +
           "(a.granteeType = 'ROLE' AND a.granteeId IN :roles))")
    List<AnalyticsScreenAccess> findGrantsForUser(
            @Param("screenId") Long screenId,
            @Param("userId") String userId,
            @Param("roles") List<String> roles);
}
```

- [ ] **Step 3: 编译验证**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics
mvn compile -q
```

期望：BUILD SUCCESS，无编译错误。

- [ ] **Step 4: Commit**

```bash
git add source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/domain/AnalyticsScreenAccess.java
git add source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/repository/AnalyticsScreenAccessRepository.java
git commit -m "feat(sprint-33): add AnalyticsScreenAccess entity and repository"
```

---

## Task 3: ScreenPermissionService 重写

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenPermissionService.java`
- Create: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/service/ScreenPermissionServiceTest.java`

- [ ] **Step 1: 编写失败测试**

新建测试文件（无 Spring 上下文，直接实例化）：

```java
package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAccessRepository;
import com.yuzhi.dts.analytics.service.ScreenPermissionService.PermissionSnapshot;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ScreenPermissionServiceTest {

    private AnalyticsScreenAccessRepository repo;
    private ScreenPermissionService service;

    private AnalyticsUser user(long id, boolean superuser) {
        AnalyticsUser u = new AnalyticsUser();
        u.setId(id);
        u.setSuperuser(superuser);
        u.setEmail("user" + id + "@test.com");
        return u;
    }

    private AnalyticsScreen screen(long id) {
        AnalyticsScreen s = new AnalyticsScreen();
        s.setId(id);
        return s;
    }

    private AnalyticsScreenAccess access(long screenId, String granteeType, String granteeId, String permission) {
        AnalyticsScreenAccess a = new AnalyticsScreenAccess();
        a.setScreenId(screenId);
        a.setGranteeType(granteeType);
        a.setGranteeId(granteeId);
        a.setPermission(permission);
        return a;
    }

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(AnalyticsScreenAccessRepository.class);
        service = new ScreenPermissionService(repo);
    }

    @Test
    void superuser_gets_all_permissions() {
        AnalyticsUser su = user(1L, true);
        PermissionSnapshot snap = service.snapshot(screen(10L), su, List.of());
        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isTrue();
        assertThat(snap.isOwner()).isTrue();
    }

    @Test
    void superuser_list_returns_sentinel() {
        AnalyticsUser su = user(1L, true);
        List<Long> ids = service.listAccessibleScreenIds(su, List.of());
        assertThat(service.isAllAccessible(ids)).isTrue();
    }

    @Test
    void owner_gets_all_permissions() {
        AnalyticsUser u = user(2L, false);
        when(repo.findGrantsForUser(eq(10L), eq("2"), any())).thenReturn(
            List.of(access(10L, "USER", "2", "OWNER")));
        PermissionSnapshot snap = service.snapshot(screen(10L), u, List.of());
        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isTrue();
        assertThat(snap.isOwner()).isTrue();
    }

    @Test
    void manager_can_read_and_manage_but_not_edit() {
        AnalyticsUser u = user(3L, false);
        when(repo.findGrantsForUser(eq(10L), eq("3"), any())).thenReturn(
            List.of(access(10L, "USER", "3", "MANAGER")));
        PermissionSnapshot snap = service.snapshot(screen(10L), u, List.of());
        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isFalse();
        assertThat(snap.isOwner()).isTrue();
    }

    @Test
    void viewer_can_read_only() {
        AnalyticsUser u = user(4L, false);
        when(repo.findGrantsForUser(eq(10L), eq("4"), any())).thenReturn(
            List.of(access(10L, "USER", "4", "VIEWER")));
        PermissionSnapshot snap = service.snapshot(screen(10L), u, List.of());
        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isFalse();
        assertThat(snap.isOwner()).isFalse();
    }

    @Test
    void no_grant_gets_none() {
        AnalyticsUser u = user(5L, false);
        when(repo.findGrantsForUser(eq(10L), eq("5"), any())).thenReturn(List.of());
        PermissionSnapshot snap = service.snapshot(screen(10L), u, List.of());
        assertThat(snap.canRead()).isFalse();
        assertThat(snap.canEdit()).isFalse();
        assertThat(snap.isOwner()).isFalse();
    }

    @Test
    void role_grant_gives_viewer_permission() {
        AnalyticsUser u = user(6L, false);
        when(repo.findGrantsForUser(eq(10L), eq("6"), eq(List.of("ROLE_ANALYST")))).thenReturn(
            List.of(access(10L, "ROLE", "ROLE_ANALYST", "VIEWER")));
        PermissionSnapshot snap = service.snapshot(screen(10L), u, List.of("ROLE_ANALYST"));
        assertThat(snap.canRead()).isTrue();
        assertThat(snap.canEdit()).isFalse();
    }

    @Test
    void list_accessible_uses_no_role_sentinel_when_empty_roles() {
        AnalyticsUser u = user(7L, false);
        when(repo.findAccessibleScreenIds(eq("7"), eq(List.of("__NO_ROLE__")))).thenReturn(List.of(1L, 2L));
        List<Long> ids = service.listAccessibleScreenIds(u, List.of());
        assertThat(ids).containsExactly(1L, 2L);
    }

    @Test
    void null_user_returns_none_snapshot() {
        PermissionSnapshot snap = service.snapshot(screen(10L), null, List.of());
        assertThat(snap.canRead()).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics
mvn test -pl . -Dtest=ScreenPermissionServiceTest -q 2>&1 | tail -20
```

期望：编译错误或测试失败（因为 `ScreenPermissionService` 还是旧实现）。

- [ ] **Step 3: 重写 ScreenPermissionService**

完整替换文件内容：

```java
package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAccessRepository;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Screen permission service — all decisions delegated to local analytics_screen_access table.
 *
 * <p>Permission levels:
 * <ul>
 *   <li>OWNER   → {@link PermissionSnapshot#all()} (canRead + canEdit + isOwner)</li>
 *   <li>MANAGER → {@link PermissionSnapshot#managerOnly()} (canRead + isOwner, no canEdit)</li>
 *   <li>VIEWER  → {@link PermissionSnapshot#readOnly()} (canRead only)</li>
 *   <li>no grant → {@link PermissionSnapshot#none()}</li>
 * </ul>
 *
 * <p>Superuser bypass: {@code analytics_user.superuser = true} → skip table, full access.
 * No hardcoded role names. No default grants.
 */
@Service
public class ScreenPermissionService {

    /** Sentinel list: first element is -1L, indicates access to ALL screens (superuser). */
    private static final List<Long> ALL_MARKER = List.of(-1L);

    /**
     * Placeholder role used in JPQL IN clause when the user has no roles,
     * to prevent empty collection binding which causes a SQL syntax error.
     */
    private static final String NO_ROLE_PLACEHOLDER = "__NO_ROLE__";

    private final AnalyticsScreenAccessRepository accessRepository;

    public ScreenPermissionService(AnalyticsScreenAccessRepository accessRepository) {
        this.accessRepository = accessRepository;
    }

    // ---- Permission snapshot ----

    public record PermissionSnapshot(boolean canRead, boolean canEdit, boolean isOwner) {

        public static PermissionSnapshot all() {
            return new PermissionSnapshot(true, true, true);
        }

        /** MANAGER: can read and manage grants, but cannot edit screen content. */
        public static PermissionSnapshot managerOnly() {
            return new PermissionSnapshot(true, false, true);
        }

        public static PermissionSnapshot readOnly() {
            return new PermissionSnapshot(true, false, false);
        }

        public static PermissionSnapshot none() {
            return new PermissionSnapshot(false, false, false);
        }
    }

    // ---- Main permission check ----

    /**
     * Build a permission snapshot by querying the local access table.
     *
     * @param roles list of role names from X-DTS-Roles header; may be empty
     */
    public PermissionSnapshot snapshot(AnalyticsScreen screen, AnalyticsUser user, List<String> roles) {
        if (user == null) {
            return PermissionSnapshot.none();
        }
        if (user.isSuperuser()) {
            return PermissionSnapshot.all();
        }

        String userId = String.valueOf(user.getId());
        List<String> safeRoles = safeRoles(roles);

        List<AnalyticsScreenAccess> grants = accessRepository.findGrantsForUser(
                screen.getId(), userId, safeRoles);

        // Highest permission wins: OWNER > MANAGER > VIEWER
        boolean hasOwner = grants.stream().anyMatch(g -> "OWNER".equalsIgnoreCase(g.getPermission()));
        if (hasOwner) {
            return PermissionSnapshot.all();
        }
        boolean hasManager = grants.stream().anyMatch(g -> "MANAGER".equalsIgnoreCase(g.getPermission()));
        if (hasManager) {
            return PermissionSnapshot.managerOnly();
        }
        boolean hasViewer = grants.stream().anyMatch(g -> "VIEWER".equalsIgnoreCase(g.getPermission()));
        if (hasViewer) {
            return PermissionSnapshot.readOnly();
        }
        return PermissionSnapshot.none();
    }

    // ---- Accessible screen IDs ----

    /**
     * Returns IDs of screens accessible to the user.
     * Returns sentinel {@code [-1L]} when the user is a superuser (access to all screens).
     *
     * @param roles list of role names from X-DTS-Roles header; may be empty
     */
    public List<Long> listAccessibleScreenIds(AnalyticsUser user, List<String> roles) {
        if (user == null) {
            return Collections.emptyList();
        }
        if (user.isSuperuser()) {
            return ALL_MARKER;
        }

        String userId = String.valueOf(user.getId());
        List<String> safeRoles = safeRoles(roles);

        List<Long> ids = accessRepository.findAccessibleScreenIds(userId, safeRoles);
        return ids.isEmpty() ? Collections.emptyList() : List.copyOf(ids);
    }

    /**
     * Check whether the returned list represents "all accessible" (superuser sentinel).
     */
    public boolean isAllAccessible(List<Long> ids) {
        return ids != null && ids.size() == 1 && ids.getFirst().equals(-1L);
    }

    // ---- Stub methods for future implementation ----

    public boolean checkClassification(AnalyticsScreen screen, String personnelLevel) {
        return true;
    }

    public boolean checkDataSourceAccess(AnalyticsScreen screen, AnalyticsUser user) {
        return true;
    }

    // ---- Private helpers ----

    private List<String> safeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of(NO_ROLE_PLACEHOLDER);
        }
        return roles;
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics
mvn test -pl . -Dtest=ScreenPermissionServiceTest -q 2>&1 | tail -10
```

期望：`Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenPermissionService.java
git add source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/service/ScreenPermissionServiceTest.java
git commit -m "feat(sprint-33): rewrite ScreenPermissionService to use local access table"
```

---

## Task 4: ScreenOwnershipService 本地化重构

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenOwnershipService.java`
- Create: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/service/ScreenOwnershipServiceLocalTest.java`

- [ ] **Step 1: 编写失败测试**

```java
package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAccessRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScreenOwnershipServiceLocalTest {

    private AnalyticsScreenAccessRepository repo;
    private ScreenOwnershipService service;

    private AnalyticsScreenAccess access(Long id, Long screenId, String type, String granteeId, String perm) {
        AnalyticsScreenAccess a = new AnalyticsScreenAccess();
        a.setId(id);
        a.setScreenId(screenId);
        a.setGranteeType(type);
        a.setGranteeId(granteeId);
        a.setPermission(perm);
        a.setGrantedAt(Instant.now());
        return a;
    }

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(AnalyticsScreenAccessRepository.class);
        service = new ScreenOwnershipService(repo);
    }

    @Test
    void listGrants_returns_mapped_records() {
        when(repo.findByScreenId(1L)).thenReturn(List.of(
            access(10L, 1L, "USER", "42", "OWNER"),
            access(11L, 1L, "USER", "99", "VIEWER")));
        List<Map<String, Object>> grants = service.listGrants(1L);
        assertThat(grants).hasSize(2);
        assertThat(grants.get(0)).containsEntry("granteeId", "42");
        assertThat(grants.get(0)).containsEntry("permission", "OWNER");
    }

    @Test
    void createGrant_inserts_new_record() {
        when(repo.findByScreenIdAndGranteeTypeAndGranteeId(1L, "USER", "42")).thenReturn(Optional.empty());
        ArgumentCaptor<AnalyticsScreenAccess> captor = ArgumentCaptor.forClass(AnalyticsScreenAccess.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createGrant(1L, "USER", "42", "OWNER", 7L);

        verify(repo).save(captor.capture());
        AnalyticsScreenAccess saved = captor.getValue();
        assertThat(saved.getScreenId()).isEqualTo(1L);
        assertThat(saved.getGranteeId()).isEqualTo("42");
        assertThat(saved.getPermission()).isEqualTo("OWNER");
        assertThat(saved.getGrantedBy()).isEqualTo(7L);
    }

    @Test
    void createGrant_updates_existing_record() {
        AnalyticsScreenAccess existing = access(10L, 1L, "USER", "42", "VIEWER");
        when(repo.findByScreenIdAndGranteeTypeAndGranteeId(1L, "USER", "42")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createGrant(1L, "USER", "42", "MANAGER", 7L);

        verify(repo).save(existing);
        assertThat(existing.getPermission()).isEqualTo("MANAGER");
    }

    @Test
    void removeAllGrants_deletes_by_screen_id() {
        service.removeAllGrants(1L);
        verify(repo).deleteByScreenId(1L);
    }

    @Test
    void revokeGrant_deletes_by_id() {
        service.revokeGrant(10L);
        verify(repo).deleteById(10L);
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics
mvn test -pl . -Dtest=ScreenOwnershipServiceLocalTest -q 2>&1 | tail -10
```

期望：编译错误（因为当前 `ScreenOwnershipService` 构造函数不匹配）。

- [ ] **Step 3: 完全重写 ScreenOwnershipService**

```java
package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAccessRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Screen ownership service — manages grants in the local analytics_screen_access table.
 * No platform API calls. No RestTemplate.
 */
@Service
public class ScreenOwnershipService {

    private final AnalyticsScreenAccessRepository accessRepository;

    public ScreenOwnershipService(AnalyticsScreenAccessRepository accessRepository) {
        this.accessRepository = accessRepository;
    }

    /**
     * List all grants for a screen, as map objects compatible with the frontend ScreenSharePanel format.
     */
    public List<Map<String, Object>> listGrants(Long screenId) {
        return accessRepository.findByScreenId(screenId).stream()
                .map(this::toMap)
                .toList();
    }

    /**
     * Create or update a grant (UPSERT by screenId + granteeType + granteeId).
     *
     * @param screenId    target screen
     * @param granteeType "USER" or "ROLE"
     * @param granteeId   analytics user.id as String for USER, role name for ROLE
     * @param permission  "OWNER", "MANAGER", or "VIEWER"
     * @param grantedBy   analytics user.id of the granter
     */
    @Transactional
    public AnalyticsScreenAccess createGrant(Long screenId, String granteeType, String granteeId,
                                              String permission, Long grantedBy) {
        Optional<AnalyticsScreenAccess> existing =
                accessRepository.findByScreenIdAndGranteeTypeAndGranteeId(screenId, granteeType, granteeId);

        AnalyticsScreenAccess record = existing.orElseGet(AnalyticsScreenAccess::new);
        record.setScreenId(screenId);
        record.setGranteeType(granteeType);
        record.setGranteeId(granteeId);
        record.setPermission(permission);
        record.setGrantedBy(grantedBy);
        if (record.getGrantedAt() == null) {
            record.setGrantedAt(Instant.now());
        }
        return accessRepository.save(record);
    }

    /**
     * Revoke (delete) a specific grant by its local table ID.
     */
    @Transactional
    public void revokeGrant(Long grantId) {
        accessRepository.deleteById(grantId);
    }

    /**
     * Remove all grants for a screen. Call on screen deletion/archival.
     */
    @Transactional
    public void removeAllGrants(Long screenId) {
        accessRepository.deleteByScreenId(screenId);
    }

    private Map<String, Object> toMap(AnalyticsScreenAccess a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", a.getId());
        map.put("screenId", a.getScreenId());
        map.put("granteeType", a.getGranteeType());
        map.put("granteeId", a.getGranteeId());
        map.put("permission", a.getPermission());
        map.put("grantedBy", a.getGrantedBy());
        map.put("grantedAt", a.getGrantedAt());
        return map;
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics
mvn test -pl . -Dtest=ScreenOwnershipServiceLocalTest -q 2>&1 | tail -10
```

期望：`Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenOwnershipService.java
git add source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/service/ScreenOwnershipServiceLocalTest.java
git commit -m "feat(sprint-33): rewrite ScreenOwnershipService to use local access table"
```

---

## Task 5: ScreenResource 端点适配

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java`

涉及修改点（按方法）：

1. **`list()`** — 替换 `PlatformContext` 权限逻辑，改用 `List<Long>` ID
2. **`create()`** — 替换 `registerOwnership` + 平台 `createGrant` 为本地 `createGrant`
3. **所有 `snapshot(screen, user, context)` 调用** — 改为 `snapshot(screen, user, extractRoles(request))`
4. **`addGrant()`** — 删除 `resolveGranteeUsername`，接受 VIEWER/MANAGER 权限
5. **`delete()`** — 替换 `screenOwnershipService.removeOwnership` 为 `removeAllGrants`
6. **`revokeGrant()`** — 简化（本地表不需要先 list 再找 granteeId）
7. **删除私有方法** — `resolveGranteeUsername`, `extractUsername`

- [ ] **Step 1: 添加 `extractRoles` 私有方法**

在 `ScreenResource.java` 中找到私有方法区域（约第 2361 行 `extractUsername` 之后），添加：

```java
    /**
     * Extract roles from X-DTS-Roles header, split by comma.
     * Returns empty list if header is absent or blank.
     */
    private List<String> extractRoles(HttpServletRequest request) {
        String header = request.getHeader("X-DTS-Roles");
        if (header == null || header.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
```

- [ ] **Step 2: 修改 `list()` 方法**

将当前 `list()` 方法（约 114-154 行）中的以下代码：

```java
        PlatformContext context = PlatformContext.from(request);

        // Get accessible screen IDs first (efficient: single platform API call)
        List<String> accessibleIds = screenPermissionService.listAccessibleScreenIds(user.orElseThrow(), context);

        List<AnalyticsScreen> screens;
        if (screenPermissionService.isAllAccessible(accessibleIds)) {
            screens = screenRepository.findAllByArchivedFalseOrderByIdDesc();
        } else if (accessibleIds.isEmpty()) {
            screens = List.of();
        } else {
            // Convert string IDs to Long and query
            List<Long> ids = accessibleIds.stream()
                .map(s -> { try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; } })
                .filter(Objects::nonNull)
                .toList();
            screens = ids.isEmpty() ? List.of() : screenRepository.findAllByIdInAndArchivedFalse(ids);
        }

        List<ObjectNode> result = screens.stream()
                .map(screen -> {
                    ScreenPermissionService.PermissionSnapshot permissions = screenPermissionService.snapshot(screen, user.orElseThrow(), context);
```

替换为：

```java
        List<String> roles = extractRoles(request);

        List<Long> accessibleIds = screenPermissionService.listAccessibleScreenIds(user.orElseThrow(), roles);

        List<AnalyticsScreen> screens;
        if (screenPermissionService.isAllAccessible(accessibleIds)) {
            screens = screenRepository.findAllByArchivedFalseOrderByIdDesc();
        } else if (accessibleIds.isEmpty()) {
            screens = List.of();
        } else {
            screens = screenRepository.findAllByIdInAndArchivedFalse(accessibleIds);
        }

        List<ObjectNode> result = screens.stream()
                .map(screen -> {
                    ScreenPermissionService.PermissionSnapshot permissions = screenPermissionService.snapshot(screen, user.orElseThrow(), roles);
```

- [ ] **Step 3: 修改 `create()` 方法**

将 `create()` 中的（约 774-781 行）：

```java
        String creatorUsername = extractUsername(user.orElseThrow());
        screenOwnershipService.registerOwnership(screen.getId(), creatorUsername, PlatformContext.from(request).dept());
        // 在平台注册创建者的 MANAGE 授权，使其可以通过平台权限系统发现并管理自己创建的大屏。
        // 平台的 listAccessibleAssetIds 只查 explicit grants，不查 ownership，因此必须显式建档。
        if (!creatorUsername.isBlank()) {
            screenOwnershipService.createGrant(screen.getId(), "USER", creatorUsername, "MANAGE", creatorUsername);
            screenPermissionService.invalidateCacheForUser(creatorUsername);
        }
```

替换为：

```java
        screenOwnershipService.createGrant(screen.getId(), "USER",
                String.valueOf(user.orElseThrow().getId()), "OWNER", user.orElseThrow().getId());
```

同时删除 `screen.setOwnerDeptCode(PlatformContext.from(request).dept());` 这一行（该字段仍保留于 entity，但无需从 PlatformContext 取值，设为 null 即可）。

注意：`create()` 方法中需要将 `ScreenPermissionService.PermissionSnapshot permissions = ScreenPermissionService.PermissionSnapshot.all();` 保持不变（创建者始终返回全量权限）。

- [ ] **Step 4: 修改所有其他 snapshot 调用**

搜索所有 `screenPermissionService.snapshot(screen, user.orElseThrow(), context)` 或 `screenPermissionService.snapshot(screen, user.orElseThrow(), ctx)` 的调用（涉及 `update`, `get`, `classify`, `publish`, `rollback`, `delete`, `getGrants`, `addGrant`, `revokeGrant`, `createPublicLink`, `updatePublicLinkPolicy`, `deletePublicLink`, `serverRenderExport` 等方法）。

每处改法：
1. 删除 `PlatformContext context = PlatformContext.from(request);` 这行（或改名 `ctx` 也替换）
2. 添加 `List<String> roles = extractRoles(request);`（若该方法已有则复用）
3. 将 `snapshot(screen, user.orElseThrow(), context)` 改为 `snapshot(screen, user.orElseThrow(), roles)`

> **注意**: 有些方法同时用 `context.dept()` 和 `context.classification()`（如 `createPublicLink`）。这些字段保留，从 request 直接读：
> ```java
> String dept = request.getHeader("X-DTS-Dept");
> String classification = request.getHeader("X-DTS-Classification");
> ```
> 把 `ctx.dept()` 替换为 `dept`，`ctx.classification()` 替换为 `classification`。

- [ ] **Step 5: 修改 `addGrant()` 方法**

将权限验证部分：

```java
        if (!Set.of("USER", "DEPT", "ROLE").contains(granteeType.toUpperCase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "granteeType must be USER, DEPT, or ROLE"));
        }
        if (!Set.of("READ", "EDIT").contains(permission.toUpperCase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "permission must be READ or EDIT"));
        }
```

替换为：

```java
        if (!Set.of("USER", "ROLE").contains(granteeType.toUpperCase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "granteeType must be USER or ROLE"));
        }
        if (!Set.of("VIEWER", "MANAGER").contains(permission.toUpperCase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "permission must be VIEWER or MANAGER"));
        }
```

将 `addGrant()` 中的 try 块（约 1197-1215 行）：

```java
        try {
            String grantedBy = extractUsername(user.orElseThrow());
            // When granteeType is USER, the frontend sends the analytics numeric ID (e.g. "42").
            // The platform grant system uses platform usernames ("test230916") for USER grants.
            // Translate: if granteeId looks like a numeric analytics ID, resolve the platform username.
            String resolvedGranteeId = granteeId;
            if ("USER".equalsIgnoreCase(granteeType)) {
                resolvedGranteeId = resolveGranteeUsername(granteeId);
            }
            Map<String, Object> grant = screenOwnershipService.createGrant(
                screen.getId(), granteeType.toUpperCase(), resolvedGranteeId, permission.toUpperCase(), grantedBy);

            screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "grant.add", null, grant, requestIdFrom(request));
            // Invalidate permission cache for the grantee so they see the screen immediately
            screenPermissionService.invalidateCacheForUser(resolvedGranteeId);
            return ResponseEntity.ok(grant);
        } catch (Exception ex) {
            return ResponseEntity.status(503).body(Map.of("error", "Failed to create grant: " + ex.getMessage()));
        }
```

替换为：

```java
        try {
            Long grantedById = user.orElseThrow().getId();
            AnalyticsScreenAccess grant = screenOwnershipService.createGrant(
                screen.getId(), granteeType.toUpperCase(), granteeId, permission.toUpperCase(), grantedById);

            Map<String, Object> grantMap = Map.of(
                "id", grant.getId(),
                "screenId", grant.getScreenId(),
                "granteeType", grant.getGranteeType(),
                "granteeId", grant.getGranteeId(),
                "permission", grant.getPermission(),
                "grantedBy", grant.getGrantedBy() != null ? grant.getGrantedBy() : "",
                "grantedAt", grant.getGrantedAt());
            screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "grant.add", null,
                objectMapper.valueToTree(grantMap), requestIdFrom(request));
            return ResponseEntity.ok(grantMap);
        } catch (Exception ex) {
            return ResponseEntity.status(503).body(Map.of("error", "Failed to create grant: " + ex.getMessage()));
        }
```

需要在文件顶部 import 中添加：`import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;`

- [ ] **Step 6: 修改 `delete()` 和 `revokeGrant()`**

在 `delete()` 中，将：

```java
        screenOwnershipService.removeOwnership(screen.getId());
```

替换为：

```java
        screenOwnershipService.removeAllGrants(screen.getId());
```

在 `revokeGrant()` 中，将原来的 list-then-find 逻辑简化（本地表的 grant 在删除后无法再查 granteeId，但我们也不需要 invalidate cache 了）：

```java
    @DeleteMapping(path = "/{id}/grants/{grantId}")
    public ResponseEntity<?> revokeGrant(
            @PathVariable("id") long id,
            @PathVariable("grantId") long grantId,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) return unauthorized();

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) return ResponseEntity.notFound().build();

        List<String> roles = extractRoles(request);
        ScreenPermissionService.PermissionSnapshot perms = screenPermissionService.snapshot(screen, user.orElseThrow(), roles);
        if (!perms.isOwner()) return forbidden();

        try {
            screenOwnershipService.revokeGrant(grantId);
            screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "grant.revoke",
                Map.of("grantId", grantId), null, requestIdFrom(request));
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (Exception ex) {
            return ResponseEntity.status(503).body(Map.of("error", "Failed to revoke grant: " + ex.getMessage()));
        }
    }
```

- [ ] **Step 7: 修改 `backfillGrants()` 方法**

当前 backfill 是给平台系统补 MANAGE grant，现在要改为给本地表补 OWNER grant。将 `backfillGrants()` 方法（约 2284 行起）替换为：

```java
    /**
     * One-time admin endpoint: backfill OWNER grants in local analytics_screen_access table
     * for screens created before Sprint-33. For each screen, inserts an OWNER grant for the creator
     * if one does not already exist.
     */
    @PostMapping(path = "/admin/backfill-grants", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> backfillGrants(HttpServletRequest request) {
        Optional<ResponseEntity<String>> authError = MetabaseAuth.requireSuperuser(sessionService, request);
        if (authError.isPresent()) {
            return authError.orElseThrow();
        }

        List<AnalyticsScreen> allScreens = screenRepository.findAll();
        int created = 0;
        int skipped = 0;

        for (AnalyticsScreen screen : allScreens) {
            Long creatorId = screen.getCreatorId();
            if (creatorId == null) {
                skipped++;
                continue;
            }
            String granteeId = String.valueOf(creatorId);
            // Check if OWNER grant already exists
            var existing = screenOwnershipService.listGrants(screen.getId()).stream()
                .filter(g -> "USER".equals(g.get("granteeType")) && granteeId.equals(g.get("granteeId"))
                    && "OWNER".equals(g.get("permission")))
                .findAny();
            if (existing.isPresent()) {
                skipped++;
                continue;
            }
            screenOwnershipService.createGrant(screen.getId(), "USER", granteeId, "OWNER", creatorId);
            created++;
        }

        return ResponseEntity.ok(Map.of(
            "total", allScreens.size(),
            "created", created,
            "skipped", skipped));
    }
```

- [ ] **Step 8: 删除 `resolveGranteeUsername` 和 `extractUsername` 私有方法**

删除以下两个私有方法（约 2340-2375 行）：
- `resolveGranteeUsername(String granteeId)` — 整个方法体
- `extractUsername(AnalyticsUser user)` — 整个方法体

同时检查并删除 `ScreenResource` 中所有 `import` 里现在不再使用的：
- `PlatformContext` 的使用（如果还有残留）

- [ ] **Step 9: 编译验证**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics
mvn compile -q 2>&1 | tail -20
```

期望：BUILD SUCCESS。若有编译错误，逐条修复（通常是残留的 `PlatformContext` 引用或 `invalidateCacheForUser` 调用）。

- [ ] **Step 10: Commit**

```bash
git add source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java
git commit -m "feat(sprint-33): adapt ScreenResource endpoints to local permission table"
```

---

## Task 6: 旧代码清理

**Files:**
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/PlatformPermissionClient.java` — 删除
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenPermissionService.java` — 清理残留 import
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java` — 清理残留 import

- [ ] **Step 1: 确认 PlatformPermissionClient 无其他引用**

```bash
grep -r "PlatformPermissionClient" \
  /opt/prod/s10/s10-stack/source/dts-analytics/src \
  --include="*.java"
```

期望：只有 `PlatformPermissionClient.java` 文件本身（或零结果）。若有其他引用，先修复再删除。

- [ ] **Step 2: 删除 PlatformPermissionClient.java**

```bash
rm /opt/prod/s10/s10-stack/source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/PlatformPermissionClient.java
```

- [ ] **Step 3: 清理旧 ACL 相关代码**

运行检查：

```bash
grep -r "ScreenAcl\|screen_acl\|aclService\|AclService\|PlatformContext\|registerOwnership\|transferOwnership\|removeOwnership\|invalidateCacheForUser\|platformPermissionClient\|platformBaseUrl\|RestTemplate" \
  /opt/prod/s10/s10-stack/source/dts-analytics/src \
  --include="*.java" -l
```

对每个匹配文件，检查是否为合法残留（如 audit log 字符串里提到 "acl" 是允许的），不合法的引用一并清理。

- [ ] **Step 4: 确认编译通过**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics
mvn compile -q
```

期望：BUILD SUCCESS。

- [ ] **Step 5: 确认 analytics_webapp 前端旧 ACL 引用**

```bash
grep -r "ScreenAcl\|screen_acl\|aclService\|updateAcl\|getAcl" \
  /opt/prod/s10/s10-stack/source/dts-platform-webapp/src/analytics \
  --include="*.ts" --include="*.tsx"
```

如有旧 ACL 残留（非 `ScreenAclPanel` 组件本身），删除相关导入和调用。

- [ ] **Step 6: Commit**

```bash
git add -u
git commit -m "chore(sprint-33): remove PlatformPermissionClient and old ACL dead code"
```

---

## Task 7: 前端 ScreenAclPanel/ScreenSharePanel 验证与清理（F2-T01, T02）

**Files:**
- `source/dts-platform-webapp/src/analytics/pages/screens/components/ScreenSharePanel.tsx` — 验证
- `source/dts-platform-webapp/src/analytics/pages/screens/components/ScreenAclPanel.tsx` — 验证/修改
- `source/dts-platform-webapp/src/analytics/api/analyticsApi.ts` — 验证

- [ ] **Step 1: 确认 ScreenSharePanel 用 analytics 数字 ID**

读取 `ScreenSharePanel.tsx`，确认：
- `granteeId: String(u.id)` 传递的是 analytics 数字 ID 字符串（如 `"42"`）
- `existingUserIds` 过滤使用 `String(e.subjectId)` 或 `e.granteeId` 与之比对

如果 `e.subjectId` 是旧字段名而新后端返回的是 `e.granteeId`，需更新字段引用。

具体检查点：
```typescript
// 后端现在返回 { id, granteeType, granteeId, permission, grantedBy, grantedAt }
// 确认 ScreenSharePanel 读取 granteeId（而不是旧的 subjectId）
```

如字段名不匹配，在 `ScreenSharePanel.tsx` 中将旧字段名更新为新字段名。

- [ ] **Step 2: 确认 ScreenAclPanel 使用新端点**

读取 `ScreenAclPanel.tsx`，确认：
- 授权调用走 `analyticsApi.addScreenGrant`（`PUT /bi/api/screens/{id}/grants`）
- `granteeId` 传 analytics user.id 数字字符串
- `permission` 传 `"VIEWER"` 或 `"MANAGER"`（**注意**: 旧代码可能传 `"READ"` 或 `"EDIT"`，需更新）
- 无旧 `ScreenAclService` 调用

如 permission 值为旧格式，更新：
```typescript
// 旧
permission: "READ"
// 新
permission: "VIEWER"
```

```typescript
// 旧
permission: "EDIT"
// 新
permission: "MANAGER"
```

- [ ] **Step 3: 确认 analyticsApi.ts 无旧 ACL 方法**

```bash
grep -n "updateAcl\|getAcl\|AclService\|aclService" \
  /opt/prod/s10/s10-stack/source/dts-platform-webapp/src/analytics/api/analyticsApi.ts
```

期望：无匹配。若有，删除相关方法。

- [ ] **Step 4: TypeScript 编译检查**

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform-webapp
npx tsc --noEmit 2>&1 | grep -E "error|Error" | head -20
```

期望：零 TypeScript 错误。

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/
git commit -m "fix(sprint-33): align frontend ScreenAclPanel/SharePanel with local permission table API"
```

---

## Task 8: 全量编译与最终验收

- [ ] **Step 1: 全量 Java 编译**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics
mvn compile -q && echo "COMPILE OK"
```

- [ ] **Step 2: 全量单元测试**

```bash
cd /opt/prod/s10/s10-stack/source/dts-analytics
mvn test -q 2>&1 | tail -15
```

期望：所有测试通过，无失败。

- [ ] **Step 3: 确认 backfill endpoint 存在**

```bash
grep -n "backfill-grants" \
  /opt/prod/s10/s10-stack/source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java
```

期望：找到 `@PostMapping(path = "/admin/backfill-grants"...`

- [ ] **Step 4: 确认无平台 API 调用残留**

```bash
grep -rn "dts-platform\|asset-grants\|asset-ownership\|PlatformPermissionClient" \
  /opt/prod/s10/s10-stack/source/dts-analytics/src/main/java \
  --include="*.java"
```

期望：零匹配（或仅在注释/日志字符串中）。

- [ ] **Step 5: Final commit**

```bash
cd /opt/prod/s10/s10-stack
git log --oneline -8
```

确认所有 Sprint-33 commits 都在当前分支上，然后：

```bash
git commit --allow-empty -m "chore(sprint-33): sprint complete — local permission table fully wired"
```

---

## 端到端验收清单（对应 F2-T03）

部署后执行以下验证：

| 场景 | 步骤 | 期望 |
|------|------|------|
| 场景一 | opadmin 创建新大屏，刷新工作台 | 新大屏出现在列表 |
| 场景二 | opadmin 授权 test230916 VIEWER，test230916 刷新 | 新大屏出现（无需重登录） |
| 场景三 | Win7 Chrome 95 重复场景二 | 结果与场景二一致 |
| 场景四 | opadmin 撤权，test230916 刷新 | 大屏消失 |
| 场景五 | superuser 登录，查看工作台 | 所有大屏可见 |
| 场景六 | MANAGER 权限用户打开大屏 | 可查看、可开权限面板，编辑按钮不可用 |

回归检查：
- `GET /bi/api/screens` 列表与工作台结果一致
- 权限校验不再依赖 `X-DTS-Roles` 头的角色名做硬匹配（仅 ROLE 类型 grant 匹配）
- 后端日志无 platform API 调用（`dts-platform:8081`）
