# F1: 容器自恢复策略

**优先级**: P0
**状态**: IN_PROGRESS

## 目标
补齐关键容器的自动恢复策略，保证宿主机重启或 Docker 服务恢复后，现场不需要人工逐个拉起容器。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 盘点 legacy 与 normal 模式下缺失的 restart 策略 | P0 | DONE | - |
| T02 | 统一关键容器 restart 策略并明确排除一次性任务 | P0 | IN_PROGRESS | T01 |
| T03 | 定义宿主机重启后的最小恢复验收标准 | P1 | READY | T02 |

## 完成标准
- [ ] 明确哪些服务必须 `restart: unless-stopped`
- [ ] 明确哪些 init/一次性任务必须保持 `restart: "no"`
- [ ] `legacy` 与 `normal` 模式都具备可重复的自动恢复行为
