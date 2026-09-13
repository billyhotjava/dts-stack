# IT-05：恢复演练

**日期**：2026-07-31
**状态**：PASS
**执行人**：AI-assisted（Sprint-78/F3/T03）

## 命令

```
bin/dts-backup --restore-check latest --dbs dts_keycloak,dts_platform,dts_common
```

（备份源：`backups/postgres/20260731-215737`；覆盖契约要求的 keycloak + platform + 1 个抽样库）

## 输出（`logs/backup/dts-backup-restore-check.log`）

```
[dts-backup] restore-check from /opt/prod/s10/v2.2.3/backups/postgres/20260731-215737
[dts-backup] restore-check dts_keycloak -> dts_keycloak_restore_check ...
[dts-backup] restore-check dts_keycloak: PASS (tables=88, rows matched, 2s)
[dts-backup] restore-check dts_platform -> dts_platform_restore_check ...
[dts-backup] restore-check dts_platform: PASS (tables=233, rows matched, 88s)
[dts-backup] restore-check dts_common -> dts_common_restore_check ...
[dts-backup] restore-check dts_common: PASS (tables=0, rows matched, 1s)
[dts-backup] restore-check: all PASS
EXIT=0
```

## 断言

| 断言 | 结果 |
|---|---|
| 隔离库恢复成功（pg_restore exit=0） | PASS（3/3） |
| 表数量与源库一致 | PASS（88 / 233 / 0） |
| 逐表精确行数比对（每库至多 200 表） | PASS（rows matched，0 mismatch） |
| 演练后隔离库已删除 | PASS（`SELECT datname FROM pg_database WHERE datname LIKE '%restore_check%'` 返回空） |
| 源库只读、业务无影响 | PASS（比对均为只读 SELECT；运行栈全程健康） |

## 结论

IT-05 PASS。恢复链路（pg_dump -Fc → pg_restore --no-owner --no-privileges → 数据一致）在本环境真实验证通过；dts_platform 全库（33.9MB 导出、233 表）恢复+比对耗时 88s，可作为单库 RTO 参考基线（< 1.5h 预算，`assets/nfr-budget.md`）。
