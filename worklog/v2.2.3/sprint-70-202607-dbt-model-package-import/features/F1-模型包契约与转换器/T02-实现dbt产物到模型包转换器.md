# T02：实现 dbt 产物到模型包转换器

**优先级**: P0  
**状态**: DONE  
**依赖**: T01

## 目标

复用现有 manifest 解析能力，从 dbt 产物生成稳定模型包。

## 技术设计

- 输入 `manifest.json`，可选 `catalog.json`、schema YAML 和 `meta.dts`。
- 提取 project/version、uniqueId、resource path、SQL checksum、columns、types、tests、tags、materialization 和 dependencies。
- manifest 与 catalog/schema 不一致时出具 issue，不静默覆盖。
- 提供 repo-native CLI 或构建脚本，输出单一 JSON 文件并支持 `--validate-only`。

## 影响范围

- `ModelingDbtManifestImporter` 的可复用纯解析逻辑。
- `bin/` 或既有 dbt 工具目录。
- PJM 模型包生成说明。

## 验证

- [ ] 缺 catalog 时仍能生成包，但字段类型缺口明确标记。
- [ ] SQL、columns、config 或 dependency 变化会改变 checksum。
- [ ] raw/compiled SQL 读取优先级与当前 importer 一致。

## 完成标准

- [ ] 转换器不写数据库、不修改 dbt 项目。
- [ ] 输出通过 T01 Schema 校验。
