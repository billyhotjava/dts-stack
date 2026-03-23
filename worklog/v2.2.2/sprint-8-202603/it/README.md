# IT: 离线环境平滑升级

本目录用于沉淀 `Sprint-8` 的集成验证与现场验收资产。

## 当前自动化回归

已落地的升级相关验证命令：

```bash
bash tests/test_dts_upgrade_preflight.sh
bash tests/test_dts_upgrade_images.sh
bash tests/test_dts_upgrade_env_merge.sh
bash tests/test_dts_upgrade_compose_merge.sh
bash tests/test_dts_upgrade_config_merge.sh
bash tests/test_dts_upgrade_backup_and_rollback.sh
bash tests/test_dts_upgrade_e2e.sh
bash tests/test_dts_upgrade_mode_and_pg_checks.sh
bash tests/test_dts_upgrade_rollback.sh
bash tests/test_dts_upgrade_sync_new_files.sh
```

## 自动化覆盖点

- `legacy` 模式启动必须使用 `docker-compose.legacy.yml`
- `single` 模式升级后启动必须带上 `docker-compose-app.yml`
- `legacy` 升级不允许把 normal compose 文件混入目标目录
- `.env` 旧值优先，新键自动补入
- compose 合并保留现场端口、卷、环境变量等运行值
- `config/` 结构化配置走旧值优先合并，未知文件进入冲突备份
- `services/dts-pg/data` 保持原位不动，同时会被冷备到 `backups/upgrade-*`
- 新包里的运行脚本/资源会同步到旧目录，但 `.env`、compose、`config/`、`services/dts-pg/data`、日志和备份目录会被显式排除
- 被覆盖的运行文件必须先进入 `backups/upgrade-*`，并记录到 `rollback-manifest.json`
- PostgreSQL major 不兼容时升级必须直接阻断
- `rollback-manifest.json` 必须覆盖配置备份和数据库冷备目录
- `bin/dts-upgrade-rollback` 必须支持配置回滚和 `--restore-db` 数据目录回滚

## 现场人工验收

升级完成后至少检查：

1. `logs/upgrade-*.log` 和 `logs/upgrade-*.summary.md` 已生成。
2. 现场 `.env` 中自定义变量仍然存在，新包新增变量已补齐。
3. `legacy` 环境实际使用 `docker-compose.legacy.yml` 启动；`single` 环境实际使用 `docker-compose.yml` 和 `docker-compose-app.yml` 启动。
4. `services/dts-pg/data` 原目录内容仍在，且 `backups/upgrade-*/services/dts-pg/data` 冷备已生成。
5. PostgreSQL 容器成功启动，关键业务库可连接。
6. 若升级失败，`rollback-manifest.json` 中能定位到配置备份和数据库冷备目录。
7. 若执行回滚，`logs/rollback-*.log` 与 `logs/rollback-*.summary.md` 已生成。

## Runbook

- [offline-upgrade-runbook.md](/opt/prod/s10/s10-stack/worklog/v2.2.2/sprint-8-202603/assets/offline-upgrade-runbook.md)
