# T03: dbt/ModelSpec 发布密级门禁

**优先级**: P0
**状态**: IN_PROGRESS

**编码状态**: DONE（统一验证延后）
**依赖**: T01

## 目标

让 dbt artifact、ModelSpec revision 和 ReleaseCandidate 绑定密级快照，发布前重新校验最高密级。

## 技术设计

- dbt manifest/column lineage 导入后计算字段与模型有效密级。
- revision/candidate 保存 snapshot version/checksum。
- 发布前检测 stale、missing、candidate-lower-than-upstream。
- 复用 Sprint-67/69 发布控制面，不新建第二套发布状态机。

## 影响范围

dbt asset sync、ModelSpec lifecycle、ReleaseCandidate/gate、构建发布工作台。

## 验证

- [ ] manifest 有/无 column lineage、上游升密、stale checksum。
- [ ] 草稿可修复，正式发布 fail closed。

## 完成标准

- [ ] 发布证据包含可追溯密级快照。
- [ ] 旧已发布版本访问仍按当前最高密级裁决。
