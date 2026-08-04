# 发布安全计划 (Gate G3)

**变更类型**: 执行日志查询修复
**风险等级**: 低（只读日志查询，不影响任务执行或数据写入）

## 1. 迁移策略

| 阶段 | 内容 | 本次是否包含 | rollback 段 |
|------|------|--------------|-------------|
| Expand | 无 Schema 变更 | 否 | - |
| Migrate | 无数据回填 | 否 | - |
| Contract | 无字段或接口删除 | 否 | - |

## 2. 兼容性

| 消费方 | 证据 | 是否受影响 | 处置 |
|--------|------|-----------|------|
| 执行日志抽屉 | `ExecutionHistoryTable.loadLog` | 是 | API 路径与响应结构不变，恢复实际日志内容 |
| 日志代理 | `IngestionTaskProxyResource.getExecutionLog` | 否 | 继续透传并执行现有响应脱敏 |
| 新执行记录 | `IngestionExecution.airflowDagId` | 是 | 优先使用执行时保存的不可变 DAG ID |
| 旧执行记录 | 无执行 DAG ID | 兼容 | 回退 `AirflowDagService.resolveDagIdForTask` 的原有行为 |

## 3. 回填策略

- 无持久化数据回填。
- Airflow 日志仅按请求读取，不复制到数据库。

## 4. 回滚

- 发布前为当前 `dts-ingestion:1.0.0` 创建独立回滚标签。
- 回滚命令：

  ```bash
  docker tag dts-ingestion:rollback-before-execution-log-dag-fix-20260804 dts-ingestion:1.0.0
  docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-ingestion
  ```

- 回滚镜像：`dts-ingestion:rollback-before-execution-log-dag-fix-20260804`
  - 镜像 ID：`sha256:43ee69c2cb6fa65af1c52cf64013a0441e02e5a97d0642d18d001092382f8f77`
- 发布镜像：`dts-ingestion:1.0.0`
  - 镜像 ID：`sha256:1e96b2889ad716d6c1976737226f26216108f146b7bcbf5d78c3a43b5788fa3d`
- 演练结果：回滚标签与镜像 ID 已核对；为避免服务中断未实际切回，状态为 `GAP`。
- 不可逆部分：无。

## 5. 部署顺序与影响面

1. 验证执行 DAG 与当前任务 DAG 不一致的回归测试。
2. 编译全部 Java 主源码与测试源码。
3. 标记当前镜像并构建 `dts-ingestion:1.0.0`。
4. 仅重建 `dts-ingestion` 并等待健康。
5. 通过执行 `#19` 的日志接口验证 DAG、节点候选及非空日志。

影响模块：`source/dts-ingestion`；无需前端、平台、Airflow 或数据库重建。

## 6. 验证结果

- `IngestionExecutionQueryServiceTest`：4 个测试通过。
- `mvn -q -DskipTests package`：通过，主源码与测试源码均完成编译。
- `v223-dts-ingestion-1`：运行发布镜像并达到 `healthy`。
- 真实执行 `#19`：
  - 返回执行时保存的 DAG `ingestion_revision_5_execution_19_task_3`；
  - 单节点日志长度 22080 字符；
  - 全节点识别 6 个候选节点，聚合日志长度 137798 字符。
- 日志内容未写入发布记录，仅记录长度和节点数量。
