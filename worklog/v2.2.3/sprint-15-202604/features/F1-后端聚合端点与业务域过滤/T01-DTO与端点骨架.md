# T01: LeaderOverview DTO 与端点骨架

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

搭好 `LeaderOverviewRequest` / `LeaderOverviewResponse` DTO、`WorkbenchLeaderOverviewService` 骨架、`WorkbenchResource.leaderOverview(...)` 端点，先返回空数据骨架，保证前端联调可走通。

## 技术设计

### 新建 DTO（放在 `service/workbench/dto/` 子包）

```java
package com.yuzhi.dts.platform.service.workbench.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record LeaderOverviewResponse(
    Instant generatedAt,
    String scope,              // MINE|DEPT|ALL
    String effectiveDeptCode,  // 服务端降级后的 deptCode
    String timeRange,          // MONTH|QUARTER|YEAR
    Kpis kpis,
    List<TopReport> topReports,
    List<TopAsset> topAssets,
    List<DomainCell> domainMatrix
) {
    public record Kpis(
        long reportsTotal,
        long reportsNewInPeriod,
        long visitsInPeriod,
        BigDecimal visitsMoM,      // 环比，可为 null
        long assetsTotal,
        long assetsNewInPeriod,
        long assetsS1,
        long assetsS1S2,           // 部门领导卡"核心资产 S1+S2"直接用
        BigDecimal assetsS1Ratio   // 可为 null
    ) {}

    public record TopReport(
        String id,
        String title,
        long visits,
        String bizDomain,          // 可为 null
        String classification,
        Instant lastVisitedAt      // 可为 null
    ) {}

    public record TopAsset(
        String id,
        String name,
        String classification,
        Instant updatedAt,
        String bizDomain           // 可为 null
    ) {}

    public record DomainCell(
        String domain,             // 域 code；归到"其他"时使用常量 "__OTHER__"
        String domainName,
        long visits
    ) {}
}
```

`scope` / `timeRange` 用顶级常量枚举 `LeaderOverviewScope` / `LeaderOverviewTimeRange` 提高类型安全（放在同包）。

### Service 骨架

`service/workbench/WorkbenchLeaderOverviewService.java`：

```java
@Service
@Transactional(readOnly = true)
public class WorkbenchLeaderOverviewService {

    public LeaderOverviewResponse build(
        String userLogin,
        List<String> userRoles,
        String userDeptCode,
        String requestedScope,
        String requestedDeptCode,
        String bizDomain,
        String timeRange
    ) {
        // T07 会填充降级逻辑
        String scope = normalizeScope(requestedScope);
        String effectiveDept = requestedDeptCode;
        String range = normalizeTimeRange(timeRange);

        return new LeaderOverviewResponse(
            Instant.now(),
            scope,
            effectiveDept,
            range,
            new Kpis(0, 0, 0, null, 0, 0, 0, 0, null),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private String normalizeScope(String s) {
        if (s == null) return "MINE";
        return switch (s.toUpperCase(Locale.ROOT)) {
            case "DEPT", "ALL", "MINE" -> s.toUpperCase(Locale.ROOT);
            default -> "MINE";
        };
    }

    private String normalizeTimeRange(String t) {
        if (t == null) return "MONTH";
        return switch (t.toUpperCase(Locale.ROOT)) {
            case "MONTH", "QUARTER", "YEAR" -> t.toUpperCase(Locale.ROOT);
            default -> "MONTH";
        };
    }
}
```

### Resource 端点

在 `WorkbenchResource.java` 新增：

```java
@GetMapping("/leader-overview")
public ApiResponse<LeaderOverviewResponse> leaderOverview(
    @RequestParam(value = "scope", required = false) String scope,
    @RequestParam(value = "deptCode", required = false) String deptCode,
    @RequestParam(value = "bizDomain", required = false) String bizDomain,
    @RequestParam(value = "timeRange", required = false, defaultValue = "MONTH") String timeRange
) {
    String user = SecurityUtils.getCurrentUserLogin().orElseThrow();
    List<String> roles = SecurityUtils.getCurrentUserAuthorities();   // 新增 helper，见下
    String userDept = SecurityUtils.getCurrentUserDept().orElse(null); // 新增 helper，见下
    LeaderOverviewResponse payload = leaderOverviewService.build(user, roles, userDept, scope, deptCode, bizDomain, timeRange);
    auditService.audit("READ", "workbench.leader-overview", "scope=" + payload.scope() + ",dept=" + payload.effectiveDeptCode());
    return ApiResponses.ok(payload);
}
```

`SecurityUtils.getCurrentUserAuthorities()` / `getCurrentUserDept()`：若项目已有类似 helper 直接复用；否则在 `SecurityUtils` 新增：

```java
public static List<String> getCurrentUserAuthorities() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .map(a -> a.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
        .orElse(List.of());
}

public static Optional<String> getCurrentUserDept() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .filter(a -> a.getPrincipal() instanceof Jwt)
        .map(a -> (Jwt) a.getPrincipal())
        .map(jwt -> jwt.getClaimAsString("dept_code"));
}
```

> **注意**：`dept_code` claim 实际字段名以项目 Keycloak 配置为准，需查项目现有 `SecurityUtils` 相关扩展点后确认。

## 影响范围

- **新增**:
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/workbench/dto/LeaderOverviewResponse.java`
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/workbench/WorkbenchLeaderOverviewService.java`
- **修改**:
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/WorkbenchResource.java`（注入新 Service、新增端点方法）
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/SecurityUtils.java`（若缺少 helper）

## 验证

- [ ] `./mvnw -pl source/dts-platform compile` 通过。
- [ ] Postman / curl：`GET /api/workbench/leader-overview?scope=DEPT&timeRange=MONTH` 返回 200 + 空结构。
- [ ] 未登录请求返回 401（沿用项目既有安全链）。

## 完成标准

- [ ] DTO 编译通过，字段类型与契约一致。
- [ ] Resource + Service 骨架能独立响应，scope/timeRange 能正常 echo 回来（未做降级前先暴露用户传入值，T07 会覆盖）。
- [ ] `@Transactional(readOnly = true)` 正确标注。
