# T01: 统一 Artifact 与 Policy 模型

**优先级**: P0
**状态**: READY

## 目标

把当前“只有字符串结果”的解析方式升级为结构化模型，至少区分 raw/display/normalized 三层值，并把 header/dataStart/fillMerged/dateFormat 等行为显式收口到 Policy。

## 影响范围

- `source/dts-platform/.../service/infra/excel/model/*`
- `ExcelImportService` 的中间结果结构
- 后续 `ExcelSchemaInferer` / `ExcelArtifactWriter` 的输入

## 完成标准

- [ ] 存在 `ExcelCellSnapshot` / `ExcelRowSnapshot` / `ExcelParsePolicy` / `ExcelParseArtifact`
- [ ] 模型字段能覆盖负数、日期、公式、合并单元格等场景
- [ ] 不直接暴露给前端，仅作为内部契约
