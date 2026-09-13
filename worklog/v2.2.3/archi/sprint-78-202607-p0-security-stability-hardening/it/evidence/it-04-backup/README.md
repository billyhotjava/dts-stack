# IT-04：备份执行与保留策略

**日期**：2026-07-31
**状态**：PASS
**执行人**：AI-assisted（Sprint-78/F3）

## 1. 全量备份（9 库自动发现）

命令：`bin/dts-backup`（tee 到 `logs/backup/dts-backup-first-run.log`）

```
[dts-backup] discovered 9 database(s): airflow biadmin dts_admin dts_analytics dts_common dts_keycloak dts_platform dts_ranger openmetadata_db
[dts-backup] backup airflow: 191581 bytes in 0s
[dts-backup] backup biadmin: 227600 bytes in 0s
[dts-backup] backup dts_admin: 621727 bytes in 1s
[dts-backup] backup dts_analytics: 461613 bytes in 0s
[dts-backup] backup dts_common: 899 bytes in 0s
[dts-backup] backup dts_keycloak: 227572 bytes in 0s
[dts-backup] backup dts_platform: 33919213 bytes in 12s
[dts-backup] backup dts_ranger: 899 bytes in 0s
[dts-backup] backup openmetadata_db: 664839 bytes in 1s
[dts-backup] backup directory: backups/postgres/20260731-215737 (failures=0)
[dts-backup] retention: nothing to delete (keep 14 days)
```

注：`.env` 实际注册 9 个 `PG_DB_*`（本环境未部署 metrics 库），脚本按发现口径备份，与契约"自动发现、不硬编码"一致。

## 2. manifest 与权限

`backups/postgres/20260731-215737/manifest.txt`：含 timestamp、pg_version=17.6、container=v223-dts-pg-1、每库 `db|file|bytes|status|duration` 九行全 SUCCESS。

```
700 backups/postgres
700 backups/postgres/20260731-215737
600 .../*.dump（9 个文件）
grep -ci "password\|PGPASSWORD" logs/backup/dts-backup-first-run.log -> 0
```

## 3. 故障注入（假库混入选库清单）

方法：`DTS_BACKUP_ENV_FILE=/tmp/dts-backup-test.env`（真实 9 库 + `PG_DB_BOGUS=dts_bogus_db`），`DTS_BACKUP_DIR=/tmp/dts-backup-test`。不停止生产 PG，避免影响运行栈。

```
WARNING: backup FAILED for dts_bogus_db: ... FATAL:  database "dts_bogus_db" does not exist
（其余 8 库 SUCCESS）
backup directory: /tmp/dts-backup-test/20260731-220019 (failures=1)
```

断言验证：

- 退出码：bogus 环境 `EXIT=1`；正常环境 `EXIT=0`（无管道遮蔽，直接 `$?` 验证）。
- manifest 中 `dts_bogus_db|dts_bogus_db.dump|0|FAILED|0s`；其余库 SUCCESS，半成品目录仍保留可用（`.wip` 原子改名生效）。

## 4. 保留策略演练

构造：`/tmp/dts-backup-test/{20260701-030000（30 天前）, 20260725-030000（6 天前）, manual-keep（命名外）}`。

```
dry-run: [dry-run] would delete /tmp/dts-backup-test/20260701-030000（不删除）
real:    retention: deleted /tmp/dts-backup-test/20260701-030000 (older than 14 days)
after:   20260725-030000、manual-keep、当日各 ts 目录均保留
```

断言：超期删除 ✔；未超期保留 ✔；命名外目录不误删 ✔；`--dry-run` 只报告 ✔。

## 结论

IT-04 全部断言 PASS：10 库口径自动发现（本环境 9 库）、manifest 完整、权限 700/600、日志无口令、故障注入非零退出且半成品保留、保留策略双向正确。
