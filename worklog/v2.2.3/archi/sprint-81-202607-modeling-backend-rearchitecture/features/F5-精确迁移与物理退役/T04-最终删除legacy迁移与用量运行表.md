# T04：最终删除 legacy migration/usage runtime ledger

**优先级**：P0
**状态**：PLANNED
**依赖**：T03；归档恢复验证；审计/运维签字

## 目标

在所有旧 owner 和 route 已物理删除后，归档并删除 `modeling_legacy_object_migration_batch`、`modeling_legacy_object_migration`、`modeling_legacy_api_usage`。

## 技术设计（Contract-first）

- **归档输入**：全部 batch/mapping/usage，包含 tenant、source/target、decision、count、last_seen、checksum、操作者和时间。
- **归档输出**：受控 URI + size + SHA-256 + restoreVerifiedAt；摘要写入 dts-admin 分类审计和 IT evidence。
- **删除前提**：旧 route/table/service 全部 absent；无 rollback/recovery 运行依赖；30/90 天 usage 和 last_seen 已签字。
- **schema**：新 forward changeset 精确 drop 三表/FK/index；不修改 `20260719_07_legacy_object_retirement.xml`。
- **错误路径**：归档不可恢复、checksum 不符、仍有 caller/旧表或签字缺失即 NO_GO。
- **后续**：不建立 tombstone ledger；长期证据只在受控归档和中央审计。

## 影响范围

三个 legacy runtime ledger 及其 repository/service/query；dts-admin 中央审计只读核验，不删除。

## 验证

- [ ] archive restore 后 count/key/checksum 一致。
- [ ] schema/repository/bean absent。
- [ ] dts-admin audit history count/checksum 不变。
- [ ] historical Liquibase checksum 全部通过。

## Definition of Done

- [ ] 三个 runtime ledger 物理删除且无替代 tombstone 表。
- [ ] 退役证明可从归档和中央审计重建。
- [ ] F5 全部删除批次关闭后才允许进入 F6 最终验收。
