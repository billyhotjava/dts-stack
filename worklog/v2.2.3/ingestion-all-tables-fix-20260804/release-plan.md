# 发布安全计划 (Gate G3)

**变更类型**: 数据库接入运行时行为修复
**风险等级**: 中（运行时源表解析影响任务创建、准入制品、作业重建与执行）

## 1. 迁移策略

| 阶段 | 内容 | 本次是否包含 | rollback 段 |
|------|------|--------------|-------------|
| Expand | 无 Schema 变更 | 否 | - |
| Migrate | 无数据回填 | 否 | - |
| Contract | 无字段或接口删除 | 否 | - |

## 2. 兼容性

| 消费方 | 证据 | 是否受影响 | 处置 |
|--------|------|-----------|------|
| 数据库“全部表”接入 | `IngestionTaskService.resolveSource` | 是 | 空表清单在运行前发现当前数据库/Schema 的实际表 |
| 手工选表接入 | `tableMapping` / reader `table` | 否 | 继续使用显式表清单，不执行全表发现 |
| `querySql` 接入 | reader `querySql` | 否 | 保留查询模式，不执行全表发现 |
| 文件/API 接入 | 文件与 API 类型分支 | 否 | 原分支提前返回，行为不变 |
| Addax/Airflow | 现有 per-table 作业契约 | 兼容 | 仅将缺失表清单补全为现有契约要求的非空清单 |

## 3. 回填策略

- 无持久化结构或业务数据回填。
- 既有任务无需修改；每次执行从托管连接只读发现表。
- 发现结果为空时显式失败，不生成带 `${table}` 的危险作业。

## 4. 回滚

- 回滚镜像：`dts-ingestion:rollback-before-all-table-runtime-fix-20260804`
- 回滚命令：

  ```bash
  docker tag dts-ingestion:rollback-before-all-table-runtime-fix-20260804 dts-ingestion:1.0.0
  docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-ingestion
  ```

- 演练结果：回滚镜像 ID 已核对并保留；为避免中断当前健康服务，未实际切回，状态记为 `GAP`。
- 不可逆部分：无 Schema、数据迁移或外部通知。

## 5. 部署顺序与影响面

1. 聚焦单元测试验证全部表、`querySql` 和 MySQL 当前 catalog 行为。
2. 构建 `dts-ingestion:1.0.0`。
3. 仅重建 `dts-ingestion`，保持 Airflow、Addax、PostgreSQL 不变。
4. 核对容器镜像 ID、健康检查与启动日志。

影响模块：`source/dts-ingestion`；无需前端、数据库迁移或其他容器重建。
