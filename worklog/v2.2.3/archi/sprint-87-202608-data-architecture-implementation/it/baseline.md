# Sprint-87 交付基线

**状态**：BLOCKED

| 输入 | 当前状态 | 通过证据 |
|---|---|---|
| 工作分支/HEAD | PASS：`v2.2.3` / `2649d474b`（规划时） | 开工前重新记录 |
| 客户/生产画像 | BLOCKED_INPUT | 脱敏统计探查归档 |
| GitNexus 索引 | BLOCKED_TOOLING | analyze 完成且 HEAD 一致 |
| 登录与菜单 | BLOCKED_INPUT | 明确账号、菜单点击可达 |
| Chrome 95 | PENDING | 现场内核 smoke |
| 数据备份/回滚 | BLOCKED_INPUT | 备份锚点 + dry-run/apply/rollback 演练 |
| Sprint-86 ADR/IT | PASS（架构） | IT-01～07 已通过；运行实现/验证仍在本 Sprint |

以上输入未齐前，F1～F6 不得进入 READY。
