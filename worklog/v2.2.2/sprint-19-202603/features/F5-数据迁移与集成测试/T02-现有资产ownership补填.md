# T02: 现有资产 ownership 补填

**优先级**: P0
**状态**: READY
**依赖**: F1/T01

## 目标

为现有的数据源、表、卡片、仪表盘、大屏、模型生成 asset_ownership 记录。

## 技术设计

### 补填策略

1. **数据源**: 需管理员手动分配 `owner_dept_id`（无法自动推断）
   - 提供管理界面批量设置
   - 未设置的数据源标记为 "待分配"

2. **表**: 继承数据源的 `owner_dept_id`
   ```sql
   INSERT INTO asset_ownership (asset_type, asset_id, owner_dept_id, source_id, assigned_by)
   SELECT 'TABLE', t.id, ds.owner_dept_id, ds.id, 'MIGRATION'
   FROM catalog_table t JOIN infra_data_source ds ON t.source_id = ds.id
   WHERE ds.owner_dept_id IS NOT NULL
   ON CONFLICT DO NOTHING;
   ```

3. **卡片/仪表盘/大屏/模型**: 默认归属创建者所在部门
   - 需关联用户 → 部门映射
   - 无法确定的标记为 "待分配"

### 待分配资产处理

资产归属管理页面增加 "待分配" 筛选条件，院级管理员可批量处理。

## 影响范围

- `source/dts-platform/src/main/resources/config/liquibase/changelog/` — 迁移脚本
- 资产归属管理页面 — 增加 "待分配" 筛选

## 验证

- [ ] 有数据源部门的表自动补填
- [ ] 无法推断的资产标记为待分配
- [ ] 补填脚本幂等

## 完成标准

- [ ] 所有可推断的资产有 ownership 记录
- [ ] 待分配资产清晰可见
