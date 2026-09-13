# Sprint-78 发布计划（G3 release-safety）

**原则**：现场无网可执行；先备份后变更；回滚步骤先演练（ADR-78-08）。

## 变更清单与兼容性

| # | 变更 | 类型 | 兼容性 | 回滚 |
|---|---|---|---|---|
| 1 | F1 初始口令随机化 + temporary | 代码（dts-admin + admin-webapp） | 行为变更：新用户首登必须改密；存量用户不受影响 | 回滚代码即恢复旧行为（不推荐，保留审计） |
| 2 | F1/T03 存量处置工具 | 代码（可选执行） | 默认不执行；执行后目标用户下次登录必须改密 | 移除 required action 即可恢复 |
| 3 | F2 keystore 出库 + tls env 化 | 配置/交付 | 默认 profile 无行为变化；tls profile 需 env 才能启动（显式失败） | git 恢复 yml；tls 保持关闭 |
| 4 | F2/T03 私钥轮换 | 运维动作 | 新证书需客户端重新信任；PKI 用户需重注册/重发（按现场 PKI 流程） | 恢复备份的 `services/certs` 并重启 |
| 5 | F3 备份脚本与调度 | 新增 | 纯新增，无行为变更 | 卸载 crontab、删除脚本与产物 |
| 6 | F4 Hetu 代理移除 | 配置（compose + file provider） | 旧 hetu 路径不再代理，回落 SPA；用户入口收敛 `/bi` | `git revert` compose 与 traefik-dynamic.yml，重建 proxy |

## 已知风险声明（必须随发布告知）

- **Git 历史泄露**：旧 `keystore.p12` 及其口令曾随源码分发，凡获得历史版本者均持有旧私钥。本发布不重写 Git 历史，以 F2/T03 轮换消除实际风险；现场必须执行轮换步骤才算关闭该风险。
- **旧路径书签失效**：使用 `/dashboards`、`/screen` 等 Hetu 路径的书签/外嵌链接将回落到平台 SPA；请用户改用 `/bi` 入口（历史深链重定向仍生效）。
- **新用户流程变化**：管理员创建用户后必须当场复制一次性口令并线下交付；系统不再使用统一初始口令。

## 现场升级顺序（离线）

1. 执行一次 `bin/dts-backup` 全量备份（F3 先部署脚本）。
2. 更新代码与 compose，重建受影响服务（admin、platform、proxy、admin-webapp、platform-webapp）。
3. 执行 F2/T03 证书轮换（含回滚演练）。
4. 验证：`dts_healthcheck.sh` + 路径行为矩阵 + 创建测试用户验证一次性口令与强制改密。
5. （可选，现场决定）执行 F1/T03 存量账号处置（先 dry-run）。
6. 卸载/安装事项：安装备份 crontab；确认 `HETU_UPSTREAM_IP` 仅存于旧 .env（标记退役）。

## 回滚总原则

任何一步验证失败：先恢复对应服务的上一版镜像/配置（compose 与 yml 走 git revert），证书问题恢复 `services/certs` 备份，再排查。备份脚本与产物不受回滚影响。
