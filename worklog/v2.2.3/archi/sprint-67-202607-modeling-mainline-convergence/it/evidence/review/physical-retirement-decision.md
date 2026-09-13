# F5 物理退役决策

结论：`NO-DROP`。

当前门禁事实：

- `unresolvedRecords=5`：4 条 `NEEDS_CLASSIFICATION`、1 条 `ARCHIVE_ONLY`；
- `recentLegacyCalls=2`：均为本次真实旧写冻结验收调用；
- `backupApproved=false`；
- source checksum 已建立且 5/5 记录全部入账。

阻塞码：`LEGACY_RECORDS_STILL_READONLY`、`LEGACY_CONSUMERS_NOT_ZERO`、`BACKUP_APPROVAL_REQUIRED`。

因此仅完成对外退役、写冻结、只读隔离和审计台账；没有删除旧表、字段、FK 或只读 API。待人工分类、观察窗口归零和独立备份审批后再启动 contract/drop 变更。
