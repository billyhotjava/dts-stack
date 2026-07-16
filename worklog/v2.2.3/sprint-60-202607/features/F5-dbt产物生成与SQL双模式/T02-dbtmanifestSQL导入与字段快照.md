# T02: dbt manifest/SQL 导入与字段快照

**优先级**: P0
**状态**: READY
**依赖**: F3-T02

## 目标

让高级开发直接使用 dbt SQL，并让 DTS 获得可查询的模型、字段、来源和测试信息。

## 技术设计

- 优先读取 `manifest.json`，必要时读取 SQL 文件保存内容 checksum。
- 按 `unique_id` 登记模型，按 column meta 读取业务字段和标准信息。
- 未声明业务对象的 SQL 模型允许导入，但标记 `UNREGISTERED`，不能进入受控发布。

## 影响范围

- manifest parser、dbt project adapter、artifact snapshot service。
- PJM 旧模型导入 fixture。

## 验证

- [ ] 导入 PJM DWS/ADS fixture 结果稳定。
- [ ] 缺 manifest、节点重复和 SQL 不可读均有明确错误。
- [ ] 重复导入不产生重复登记。

## 完成标准

- [ ] 高级开发不需要先在低代码页面重建模型。
