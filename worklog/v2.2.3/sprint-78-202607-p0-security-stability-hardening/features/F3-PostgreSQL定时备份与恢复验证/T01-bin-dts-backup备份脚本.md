# T01：bin/dts-backup 备份脚本

**优先级**：P0
**状态**：DONE
**依赖**：无

## 目标

一个宿主机脚本完成全部业务库的逻辑备份：库清单自动发现、逐库 pg_dump、manifest 记录、失败即非零退出。

## 技术设计（Contract-first）

- **输入契约**：`.env` 中 `PG_DB_*`（账本#11）；可选参数 `--dir`、`--retention-days`（本任务只落备份与 manifest，清理逻辑在 T02）。
- **输出契约**：见 F3 契约表——`<ts>/<db>.dump`（`-Fc`）+ `manifest.txt`；stdout 逐库进度；stderr 错误明细。
- **数据流**：读取 .env → 枚举 `PG_DB_*` 与对应 `PG_USER_*` → `docker exec <pg容器> pg_dump -U <user> -d <db> -Fc` → 落盘 → 写 manifest → 汇总退出码。
- **错误路径**：容器不可用/单库失败 → 该库标记 FAILED 继续其余库，最终非零退出；磁盘空间预检（估算不足则直接失败，不写半个备份）；产物目录先写临时名成功后原子改名，避免半成品被当成有效备份。
- **复用点**：init.sh 的库清单与 `docker exec ... psql` 调用形态（账本#11）；`bin/lib/` 既有公共函数（如有日志/锁函数则复用，实施期一次 grep 确认，禁止重扫账本项）。
- **实现方案**：
  1. bash 脚本，风格对齐 `bin/dts-upgrade-*`；`set -euo pipefail`；flock 防并发重入。
  2. 口令传递走容器内环境（`PGPASSWORD` 经 `docker exec -i` env），不出现在命令行参数与日志（security-compliance）。
  3. manifest 字段：时间戳、pg 版本、每库 `{name, file, bytes, status, duration_s}`。
  4. 不动 `services/dts-pg/data`（devops-runbook 红线）；备份一律走 pg_dump 逻辑导出。
- **禁止**：文件级拷贝 data 目录充当备份；在脚本中硬编码库清单（必须自动发现）。

## 影响范围

- `bin/dts-backup`（新增）
- `backups/postgres/`（新增产物目录，确认 `.gitignore` 覆盖）
- `logs/backup/`（日志目录）

## 验证（RED→GREEN）

- [ ] 全量执行一次：10 库全部 SUCCESS，manifest 字段完整。
- [ ] 故障注入：停止 PG 容器后执行，非零退出且已成功的库保留、失败库标记 FAILED。
- [ ] 权限检查：产物目录 700、文件 600；日志无口令。

## Definition of Done

- [ ] 架构：脚本 + 故障注入验证输出
- [ ] UI：无
- [ ] 切片：执行输出与 manifest 入 `it/evidence/it-04-backup/`
- [ ] 无占位证据
