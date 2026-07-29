# Sprint-78 集成验收

**状态**：PENDING（规划完成，未实施）

| IT | 场景 | 必须断言 | 状态 | 证据位置 |
|---|---|---|---|---|
| IT-01 | 初始口令随机化与强制改密 | 创建用户 `temporary=true`；口令非 `"sa"` 且两次生成不同；设密失败创建整体失败；日志/DB/审计无明文；无 `DEFAULT_INITIAL_PASSWORD` 残留 | PENDING | `evidence/it-01-initial-password/` |
| IT-02 | 一次性交付与存量处置 | 执行响应含一次性口令、再查无明文；处置工具 dry-run 无副作用、幂等、内置账号排除、审计完整 | PENDING | `evidence/it-02-legacy-passwords/` |
| IT-03 | TLS 私钥出库与轮换 | `git ls-files` 无 p12；无明文口令；env 注入可启动 tls profile、未注入显式失败；轮换与回滚各演练一次 | PENDING | `evidence/it-03-tls-keystore/` |
| IT-04 | 备份执行与保留策略 | 10 库备份 SUCCESS + manifest 完整 + 权限 600/700；故障注入非零退出；超期目录清理正确、命名外目录不动 | PENDING | `evidence/it-04-backup/` |
| IT-05 | 恢复演练 | 隔离库恢复成功、行数比对 PASS、演练后隔离库已删除 | PENDING | `evidence/it-05-restore-drill/` |
| IT-06 | Hetu 移除 | compose/file provider 无 hetu 残留；`docker compose config` 通过；路径行为矩阵符合契约；前端无 HETU 选项、重定向保留；浏览器 smoke（基线 GAP 段） | PENDING | `evidence/it-06-hetu-removal/` |
| IT-07 | 合并质量门 | `.skills/dts-quality-gate/scripts/changed_module_checks.sh` 建议项全部执行并有输出；`git diff --check` 通过 | PENDING | `evidence/it-07-quality-gate/` |

说明：浏览器相关证据段受共享登录基线约束（见 `it/baseline.md`），以 GAP 显式记录而非占位；源代码/命令行证据不得标注"待补"后先行标记 DONE。
