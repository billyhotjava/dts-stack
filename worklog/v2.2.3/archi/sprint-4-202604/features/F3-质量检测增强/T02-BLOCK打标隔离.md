# T02: BLOCK 打标隔离实现

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
当 action_on_fail=BLOCK 的规则检测失败时，在 ODS 表的对应行上打标 `_quality_blocked=true`，DWD 层 dbt 模型过滤这些行。

## 技术设计

### ODS 表扩展
入湖时自动为 ODS 表添加两个质量标记列（如果不存在）：
```sql
ALTER TABLE {{table}} ADD COLUMN IF NOT EXISTS _quality_blocked BOOLEAN DEFAULT false;
ALTER TABLE {{table}} ADD COLUMN IF NOT EXISTS _quality_checked_at TIMESTAMP;
```

### 打标逻辑
在 `QualityRunService.doExecuteRun()` 中，BLOCK 规则失败后：
```sql
UPDATE {{table}} SET _quality_blocked = true
WHERE id IN (SELECT row_id FROM gov_quality_failing_row WHERE run_id = {{runId}});

UPDATE {{table}} SET _quality_checked_at = now();
```

### 解除打标
用户修正数据后重新执行检测，通过的行自动解除：
```sql
UPDATE {{table}} SET _quality_blocked = false
WHERE _quality_blocked = true
  AND id NOT IN (SELECT row_id FROM gov_quality_failing_row WHERE run_id = {{latestRunId}});
```

### DWD 层过滤
dbt 模型中增加标准过滤条件：
```sql
WHERE _quality_blocked IS NOT TRUE
```

## 影响范围
| 文件 | 改动 |
|------|------|
| `QualityRunService.java` | 打标+解除逻辑 |
| ODS 建表流程 | 自动添加标记列 |
| dbt 模型模板 | WHERE 过滤 |

## 验证
- [ ] BLOCK 规则失败后对应行 _quality_blocked = true
- [ ] ALERT 规则失败后不打标
- [ ] 修正数据后重新检测，通过的行解除打标
- [ ] DWD 模型不包含被打标的行

## 完成标准
- [ ] 打标和解除逻辑实现
- [ ] DWD 过滤机制说明
