# T04: DWD/DWS/ADS 准入规则

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

定义 DWD、DWS、ADS 进入 `dts-metrics` 的具体准入条件，防止后续实现把所有表都当成普通可视化资产。

## 技术设计

- DWS 准入：`warehouseLayer=DWS`、schema contract 存在、治理缺口非阻断、lineage ready、用户有 read permission、RLS/masking 可解析。
- ADS 准入：`warehouseLayer=ADS`、可追溯到 DWS/DWD、消费语义明确、用户有 read permission。
- DWD 准入：只在 `mode=advanced` 下返回；必须有 primary keys、grain、标准码字段、字段级 lineage、无阻断治理缺口。
- ODS/STG 准入：不返回到普通 visual assets，只在 lineage/impact endpoint 中出现。

## 影响范围

- `GET /api/metrics/visual-assets`
- `GET /api/internal/metrics/visual-assets`
- `source/dts-metrics-webapp` 资产选择器
- `source/dts-metrics` graph preflight

## 验证

- [ ] 普通查询默认不返回 DWD/ODS/STG。
- [ ] DWD 查询必须携带高级模式参数。
- [ ] ODS/STG 进入画布时返回 `invalid_layer`。

## 完成标准

- [ ] 分层准入规则写入 API、前端、后端和 IT 测试任务。
