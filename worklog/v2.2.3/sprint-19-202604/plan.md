# Sprint-19 执行计划

## 阶段 0 - 基线确认

- 复核当前 `.env`、`docker-compose-app.yml`、OpenMetadata 容器状态和 ingestion 日志。
- 记录当前失败基线：FQN pattern 截断、token 为空、lineage streams 为空、目标库配置解析不完整。
- 输出现场验收命令和初始证据到 `it/README.md`。

## 阶段 1 - 配置先行

- 修复 `init.sh` 默认值生成，避免 Bash 参数展开截断 `{service}.{database}.{table}`。
- 增加已有 `.env` 迁移或诊断：发现 `{service` 时给出修复动作。
- 明确 OpenMetadata token / no-auth / healthcheck 策略。

**退出条件**: 新环境和旧环境都能得到合法 FQN pattern；OpenMetadata server 与 ingestion 的认证配置不互相冲突。

## 阶段 2 - 写路径闭环

- 重构目标库连接配置解析，支持 Addax nested `connection`、`jdbcUrl` 和顶层字段。
- 将 create service、create pipeline、trigger pipeline、register lineage 的结果结构化。
- 从接入任务上下文提取真实 source/target table mapping，替换空 streams。

**退出条件**: 一个数据库接入任务完成后，OpenMetadata 中能看到服务、目标表、pipeline 触发记录和表级血缘。

## 阶段 3 - 采集作业运维化

- 修正 one-shot ingestion 脚本的 token/no-auth 行为。
- 增加 dbt artifact / Postgres 元数据采集的可重复执行 runbook。
- 将 ingestion 容器退出、跳过、失败和成功状态纳入现场验收。

**退出条件**: `dts-openmetadata-ingestion` 不再无声跳过；失败时能在日志和文档中定位原因。

## 阶段 4 - 读路径和体验收敛

- 修复平台侧 FQN candidate 生成和日志。
- 返回 metadata source、fallback reason、OpenMetadata lookup detail。
- 前端展示 OpenMetadata 命中、未命中、回退和采集失败状态。

**退出条件**: 用户打开 catalog 详情、血缘、质量页时能区分 OpenMetadata 数据和本地 catalog 数据。

## 阶段 5 - 发布门禁

- Java 单测覆盖 FQN、配置 parser、lineage stream builder、OpenMetadata client 错误处理。
- compose 冒烟覆盖 OpenMetadata server、ingestion、dts-platform、dts-ingestion。
- 文档覆盖部署、升级、回滚和已知限制。

**退出条件**: `it/README.md` 留存命令、结果、证据路径和残余风险。

## Sprint 看板规则

- Task 进入开发前从 `READY` 改为 `IN_PROGRESS`。
- 合并或交付后改为 `DONE`，并在相关 feature README 勾选完成标准。
- 发现需要延期的能力，新增 follow-up，不在本 Sprint 中扩大范围。
- 涉及运行时配置的 task 必须同步更新验收命令或 runbook。
