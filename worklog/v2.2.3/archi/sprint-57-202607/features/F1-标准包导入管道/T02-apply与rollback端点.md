# T02: apply/rollback/runs 端点

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

基于 preview 的 runId 实现事务性应用、导入历史查询与整包回滚。

## 技术设计

**端点**（StandardPackageResource 追加）：
- `POST /import/apply` body `{ runId }` — 读取 run.payload_json，单事务按依赖序入库：术语 → 码表目录 → 码表值 → 数据元 → 映射；每条记录写入前记录 before-image 到 `standard_package_import_run_item`（entity_type, entity_id, action(CREATE/UPDATE), before_json）；成功置 run=APPLIED
- `POST /runs/{runId}/rollback` — 逆序遍历 run items：CREATE→删除，UPDATE→回写 before_json；置 run=ROLLED_BACK；若实体已被后续修改（updated_date 晚于 apply 时间）则该条标记 SKIPPED 并在响应中列出
- `GET /runs` / `GET /runs/{id}` — 分页历史 + 明细

**入库映射**：
- 术语 → `ModelingGlossaryTermRepository`（对齐 ModelingAuxResource 单条创建的字段语义与默认值）
- 码表 → 复用 `ReferenceCodeService.applyStructuredImport` 的写入路径（目录级），包级循环调用并纳入同一事务
- 数据元 → `MetadataStandardService` upsert

**审计**：AuditService 记录 apply/rollback（对齐 GovernanceReferenceCodeResource 的 AuditStage 用法）。

## 影响范围

新 liquibase changelog `20260702_02_standard_package_import_run_item.xml`；不改动既有写入服务签名（只新增重载/入口）。

## 验证

- [ ] 单测：apply 中途某行失败 → 全事务回滚，run=FAILED
- [ ] 单测：apply 后 rollback → 五类实体计数还原；被篡改实体 SKIPPED
- [ ] curl 全流程：preview → apply → runs → rollback

## 完成标准

- [ ] 依赖序正确（先术语/目录后数据元/映射），映射的 code_set 关联在同事务内可见
- [ ] 回滚幂等（重复调用返回已回滚状态，不报错）
