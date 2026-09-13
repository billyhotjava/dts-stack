# T25: Log 面板（含重写后 SQL 展示）

**优先级**: P2
**状态**: READY
**依赖**: F4

## 目标

底部 `Log` Tab 展示查询执行的完整元信息、错误堆栈、被 `SecuritySqlRewriter` 重写后的真实 SQL（高级模式），方便开发者调试与问题排查。

## 技术设计

### 展示内容

```
[2026-04-12 10:32:15] SUBMIT by billy (dept=R&D, level=INTERNAL)
[2026-04-12 10:32:15] Engine: Trino  Datasource: production
[2026-04-12 10:32:15] Queue wait: 12ms
[2026-04-12 10:32:15] Executing SQL:
----------------------------------------------------
SELECT u.name, COUNT(o.id)
FROM users u JOIN orders o ON u.id = o.user_id
GROUP BY u.name
----------------------------------------------------
[2026-04-12 10:32:15] ⚠ Rewritten SQL (by SecuritySqlRewriter):
----------------------------------------------------
SELECT * FROM (
  SELECT u.name, COUNT(o.id) FROM users u JOIN orders o ON u.id = o.user_id GROUP BY u.name
) AS __sec WHERE __sec.data_level <= 'INTERNAL' AND __sec.dept_code = 'RD'
----------------------------------------------------
[2026-04-12 10:32:15] Scan rows: 1,234,567  Returned rows: 1,234  Elapsed: 420ms
[2026-04-12 10:32:15] SUCCESS
```

### 字段来源

- `query_execution` 表（已有）
- 新增字段 `rewritten_sql`（若尚未保存；与 T20 审计字段合并）

### 简洁/高级模式差异

| 字段 | 简洁 | 高级 |
|---|---|---|
| 原始 SQL | ✓ | ✓ |
| 重写后 SQL | ✗ | ✓ |
| 排队/扫描字节/内部计划摘要 | ✗ | ✓ |
| 错误堆栈 | 简要 | 完整 |

### UI

- 类 VS Code Output Panel：固定字体、时间戳着色、可复制
- 错误行红色高亮
- 顶部 Filter: `Info | Warning | Error` 按级过滤
- 右上"Copy All"按钮

## 影响范围

- 新增 `result/LogPanel.tsx`
- `query_execution` 可能需新增 `rewritten_sql` 列（若与 T20 同步即可）

## 验证

- [ ] 执行成功/失败/取消三种状态展示正确
- [ ] 高级模式显示重写后 SQL，简洁模式不显示
- [ ] Copy All 复制有效

## 完成标准

- [ ] Log 面板可用
- [ ] 字段完整
