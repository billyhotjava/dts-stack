# T02: ELT 分层入口 PRD

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

把“源数据库清洗到数据仓库后，dts-metrics 从哪一层开始做可视化设计”写成产品和工程都能执行的 PRD。

## 技术设计

- 定义数据层职责：源库、ODS、STG、DWD、DWS、ADS、BI Dataset。
- 固化入口：DWS 默认建模；ADS 复用/消费；DWD 高级生成 DWS；ODS/STG 只展示 lineage。
- 明确资产必须来自 platform Catalog，且包含 `warehouseLayer`、schema contract、lineage、governance gap、permission、RLS/masking。
- 明确 DWD 进入高级建模时必须检查主键、粒度、标准码和字段级血缘。

## 影响范围

- `worklog/v2.2.3/sprint-35-202605/assets/dts-metrics-elt-layer-prd.md`
- `worklog/v2.2.3/sprint-35-202605/README.md`
- 后续 `source/dts-metrics` 和 `source/dts-metrics-webapp` API/UX 实施

## 验证

- [ ] 文档中明确回答“从 DWS 还是 DWD 开始”：默认 DWS/ADS，DWD 只作为高级上游。
- [ ] 文档中明确 ODS/STG 不进入普通指标画布。
- [ ] 文档中区分用户承诺、实现事实源和非目标。

## 完成标准

- [ ] PRD 可作为 F2-F5 的输入，不需要再解释数据层边界。
