# T03: schema.yml 标准 meta 与 dbt tests 增强

**状态**: READY
**优先级**: P0

## 目标

让 `schema.yml` 成为标准进入 dbt 和下游语义层的机器可读契约，而不是导出的说明文件。

## 必须包含

- `meta.dts.standardCode`
- `meta.dts.standardVersion`
- `meta.dts.codeSet`
- `meta.dts.securityLevel`
- `meta.dts.bindingSource`
- `meta.dts.status`
- `not_null` tests
- `relationships` tests to seed

## 建议增强

- 补充 `meta.dts.semanticType`：dimension / metric / time。
- 补充 `meta.dts.businessTermCode`：用于业务对象和指标口径追踪。
- 补充 `meta.dts.ownerDept`：用于治理责任链。

## 验收

- [ ] 生成的 schema.yml 可被 dbt parse。
- [ ] 没有标准绑定时不得生成假成功内容。
- [ ] 写入路径仍受 dbt 项目根目录约束。
