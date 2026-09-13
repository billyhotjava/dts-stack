# T03：纳入计量单位第六 CSV 与 SI 单位包

**优先级**：P0  
**状态**：READY  
**依赖**：T01、F1-T03

## 目标

把计量单位纳入标准包统一安装、升级和回滚，并交付 SI 与常用业务单位基础包。

## 技术设计

- 新增 `06-measurement-units.csv`：code/name/symbol/quantity_kind/conversion_factor/base_unit_code/precision/status。
- preview 先按 code 建索引，再解析 `base_unit_code`，入库时映射到 `baseUnitRef` UUID。
- 阻断缺基准单位、跨量纲、换算因子非法、循环引用和重复 symbol/code。
- apply 顺序在数据元之前；数据元可用稳定 unit code 引用单位。
- 回滚复用版本/checksum/引用影响保护，不删除被后续对象引用的单位。

## 影响范围

- StandardPackageImport/Apply/Builtin
- MeasurementUnit service/repository/resource
- 模板 ZIP、前端报告和 source-contract
- SI 单位内容包

## 验证

- [ ] 有效单位包 preview/apply/reinstall/rollback。
- [ ] 循环、跨量纲、缺基准和非法精度负例。
- [ ] 单位版本和数据元引用在升级后可追溯。

## 完成标准

- [ ] 计量单位不再需要逐条手工维护。
- [ ] 包 CSV 使用稳定 code，不泄露数据库 UUID。
