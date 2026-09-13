# F1: 配置与部署基线

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

修复 OpenMetadata 集成的配置根因，保证新装和存量环境都能得到一致、可诊断、可运行的配置。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | 修复 FQN Pattern 默认值与初始化 | P0 | DONE |
| T02 | 存量环境配置迁移与诊断 | P0 | DONE |
| T03 | 认证与 no-auth 策略收敛 | P0 | DONE |
| T04 | Compose 与镜像配置一致性校验 | P1 | DONE |

## 完成标准

- [x] `init.sh` 不再把 `{service}.{database}.{table}` 截断为 `{service`。
- [x] 旧 `.env` 中的坏 pattern 能被诊断并给出修复动作。
- [x] token/no-auth 行为在 server、ingestion 容器、platform、ingestion-service 间一致。
- [x] 关键配置有最小文档和验收命令。
