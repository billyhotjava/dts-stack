# T04: SecurityInjector — 密级/行级 WHERE 注入

**优先级**: P0
**状态**: READY
**依赖**: T03

## 目标

在 SQL 编译期把**密级过滤**和**行级安全 predicate** 注入到 WHERE 子句，保证任何查询都强制应用当前用户的安全上下文。**这是把合规要求从运行时后处理前移到编译时的关键一步**——查询出来就已经是合法的数据。

## 技术设计

### 1. 为什么在编译期注入

| 方式 | 问题 |
|---|---|
| DB 侧 Row-Level Security | 切方言麻烦，国产库支持参差，debug 困难 |
| 结果后处理 | 敏感数据已落 JVM，审计视角等于泄露；大结果集浪费带宽 |
| **编译期注入（本方案）** | SQL 就是合法的，DB 只读到该读的 |

### 2. 注入时机

在 `SingleModelCompiler` / `MultiModelCompiler` 生成 WHERE 后、执行前，调用 `SecurityInjector.inject(compiledQuery, userClaims)`。

### 3. 注入的条件

对每个参与查询的 model：

1. **密级横切过滤**：如果 model 的 `security_level > user.security_level`，**编译失败 422**（不能降密级，直接拒绝）。
2. **密级字段脱敏**：如果 model 内某个 dimension 的 `security_level > user.security_level`，select 时用视图层脱敏列替代（从 `<model>_masked` 视图取；由 dbt 提前建）。
3. **行级 predicate**：把 `meta.dts.row_security_predicate` 模板渲染成 SQL 并 AND 进 WHERE。

### 4. row_security_predicate 模板

模板语法：Jinja2-like，但**白名单变量**：

```
{{ user.dept_id }}
{{ user.dept_ids }}            // 部门列表
{{ user.security_level }}
{{ user.user_id }}
{{ user.branch_id }}
```

例子：
```yaml
# ads_sales_daily.schema.yml
meta:
  dts:
    row_security_predicate: "dept_id IN ({{user.dept_ids | sql_list}})"
```

渲染后：
```sql
... AND (dept_id IN ('D001', 'D002', 'D003'))
```

### 5. 渲染器实现

```java
@Service
public class RowSecurityRenderer {
    public String render(String template, UserClaims user) {
        // 用 Pebble / FreeMarker，严格限定允许的 token
        // 自定义 filter: sql_list (把 List 转成 'a', 'b', 'c' 形式，自动参数化)
    }
}
```

**约束：**
- template 里任何非白名单变量 → 编译错
- template 里含 `;` / `DROP` / `SELECT` 等关键字 → 报错
- 渲染结果的字面量全部进参数列表，不直接拼接

### 6. 部门层级展开

`user.dept_ids` 不是直接取用户主部门，而是：

```java
Set<String> deptIds = departmentHierarchyService.descendants(user.dept_id);
```

这样一个总部负责人能看到所有下属部门的数据。层级表 `sys_department` 已存在（本 Task 依赖但不修改）。

### 7. OP_ADMIN 旁路

- OP_ADMIN 角色跳过所有注入**但：**
  - 审计日志记录 "security_bypass"
  - 响应 meta 里 `security_applied` 空数组 + warning
  - UI 在 Card 页头显示"管理员模式：安全上下文未应用"

### 8. `security_applied` 回显

每次查询响应里 meta.security_applied 数组如实汇报：
```json
"security_applied": [
  "classification_filter",
  "row_level_by_dept",
  "column_masking:phone_number"
]
```
前端可据此给用户看到"当前看到的数据受哪些安全策略限制"。

### 9. 缓存 key 必须包含用户密级和部门

`ResultCache` 的 key 包括 `user.security_level`、`user.dept_ids_hash`（而不只是 user_id），防止缓存串用户。见 T05。

### 10. 测试矩阵

| 场景 | 期望 |
|---|---|
| 低密级用户查 CONFIDENTIAL model | 422 |
| 中密级用户查含 CONFIDENTIAL 列 | 该列返回 "***" |
| 单部门用户 | WHERE dept_id = 'D001' |
| 多部门用户 | WHERE dept_id IN ('D001', 'D002', ...) |
| 总部用户（含所有下级） | 层级展开 |
| OP_ADMIN | 无 WHERE 增加，审计日志写入 |
| template 含非法 token | 编译失败 |
| user claims 缺字段 | 400 |

### 11. ClassificationPolicy 类

```java
public enum ClassificationPolicy {
    PUBLIC(0),
    INTERNAL(1),
    SENSITIVE(2),
    CONFIDENTIAL(3);

    public boolean canAccess(ClassificationPolicy resource) {
        return this.level >= resource.level;
    }
}
```

和现有 `DataSecurityLevel` 对齐（不重复建枚举，若有冲突以现有的为准）。

## 影响范围

| 类型 | 文件 |
|---|---|
| 新建 | `service/semantic/security/SecurityInjector.java` |
| 新建 | `service/semantic/security/RowSecurityRenderer.java` |
| 新建 | `service/semantic/security/ClassificationPolicy.java`（如需） |
| 修改 | `SingleModelCompiler.compile()` 调用注入 |
| 修改 | `QueryResponse` 加 `security_applied` 字段 |
| 测试 | `SecurityInjectorTest`、`RowSecurityRendererTest`、端到端 IT |

## 验证

- [ ] 低密级用户查高密级 model 返回 422
- [ ] 脱敏列正确替换为 `_masked` 视图字段
- [ ] 多部门用户 predicate 正确 IN 展开
- [ ] 部门层级用户 predicate 含所有下级
- [ ] OP_ADMIN 审计日志写入
- [ ] SQL 注入测试：dept_id 含注入字符串不影响
- [ ] template linter 正确拒绝非白名单变量
- [ ] `security_applied` 回显准确

## 完成标准

- [ ] 所有查询（即使没有行级规则）也要过 `SecurityInjector.inject()`，确保路径唯一
- [ ] 端到端测试：同一个查询在不同用户下返回不同结果集
- [ ] 单元测试覆盖率 ≥ 85%
- [ ] 审计日志样例存 `it/evidence/f2-audit-samples.md`
