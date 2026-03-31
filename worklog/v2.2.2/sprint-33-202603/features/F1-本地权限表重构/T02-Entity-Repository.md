# T02: JPA Entity + Repository

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

为 `analytics_screen_access` 新建 JPA Entity 和 Spring Data Repository，提供所有权限查询所需的方法。

## 技术设计

### Entity

```java
// package com.yuzhi.dts.analytics.domain;
@Entity
@Table(name = "analytics_screen_access")
public class AnalyticsScreenAccess {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "screen_id", nullable = false)
    private Long screenId;

    @Column(name = "grantee_type", nullable = false, length = 10)
    private String granteeType;   // "USER" | "ROLE"

    @Column(name = "grantee_id", nullable = false, length = 255)
    private String granteeId;     // USER: String.valueOf(userId); ROLE: "ROLE_XXX"

    @Column(name = "permission", nullable = false, length = 10)
    private String permission;    // "OWNER" | "MANAGER" | "VIEWER"

    @Column(name = "granted_by")
    private Long grantedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // getters + setters
}
```

### Repository

```java
// package com.yuzhi.dts.analytics.repository;
public interface AnalyticsScreenAccessRepository
        extends JpaRepository<AnalyticsScreenAccess, Long> {

    /** 列出某大屏的所有授权记录（用于权限管理 UI） */
    List<AnalyticsScreenAccess> findByScreenId(Long screenId);

    /** 查某用户对某大屏的最高权限（USER 行 + ROLE 行中取最高）*/
    @Query("""
        SELECT a.permission FROM AnalyticsScreenAccess a
        WHERE a.screenId = :screenId
          AND ((a.granteeType = 'USER' AND a.granteeId = :userId)
            OR (a.granteeType = 'ROLE' AND a.granteeId IN :roles))
        ORDER BY CASE a.permission
            WHEN 'OWNER'   THEN 1
            WHEN 'MANAGER' THEN 2
            WHEN 'VIEWER'  THEN 3
            ELSE 4 END
        LIMIT 1
        """)
    Optional<String> findTopPermission(
        @Param("screenId") Long screenId,
        @Param("userId")   String userId,
        @Param("roles")    List<String> roles);

    /** 列出某用户（USER + ROLE）可访问的所有大屏 ID */
    @Query("""
        SELECT DISTINCT a.screenId FROM AnalyticsScreenAccess a
        WHERE (a.granteeType = 'USER' AND a.granteeId = :userId)
           OR (a.granteeType = 'ROLE' AND a.granteeId IN :roles)
        """)
    List<Long> findAccessibleScreenIds(
        @Param("userId") String userId,
        @Param("roles")  List<String> roles);

    /** 查某大屏是否存在指定 grantee 的记录（用于重复检查） */
    boolean existsByScreenIdAndGranteeTypeAndGranteeId(
        Long screenId, String granteeType, String granteeId);

    /** 按 grantee 删除（撤权） */
    void deleteByScreenIdAndGranteeTypeAndGranteeId(
        Long screenId, String granteeType, String granteeId);

    /** 删除大屏的所有授权（归档时清理） */
    void deleteByScreenId(Long screenId);
}
```

**注意**：`findTopPermission` 中 roles 为空时 JPQL `IN :roles` 会产生空 IN 子句导致 SQL 错误，调用方需在 roles 为空时传 `List.of("__EMPTY__")` 或在 Service 层规避（见 T03）。

## 影响范围

- 新建: `domain/AnalyticsScreenAccess.java`
- 新建: `repository/AnalyticsScreenAccessRepository.java`

## 验证

- [ ] `findAccessibleScreenIds` 对空 roles 参数安全（不抛 SQL 异常）
- [ ] `findTopPermission` 返回优先级最高的权限（OWNER > MANAGER > VIEWER）
- [ ] UNIQUE 约束触发时抛 `DataIntegrityViolationException`（正确处理）

## 完成标准

- [ ] 所有方法有对应的集成测试（H2 或 PostgreSQL testcontainer）
- [ ] 编译通过，无警告
