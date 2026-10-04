# Sprint-78 运维手册（G4 operability-pack）

## 1. 备份与恢复

### 每日备份
- crontab（建议避开整点）：`17 3 * * * /opt/prod/s10/v2.2.3/bin/dts-backup >> /opt/prod/s10/v2.2.3/logs/backup/dts-backup-$(date +\%F).log 2>&1`
- 安装：`crontab -e` 追加上行；卸载：删除该行。
- 巡检：每日确认 `backups/postgres/<ts>/manifest.txt` 全部 SUCCESS；连续失败检查 `logs/backup/` 与磁盘空间。

### 恢复（灾难场景）
1. 选定备份目录 `backups/postgres/<ts>/`。
2. 目标库为空库（或新建库）：`docker exec -i <pg> pg_restore -U <user> -d <db> --clean --if-exists < <ts>/<db>.dump`（按 runbook 实际命令执行）。
3. 验证行数与关键表；切换应用连接；观察服务日志。
4. 扩展/角色/owner 差异按 manifest 记录的 pg 版本核对。

### 恢复演练（常规）
`bin/dts-backup --restore-check latest`，演练后确认隔离库 `<db>_restore_check` 已删除。

## 2. TLS 证书

- 启用 tls profile：确认 `services/certs/keystore.p12` 存在、`.env` 含 `SERVER_SSL_KEY_STORE_PASSWORD`；compose 启用注释化示例块（挂载 + env + profile）。
- 轮换：`cp -r services/certs services/certs.bak.<date>` → 重跑 `services/certs/gen-certs.sh` → 重建使用证书的服务 → 验证 HTTPS/PKI；失败则恢复 `.bak` 目录。
- 排查：服务启不来先看是否缺 `SERVER_SSL_KEY_STORE`/`SERVER_SSL_KEY_STORE_PASSWORD`（显式失败为预期行为）。

## 3. 用户口令

- 新建用户：审批执行结果弹窗一次性展示初始口令 → 立即复制线下交付 → 用户首登强制改密。
- 口令未复制/丢失：走既有"重置密码（临时口令）"流程重新交付，系统无法找回原口令。
- 存量账号强制改密：使用 F1/T03 处置工具，先 dry-run 看范围，再执行；内置管理员账号自动排除。

## 4. Hetu 移除后

- 用户反馈旧链接（`/dashboards`、`/screen` 等）：说明已迁移至"数据分析 → /bi"；历史深链会自动重定向。
- Traefik 日志出现 `hetu` 字样：说明交付物混入旧配置，按 release-plan 回滚项核对。
- 磁盘巡检：备份目录按 10 库 × 14 天估算占用；接近磁盘 80% 时缩短 `--retention-days` 并归档旧备份。

## 5. 告警与升级

本 Sprint 不接告警系统（P1 统一规划）；过渡期巡检依赖：cron 日志、manifest、healthcheck 脚本。任何恢复/轮换/处置动作必须记录到运维日志（时间、操作者、范围、结果）。
