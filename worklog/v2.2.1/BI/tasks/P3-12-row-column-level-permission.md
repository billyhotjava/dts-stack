# P3-12 行列级数据权限

`status`: `done` (frontend)
`priority`: `P3`
`sprint`: `Sprint 3 - 企业能力`
`inspiration`: `DataEase(行级安全 RLS + 列级脱敏) + 企业合规/数据分级要求`

## 目标

在大屏数据查询链路中实现行级安全过滤（RLS）和列级脱敏，确保不同角色看到的数据范围与精度不同。

## 当前状态

- P0-04 已实现大屏级 ACL（read/edit/publish/manage）。
- 数据查询经过 `useCardDataSource` → 后端 Card/SQL/API 执行。
- 缺少：查询时按用户角色注入过滤条件、敏感列脱敏。

## 子任务

### 1. 行级安全策略定义

**后端新增**: `RowLevelSecurityPolicy`

```java
public record RowLevelSecurityPolicy(
    Long id,
    Long datasetId,        // 关联数据集
    String filterExpression, // SQL WHERE 片段，如 "region = :user_region"
    String rolePattern,     // 匹配角色，如 "ROLE_REGION_*"
    boolean enabled
) {}
```

**管理 API**:
```
GET    /api/analytics/rls-policies?datasetId=xx
POST   /api/analytics/rls-policies
PUT    /api/analytics/rls-policies/{id}
DELETE /api/analytics/rls-policies/{id}
```

### 2. 查询链路注入

**后端改动**: Card 查询/SQL 查询执行前

- 从当前用户的角色中匹配 RLS 策略。
- 将 `filterExpression` 注入查询的 WHERE 子句。
- 支持变量替换：`:user_id`、`:user_name`、`:user_region`、`:user_department`。
- 多条策略用 AND 组合。

### 3. 列级脱敏策略

**后端新增**: `ColumnMaskingPolicy`

```java
public record ColumnMaskingPolicy(
    Long id,
    Long datasetId,
    String columnName,
    String maskType,        // 'full' | 'partial' | 'hash' | 'null'
    String maskPattern,     // partial 模式下的模式，如 "***{last4}"
    String rolePattern      // 对哪些角色生效
) {}
```

**脱敏类型**:
| 类型 | 效果 | 场景 |
|------|------|------|
| `full` | `***` | 完全隐藏 |
| `partial` | `张**` / `138****1234` | 部分可见 |
| `hash` | `a1b2c3...` | 不可逆脱敏 |
| `null` | `null` | 不返回 |

### 4. 前端权限标识

**文件**: `ComponentRenderer.tsx` / 相关渲染逻辑

- 查询结果中包含脱敏标记（列级 `_masked: true`）。
- 脱敏列在表格中显示特殊样式（灰色斜体 + tooltip "数据已脱敏"）。
- 图表中脱敏数值不参与数学计算（避免误导）。

### 5. 管理界面

**后端管理 + 前端配置**（可在平台管理端或大屏设置中配置）:
- RLS 策略列表：按数据集分组，增删改启停。
- 脱敏策略列表：按数据集 + 列分组。
- 预览效果："以角色 X 预览" 切换视角。

## Chrome 95 兼容性

- 后端逻辑为主，前端仅展示脱敏标记，无 API 依赖 ✅。

## 验收标准

- 不同角色用户看到同一大屏时数据范围不同。
- 脱敏列正确展示 `***` 等掩码。
- RLS 策略变更后查询结果立即生效。
- 管理界面可配置策略。

## 风险与回滚

- 风险：RLS 注入 SQL 引入安全漏洞（SQL 注入）。
- 回滚：filterExpression 使用参数化查询（`PreparedStatement`），禁止直接拼接 SQL。
