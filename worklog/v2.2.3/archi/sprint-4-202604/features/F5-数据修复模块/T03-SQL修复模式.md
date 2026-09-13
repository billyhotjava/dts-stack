# T03: SQL 修复模式

**优先级**: P1
**状态**: READY
**依赖**: F3

## 目标
为数据专员提供 SQL 编辑器，支持编写自定义 UPDATE 语句修复复杂数据问题。

## 技术设计

### 交互
```
SQL 编辑器（Monaco Editor，复用现有 dbt-sql 语言）
  → [预览效果] 按钮 → 显示影响行数 + 前10行修复前后对比
  → [执行] 按钮 → 确认对话框 → 执行
```

### 安全约束
- **白名单校验**：仅允许 `UPDATE ods_*` 语句
- **禁止语句**：DELETE、DROP、TRUNCATE、ALTER、CREATE、INSERT
- 正则检查：`^\s*UPDATE\s+ods_\w+\s+SET\s+`
- 预览使用 `EXPLAIN` + `SELECT ... LIMIT 10`（不实际修改）

### API
```
POST /api/governance/quality/sql-repair/preview
{ sql, limit: 10 }
→ { affectedRows, samples: [{ before, after }] }

POST /api/governance/quality/sql-repair/execute
{ sql, runId }
→ { affectedRows, auditLogId }
```

### 审计
- 完整 SQL 语句记录
- 影响行数
- 执行人 + 时间

## 影响范围
- 新增 `SqlRepairService.java`：SQL 校验 + 预览 + 执行
- 新增前端 `SqlRepairEditor.tsx` 组件（复用 Monaco Editor）
- `GovernanceResource`：新增 2 个端点

## 验证
- [ ] 合法 UPDATE ods_* 语句可执行
- [ ] DELETE/DROP 等被拒绝
- [ ] 预览显示修复前后对比
- [ ] 审计日志完整

## 完成标准
- [ ] SQL 修复全流程可用
- [ ] 安全校验不可绕过
