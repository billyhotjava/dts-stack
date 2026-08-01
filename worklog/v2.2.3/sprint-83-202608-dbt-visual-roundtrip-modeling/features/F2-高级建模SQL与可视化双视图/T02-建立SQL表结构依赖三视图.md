# T02：建立 SQL、表结构与依赖三视图

**优先级**：P0  
**状态**：DRAFT  
**依赖**：T01、F1/T03～T04

## 目标

在同一 revision 下提供 SQL/dbt、逻辑/技术表结构和 ref/source 依赖图，明确 capability、provenance 与编辑边界。

## Contract-first

- **SQL 视图**：原始 SQL/Jinja 可查看；compiled SQL 只读且带 artifact checksum。
- **表结构视图**：字段名、类型、nullable、key/role、standard、declared/compiled provenance 和 drift。
- **依赖视图**：model/source/technical node、ref/source edge、阻断原因；不猜动态 edge。
- **错误路径**：source-only 缺字段显示“待补充”，动态依赖显示 BLOCKED；不得生成虚假列。
- **可访问性**：键盘切换、aria tab、Chrome95。

## 验证

- [ ] FULL_EDITABLE、STRUCTURE_VIEW_ONLY、BLOCKED 三种 UI fixture。
- [ ] 三视图头部 revision/checksum 完全一致。

## Definition of Done

- [ ] 用户可看清“设计的表”“dbt 实现”和“依赖”，且知道哪些可编辑。
