# Sprint-78 集成验收

**状态**：PASS（IT-04/05/06/07）；IT-01/02 随 F1 放弃（ADR-78-09）；IT-03 随 F2 暂缓；浏览器证据段 GAP（`it/baseline.md`）

| IT | 场景 | 必须断言 | 状态 | 证据位置 |
|---|---|---|---|---|
| IT-01 | 初始口令随机化与强制改密 | ~~创建用户 `temporary=true` 等~~ | N/A（F1 ABANDONED，ADR-78-09） | - |
| IT-02 | 一次性交付与存量处置 | ~~执行响应含一次性口令等~~ | N/A（F1 ABANDONED，ADR-78-09） | - |
| IT-03 | TLS 私钥出库与轮换 | `git ls-files` 无 p12 等 | PENDING（F2 暂缓，另行验收） | - |
| IT-04 | 备份执行与保留策略 | 9 库备份 SUCCESS + manifest 完整 + 权限 600/700；故障注入非零退出；超期清理正确、命名外不动 | **PASS** | `evidence/it-04-backup/` |
| IT-05 | 恢复演练 | 隔离库恢复成功、行数比对 PASS、演练后隔离库已删除 | **PASS** | `evidence/it-05-restore-drill/` |
| IT-06 | Hetu 移除 | compose/file provider 无 hetu 残留；`docker compose config` 通过；路径行为矩阵符合契约；前端无 HETU 选项、重定向保留；浏览器 smoke（基线 GAP 段） | **PASS**（浏览器段 GAP） | `evidence/it-06-hetu-removal/` |
| IT-07 | 合并质量门 | `changed_module_checks.sh` 建议项按本 Sprint 范围执行并有输出；`git diff --check` 通过 | **PASS** | `evidence/it-07-quality-gate/` |

说明：浏览器相关证据段受共享登录基线约束（见 `it/baseline.md`），以 GAP 显式记录而非占位；命令行/契约层证据均为 2026-07-31 真实执行输出。
