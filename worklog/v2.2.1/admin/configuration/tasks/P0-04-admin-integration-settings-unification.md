# P0-04 Admin 集成配置控制台统一入口

`status`: `done`  
`priority`: `P0`

## 目标

把 Airflow/OpenMetadata/Addax 等接入配置统一到 Admin 一处管理与测试。

## 范围

- `AdminInfraResource` 的设置查看/更新/测试页面
- Ingestion `infra/settings` 透传模型对齐
- 失败信息标准化

## 子任务

1. 为 `airflow/openmetadata/addax/platform` 补统一 UI 配置卡片。
2. 统一测试按钮输出结构（成功/失败、HTTP 状态、建议动作）。
3. 接口错误码映射成可读文案。
4. 增加操作审计字段（service、operator、before/after 摘要）。

## 验收标准

- 4 类集成配置均可在 Admin 中查看、编辑、测试。
- 测试失败可直接定位到字段级别。

## 风险与回滚

- 风险：Admin 与 Ingestion 配置项定义不一致。
- 回滚：以 Ingestion 定义为准，Admin 做只读降级。

## 实现进展（2026-02-22）

- 页面增强：`source/dts-admin-webapp/src/admin/views/infra-settings.tsx`
  - 增加统一重启提示
  - 增加测试结果结构化展示（`message/status/body`）
- 保持服务定义单一来源：Ingestion `infra/settings` 继续作为字段白名单基准。
