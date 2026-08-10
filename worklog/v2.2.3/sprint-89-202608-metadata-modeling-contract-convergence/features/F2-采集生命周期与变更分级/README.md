# F2: 采集生命周期与变更分级

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: F1/T01 身份契约
**价值**: 让采集更新保持稳定身份，并把“结构变了”转成建模可判断的兼容/破坏性证据。

## 生命周期状态机

```text
UNKNOWN --成功完整采集--> SYNCED
SYNCED  --完整快照缺失--> STALE（保留 dataset/table/column ID）
STALE   --来源重现------> SYNCED（复用原 ID，生成 drift）
任意状态--采集失败------> 保留最后成功快照，run=FAILED
```

字段按 `(table_id, normalized column name)` upsert：存在则原地更新；本次缺失则 `status=REMOVED`；重现则恢复 `ACTIVE`。采集不得覆盖 tags、sensitiveTags、standard、classification、owner 等业务治理事实。

## drift 两阶段判定

1. 结构层：根据 add/remove/type/nullability 产生 `COMPATIBLE/BREAKING/REVIEW_REQUIRED` 基础级别。
2. 消费层：结合 WarehousePlan/ModelSpec 实际引用字段，把“删除未引用字段”降为兼容，把“删除已引用字段”确认为破坏性。

结果写入既有 `catalog_schema_drift_event.details_json`，不新增平行事件表；ticket 的 open/handled 仍由现有字段承载。

## Task

| Task | 状态 |
|---|---|
| T01-统一采集软失效与稳定ID | DONE |
| T02-建立兼容与破坏性变更判定 | DONE |

## DoD

- [x] PostgreSQL/JDBC/Inceptor 例行同步均无 dataset/table/column 物理删除
- [x] 列更新不再 delete-and-recreate，稳定 ID 与业务治理字段得到保留
- [x] STALE/UNKNOWN 被来源 resolver 正确映射，不再返回 AVAILABLE
- [x] drift 分类矩阵与消费者覆盖有自动化测试
- [ ] 同步失败、并发与重复执行满足 `assets/nfr-budget.md`
