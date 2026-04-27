# Sprint-16 Review: Sprint-18 ODS/stg 约束回灌

**日期**: 2026-04-27
**结论**: Sprint-16 的 API 接入方向可继续，但必须把原设计中的 ODS 字段映射改为 schema snapshot + stg 映射。

## 主要发现

| 严重级别 | 位置 | 问题 | 调整 |
|---|---|---|---|
| High | F3 / F5 | 原设计允许 preview 后编辑 ODS 列名、类型、nullable、敏感标记 | 改为只生成 schema snapshot；字段编辑进入 stg mapping |
| High | 前端 API 分支 | `apiFieldsJson` 会写入 `resource.fields`，容易被理解为 ODS 字段映射 | 已移除表单入口和提交逻辑 |
| Medium | API contract | `ApiFieldMapping` 把 target column 绑定在 API resource 上，边界偏 ODS | 已替换为 `ApiLandingPolicy`、`SchemaSnapshotPolicy`、`ApiStagingFieldMapping` |
| Medium | 运行时选型 | 文档仍把 Airbyte 作为候选运行时 | 已改为 Addax HTTP reader / 自研 runner，不引入 Airbyte |

## 调整后的边界

- ODS：每个 API resource 稳定落一张 `ods_api_<source>_<resource>` 表。
- ODS 列：`_dts_raw_record` + `_dts_*` 技术字段。
- Schema snapshot：保存 JSON path、字段顺序、推断类型、nullable、样本、drift 策略。
- stg：字段重命名、类型标准化、敏感标记、主键、质量测试和 freshness 从这里开始。
- 下游：DWD/DWS/ADS 只依赖 stg，不直接依赖 API ODS 业务字段。

## 后续仍需完成

- API preview/inference 服务需要真正落 schema snapshot。
- API ODS 建表器需要按 raw record + `_dts_*` 固定契约实现。
- stg 自动生成需要读取 API schema snapshot，而不是读取 `resource.fields`。
- E2E 需要断言 ODS/stg 边界，防止后续重新把业务字段写入 ODS。
