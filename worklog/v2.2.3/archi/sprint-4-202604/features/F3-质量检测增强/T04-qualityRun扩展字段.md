# T04: gov_quality_run 扩展字段

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
扩展 gov_quality_run 表，记录失败行总数和是否执行了清洗。

## 技术设计

```sql
ALTER TABLE gov_quality_run ADD COLUMN failing_row_count INTEGER DEFAULT 0;
ALTER TABLE gov_quality_run ADD COLUMN cleansing_applied BOOLEAN DEFAULT false;
```

## 验证
- [ ] 迁移成功
- [ ] 检测执行后 failing_row_count 正确
- [ ] 带清洗的执行 cleansing_applied = true

## 完成标准
- [ ] 字段添加
- [ ] 写入逻辑集成
