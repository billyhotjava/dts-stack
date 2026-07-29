# F3：PostgreSQL 定时备份与恢复验证

**优先级**：P0
**状态**：READY

## 目标

单实例 PostgreSQL 承载的 10 个业务库具备每日定时逻辑备份（RPO ≤ 24h）、14 天保留、权限受控，且恢复流程经过真实演练验证。

## 契约定义（Contracts）

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| 命令 | `bin/dts-backup [--dir DIR] [--retention-days N]` | 默认 dir=`backups/postgres`、retention=14；退出码 0=全部成功，非 0=任一库失败 |
| 产物 | `backups/postgres/<YYYYMMDD-HHMMSS>/<db>.dump` + `manifest.txt` | pg_dump custom format（`-Fc`）；manifest 含库清单、大小、pg 版本、时间戳；目录权限 700、文件 600 |
| 配置 | 库清单来源 `.env` 的 `PG_DB_*` | 复用 init.sh 同一注册口径（账本#11），新增库自动纳入 |
| 调度 | 宿主机 crontab | 每日执行 + 日志落 `logs/backup/`；安装方式写入 runbook，脚本本身与调度器解耦 |
| 恢复 | 恢复演练流程 | 恢复到隔离库 `<db>_restore_check` → 关键表行数比对 → 校验通过 → 删除隔离库 |

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | bin/dts-backup 备份脚本 | P0 | READY | - |
| T02 | 定时调度接入与保留策略执行 | P0 | READY | T01 |
| T03 | 恢复演练与验证证据 | P0 | READY | T01 |

## Definition of Ready

- [x] 契约已钉死（CLI、产物布局、权限、退出码）
- [x] 竖切片已画通（备份 → 调度 → 保留清理 → 恢复演练 → 校验）
- [x] UI 落点：本 Feature 无 UI（运维能力）
- [x] 依赖已就绪（`docker exec dts-pg pg_dump` 路径与 init.sh 库清单口径已有先例，账本#11/#12）
- [x] 验收可验证（IT-04/IT-05）

## 完成标准

- [ ] 10 库备份一次成功，manifest 完整，权限 600/700（IT-04）
- [ ] 保留策略删除超期目录且不误删（IT-04）
- [ ] 恢复演练行数比对通过（IT-05）
- [ ] runbook 含备份/恢复/告警处置步骤（IT-05）
