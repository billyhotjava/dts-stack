# F4 dbt schema.yml 与标准元数据生成

**状态**: DONE  
**目标**: 标准绑定必须生成真实 dbt `schema.yml` 文件，不只停留在前端展示。

## Tasks

| Task | 内容 | 状态 | 代码/证据 |
|------|------|------|-----------|
| T01 | 后端生成 dbt schema.yml 文本 | DONE | `generateSchemaYml` |
| T02 | schema.yml 写入模型同目录 `schema.yml` | DONE | `ModelFileService.writeSchemaYmlFile` |
| T03 | schema.yml 包含 `meta.dts.standardCode`、`standardVersion`、`codeSet`、`securityLevel` | DONE | `buildSchemaYml` |
| T04 | 前端按钮触发真实生成接口并展示写入路径 | DONE | `platform-sql-modeling-schema-yml-generate` |

## 验收

- 后端目标单测断言生成内容，并 verify `writeSchemaYmlFile(...)`。
