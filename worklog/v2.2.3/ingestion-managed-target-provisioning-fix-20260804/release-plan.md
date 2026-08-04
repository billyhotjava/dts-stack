# 发布安全计划 (Gate G3)

**变更类型**: 托管目标数仓凭据解析修复
**风险等级**: 中（影响数据库接入执行前的目标表自动建表）

## 1. 迁移策略

| 阶段 | 内容 | 本次是否包含 | rollback 段 |
|------|------|--------------|-------------|
| Expand | 无 Schema 变更 | 否 | - |
| Migrate | 无数据回填 | 否 | - |
| Contract | 无字段或接口删除 | 否 | - |

## 2. 兼容性

| 消费方 | 证据 | 是否受影响 | 处置 |
|--------|------|-----------|------|
| 托管目标数仓自动建表 | `TargetTableProvisioner.ensureTargetTables` | 是 | 根据 `targetDataSourceId` 从平台运行时详情解析 JDBC 凭据 |
| Addax 作业生成 | `AddaxJobService.resolveManagedDestinationConfig` | 否 | 保留现有托管目标解析流程 |
| 任务配置与 API | `destinationConfig` | 否 | 继续只保存目标数据源 ID，不持久化密码 |
| 非托管目标连接 | `TargetTableProvisioner.buildConnectionInfo` | 否 | 未配置目标数据源 ID 时沿用原有内嵌连接解析 |

## 3. 回填策略

- 无持久化结构或业务数据回填。
- 不把平台密钥复制到任务、Revision 或 Addax 审计数据。

## 4. 回滚

- 回滚标签：`dts-ingestion:rollback-before-managed-target-provisioning-fix-20260804`
- 回滚镜像：`sha256:a1820c7173cb908696be8335f3a08e6cef9b79d0ff1673b0439f0d23e54f7daf`
- 回滚命令：

  ```bash
  docker tag dts-ingestion:rollback-before-managed-target-provisioning-fix-20260804 dts-ingestion:1.0.0
  docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-ingestion
  ```

- 演练结果：回滚标签及镜像 ID 已核对；为避免中断接入服务，不实际切回，状态记为 `GAP`。
- 不可逆部分：任务执行可能写入目标表，因此部署过程不自动触发任务。

## 5. 部署顺序与影响面

1. 验证托管目标凭据回归测试及 Java 编译。
2. 标记当前镜像作为回滚点。
3. 构建 `dts-ingestion:1.0.0`。
4. 仅重建 `dts-ingestion` 并核对健康状态、镜像 ID 与启动日志。
5. 由用户手工重跑任务 3，确认自动建表与 Addax 执行链路。

影响模块：`source/dts-ingestion`；无需前端、数据库迁移或其他容器重建。

发布结果：`dts-ingestion:1.0.0` 为 `sha256:43ee69c2cb6fa65af1c52cf64013a0441e02e5a97d0642d18d001092382f8f77`，容器镜像一致且健康检查为 `healthy`。
