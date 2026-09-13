# T01：按拓扑应用 ModelSpec 与 revision

**优先级**: P0  
**状态**: IN_PROGRESS  
**依赖**: F2

## 目标

按预检拓扑创建或修订 canonical ModelSpec，并把包内依赖转换为精确 revision pin。

## 技术设计

- apply 前重新计算 previewHash 和上下文版本。
- 复用 `ModelSpecApplicationService` 的创建、更新、领域校验和 idempotency replay。
- CREATE 生成新 ModelSpec；UPDATE 生成新 revision；SKIP 不写入。
- 下游只引用本批已成功或既有 CURRENT 上游的精确 revision/checksum。

## 影响范围

- canonical import apply service。
- ModelSpec repository/application service 适配层。

## 验证

- [ ] FACT→SUMMARY→APPLICATION 按序创建。
- [ ] 任一上游失败时相关下游不写入。
- [ ] 已发布 revision 不被覆盖。

## 完成标准

- [ ] 模型中心只出现合法四类模型。
- [ ] plan/domain/type/layer/grain/fields 与预检一致。
