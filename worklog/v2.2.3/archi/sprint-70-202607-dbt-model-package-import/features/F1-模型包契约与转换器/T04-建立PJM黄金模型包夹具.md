# T04：建立 PJM 黄金模型包夹具

**优先级**: P0  
**状态**: DONE  
**依赖**: T02、T03

## 目标

用现有 PJM 预算链形成可重复生成、可审查的黄金模型包。

## 技术设计

- 来源固定为 `worklog/v2.2.3/s10/v4/pjm/dbt_model`。
- 覆盖预算 STG、FACT、SUMMARY、KPI APPLICATION、派生 APPLICATION 和节点类型 DIMENSION。
- 为不可从 SQL 推断的业务语义提供明确 `meta.dts`/override 夹具。
- 不复制 ODS 测试数据行、target/logs 或运行产物。

## 影响范围

- Sprint-70 tests/fixtures 或测试资源。
- `worklog/v2.2.3/s10/v4/models` 操作说明。

## 验证

- [ ] 两次生成 byte-equivalent 或 checksum-equivalent。
- [ ] 预期分类与架构文档一致。
- [ ] 故意删除粒度/来源时对应候选变为 BLOCKED。

## 完成标准

- [ ] 黄金包可供后端、前端和真实 E2E 共用。
- [ ] 夹具来源与生产包边界有文档说明。
