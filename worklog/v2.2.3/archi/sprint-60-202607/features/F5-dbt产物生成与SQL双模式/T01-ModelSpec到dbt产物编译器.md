# T01: ModelSpec 到 dbt 产物编译器

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: F4-T03

## 目标

把 ModelSpec 编译为可提交到 dbt 项目的 SQL、schema.yml、tests 和 docs。

## 技术设计

- 模板按 ODS/STG/DWD/DWS/ADS 和 FACT/DIMENSION/SUMMARY/APPLICATION 分支。
- 所有来源引用使用 dbt `ref()` 或 `source()`，禁止生成不可追溯裸表名。
- schema.yml 写入字段说明、标准编码、语义类型和质量测试。
- 生成目录使用模型名和 revision，避免覆盖其他项目文件。

## 影响范围

- `source/dts-platform` compiler/template resources。
- dbt fixture 输出目录和 compile tests。

## 验证

- [x] PJM DWD/DWS fixture 生成 SQL、schema、tests、docs 四类文件。
- [x] 同一 revision 输出目录和文件名稳定。
- [x] 非法来源层级和无 grain 模型被阻断。
- [ ] 使用真实 dbt parse 验证生成物。

## 完成标准

- [x] 至少生成 SQL、schema.yml、tests、docs 四类产物。
