# T07: scope 参数角色权限降级保护

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

服务端对 `scope` 入参做**强制**角色降级，防止越权：

- 非所领导传 `scope=ALL` → 强制降级为 `scope=DEPT` + 用户 `deptCode`。
- 非领导（既非部门领导也非所领导）传 `scope=DEPT` → 强制降级为 `scope=MINE`。
- 空值默认按用户身份推断：所领导默认 `ALL`，部门领导默认 `DEPT`，员工默认 `MINE`。
- 响应体里 `scope` / `effectiveDeptCode` 两个字段回显**降级后的真值**，让前端看到同一事实。

## 技术设计

### 角色常量

复用项目现有常量（在 `AuthoritiesConstants` 或类似）：

- `ROLE_INST_LEADER` / `INST_LEADER`（所级领导）
- 部门领导：当前没有精确常量，使用项目现有映射。如果项目用的是 position 字段（`"部门领导" / "副所领导" / "财务主管"`），需要读 `SecurityUtils.getCurrentUserPosition()`。若不存在 helper，临时以 "非 INST_LEADER 且 roles 包含以下之一" 粗判：`ROLE_DEPT_LEADER`、`DEPT_LEADER`、`SUB_INST_LEADER`、`FIN_MANAGER`。

为避免常量散落，新增 `WorkbenchRoleResolver`（service 层）封装这套判定：

```java
@Component
public class WorkbenchRoleResolver {

    private static final Set<String> INST_LEADER_CODES = Set.of("ROLE_INST_LEADER", "INST_LEADER");
    private static final Set<String> DEPT_LEADER_CODES = Set.of(
        "ROLE_DEPT_LEADER", "DEPT_LEADER", "SUB_INST_LEADER", "FIN_MANAGER"
    );

    public enum Role { EMP, DEPT_LEADER, INST_LEADER }

    public Role resolve(List<String> roles) {
        Set<String> normalized = roles.stream()
            .map(r -> r == null ? "" : r.toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());
        if (normalized.stream().anyMatch(INST_LEADER_CODES::contains)) return Role.INST_LEADER;
        if (normalized.stream().anyMatch(DEPT_LEADER_CODES::contains)) return Role.DEPT_LEADER;
        return Role.EMP;
    }
}
```

### 降级

在 `WorkbenchLeaderOverviewService.build(...)` 最顶部替换 T01 的 placeholder：

```java
Role role = roleResolver.resolve(userRoles);
String scope;
String effectiveDept;

if (requestedScope == null) {
    scope = switch (role) {
        case INST_LEADER -> "ALL";
        case DEPT_LEADER -> "DEPT";
        case EMP -> "MINE";
    };
} else {
    scope = requestedScope.toUpperCase(Locale.ROOT);
    if ("ALL".equals(scope) && role != Role.INST_LEADER) {
        log.warn("user={} role={} requested scope=ALL, downgraded to DEPT", userLogin, role);
        scope = "DEPT";
    }
    if ("DEPT".equals(scope) && role == Role.EMP) {
        log.warn("user={} role=EMP requested scope=DEPT, downgraded to MINE", userLogin);
        scope = "MINE";
    }
}

effectiveDept = switch (scope) {
    case "MINE" -> null;
    case "DEPT" -> userDeptCode;           // 非所领导强制用自身 dept
    case "ALL" -> requestedDeptCode;       // 仅所领导，允许传入下钻部门
    default -> null;
};

// 仅所领导在 ALL 下可指定任意 dept；非所领导到此 scope != ALL 已保证
```

> **硬约束**：`scope=DEPT` 的 `effectiveDept` 一律重置为用户自身 `userDeptCode`，**不**采用请求里的 `deptCode`——防止用户 A 构造 `deptCode=B` 观测部门 B 的数据。

### 审计

审计事件中记录降级行为：

```java
auditService.audit("READ", "workbench.leader-overview",
    "user=" + userLogin + ",requested=" + requestedScope +
    ",effective=" + scope + ",dept=" + effectiveDept);
```

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/workbench/WorkbenchRoleResolver.java`（新增）
- `WorkbenchLeaderOverviewService.java`（替换 T01 占位实现）
- `WorkbenchResource.java`（传入 roles / deptCode 已在 T01 做好）

## 验证

- [ ] 单测 `WorkbenchRoleResolverTest` 覆盖 INST_LEADER / DEPT_LEADER / EMP 三种识别路径 + 空 roles。
- [ ] 单测 `WorkbenchLeaderOverviewServiceTest#downgrade_*`：
  - `downgrade_emp_requesting_ALL_to_MINE()`
  - `downgrade_dept_leader_requesting_ALL_to_DEPT()`
  - `downgrade_emp_requesting_DEPT_to_MINE()`
  - `inst_leader_requesting_ALL_with_deptCode_keeps_ALL_with_dept()`
- [ ] IT：用不同 role 调用 `/leader-overview?scope=ALL` → 响应的 `scope` 字段正确。
- [ ] 审计表里能看到降级事件（payload 含 `requested=` / `effective=`）。

## 完成标准

- [ ] 越权降级不抛异常，返回降级后的数据；日志记录原请求与降级后值。
- [ ] `scope=DEPT` 时 `effectiveDept = userDeptCode`，请求里的 `deptCode` 被忽略。
- [ ] `scope=ALL` 仅所领导可指定任意 `deptCode`；其他角色到此阶段已被降级。
