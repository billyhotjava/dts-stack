# Sprint-8: 离线环境平滑升级

**时间**: 2026-03
**状态**: DONE
**目标**: 为内网离线现场交付一套可重复执行的平滑升级方案，兼容 legacy/normal 模式，保留 `.env` 自定义配置与 `services/dts-pg/data` 数据目录

## 背景

`worklog/v2.2.2/sprint-2` 已经收过一版“离线环境一键升级编排”，但结构还是旧式 flat sprint，且当前约束已经明显扩展：

- 客户环境是内网离线环境
- 现场大量定制保存在 `.env`
- legacy 模式下运行主文件是 `docker-compose.legacy.yml`
- PostgreSQL 数据保存在 `services/dts-pg/data`
- 平滑升级不能只看 compose 合并，还必须单独处理数据库兼容、备份和回滚

因此该工作不再继续塞回旧的 `sprint-2`，而是按新的 sprint-workflow 独立为 `Sprint-8`。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 升级模式识别与包契约 | 3 | DONE |
| F2 | 配置与数据库平滑升级 | 4 | DONE |
| F3 | 升级执行与回滚验收 | 3 | DONE |

## 当前推进

- 已确认 `legacy` 模式的运行态主文件是 `docker-compose.legacy.yml`，不能在现场按 `docker-compose.yml + docker-compose-app.yml` 重新拼装。
- 已确认 `start.sh` / `stop.sh` 当前不识别 `LEGACY_STACK`，升级器必须自己选择 compose 主文件，不能直接复用默认脚本语义。
- 已确认 `services/dts-pg/data` 是状态数据目录，新包中的同名目录只能作为骨架，升级时不得覆盖旧目录。
- 已确认 PostgreSQL 平滑升级需要独立门禁：升级前必须校验旧数据目录 `PG_VERSION` 与目标 `IMAGE_POSTGRES` major 是否兼容。
- 已实现 mode-detect：升级前检查与升级后启动分别使用独立 compose 集合，`legacy` 只走 `docker-compose.legacy.yml`，`single` 模式会在启动阶段自动带上 `docker-compose-app.yml`。
- 已实现 PostgreSQL 数据目录冷备：升级前会把 `services/dts-pg/data` 复制到 `backups/upgrade-*/services/dts-pg/data`，并写入 `rollback-manifest.json`。
- 已补回归测试：`tests/test_dts_upgrade_mode_and_pg_checks.sh` 覆盖 legacy 启动、normal single app compose 启动、legacy compose 过滤和 PostgreSQL major 不兼容阻断。
- 已实现 `bin/dts-upgrade-rollback`：支持按 `rollback-manifest.json` 恢复配置，并可通过 `--restore-db` 恢复 PostgreSQL 冷备目录。
- 已补现场 runbook：[offline-upgrade-runbook.md](/opt/prod/s10/s10-stack/worklog/v2.2.2/sprint-8-202603/assets/offline-upgrade-runbook.md)，覆盖升级成功检查、配置回滚、数据库整目录回滚和 legacy/normal 重启方式。
- 已实现 `SYNC_NEW_FILES`：升级时会把新包中的运行脚本/资源同步到旧目录，并显式排除 `.env`、compose、`config/`、`services/dts-pg/data`、日志和备份目录；被覆盖的运行文件会先写入本次升级备份。
- 已补回归测试：`tests/test_dts_upgrade_sync_new_files.sh` 覆盖运行文件同步、覆盖前备份，以及 PostgreSQL 数据目录不得被新包骨架污染。

## 完成标准
- [x] 能识别 normal/legacy 升级模式，并选择正确的 compose 主文件
- [x] `.env` 按旧值优先合并，新包新增键自动补齐
- [x] `docker-compose.legacy.yml` 在 legacy 模式下作为运行态主文件单独合并
- [x] `services/dts-pg/data` 不被新包覆盖，并纳入版本兼容、备份和回滚策略
- [x] 升级过程生成日志、摘要和回滚清单
- [x] 离线环境下不依赖任何远程下载
