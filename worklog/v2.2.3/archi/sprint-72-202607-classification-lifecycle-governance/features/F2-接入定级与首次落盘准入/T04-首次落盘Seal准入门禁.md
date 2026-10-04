# T04: 首次落盘 Seal 准入门禁

**优先级**: P0
**状态**: IN_PROGRESS
**编码状态**: DONE（统一验证延后）
**依赖**: T01,T02,T03

## 目标

在任何 ODS/landing 生产写入前验证密级已封存，防止数据先落盘后补治理。

## 技术设计

- 在 ODS plan/apply 和 ingestion execute 前验证 seal。
- seal 绑定来源资产、字段集合、任务版本和 checksum。
- 预检区与生产区明确分离；缺密级只允许受控样本预览。
- 保存准入批准、拒绝和执行证据。

## 影响范围

`OdsGenerationService`、`OdsTableMappingSyncService`、Addax/Airflow/ingestion 任务入口、治理 gate。

## 验证

- [ ] 无 seal、过期 seal、字段漂移和合法 seal 场景。
- [ ] 落盘与事实写入保持事务/补偿一致。

## 完成标准

- [ ] 无密级数据不能进入生产 ODS。
- [ ] schema drift 会使旧 seal 失效并要求重新确认。
