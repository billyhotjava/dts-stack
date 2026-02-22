# P0-03 配置元数据模型扩展

`status`: `done`  
`priority`: `P0`

## 目标

给配置项补齐“可编辑语义”，支持前端正确渲染与操作限制。

## 范围

- `system_config` 元数据字段扩展
- 配置接口返回结构扩展
- 前端配置表单渲染协议

## 子任务

1. 增加元数据：`scope`、`restartRequired`、`validationRule`、`owner`。
2. 补 Liquibase 变更与默认值回填。
3. API 返回新增元数据字段。
4. UI 根据元数据控制编辑能力与提示。

## 验收标准

- 新老配置均可返回完整元数据。
- 前端可基于元数据自动渲染控件和提示。

## 风险与回滚

- 风险：老数据无元数据导致渲染异常。
- 回滚：服务端兜底默认值（`restartRequired=false`，`scope=runtime`）。

## 实现进展（2026-02-22）

- 实体扩展：`source/dts-admin/src/main/java/com/yuzhi/dts/admin/domain/SystemConfig.java`
  - 新增 `configScope` / `restartRequired` / `validationRule` / `owner`
- DTO 扩展：`source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/ops/OpsConfigView.java`
- Liquibase 变更：
  - `source/dts-admin/src/main/resources/config/liquibase/changelog/20260222-03_ops_config_runtime_metadata.xml`
  - `source/dts-admin/src/main/resources/config/liquibase/master.xml`
