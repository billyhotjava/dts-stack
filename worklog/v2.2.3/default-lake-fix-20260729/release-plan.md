# 默认数据湖一致性修复发布方案

## 目标与边界

- 修复 `dts-admin` 默认数据湖已经指向 `biadmin`，但 `dts-platform` 本地镜像及既有入湖任务仍保留 `biadmin1` 快照的问题。
- `dts-admin` 是系统默认湖连接参数的唯一权威来源；Platform UUID 仅作为稳定引用。
- 普通显式数据源仍以 Platform 本地配置为权威，不被系统默认湖同步逻辑认领。
- 仅发布 `dts-platform`，不修改 `dts-admin`、`dts-ingestion`、前端或远程 MySQL/API Demo。
- 不创建错误数据库 `biadmin1`，不做浏览器 E2E；由用户完成页面手工验收。

## 发布前基线

- Admin 默认湖：`jdbc:postgresql://dts-pg:5432/biadmin`，状态 `ACTIVE`。
- Platform 管理镜像 ID：`a0000000-0000-0000-0000-000000000001`。
- 故障镜像 JDBC：`jdbc:postgresql://dts-pg:5432/biadmin1`。
- 故障任务：`ingestion_task.id=12`、名称 `findemo1`。
- 任务旧快照仅允许出现在：
  - `destination_config.jdbcUrl`
  - `destination_config.connection[0].jdbcUrl`
- PostgreSQL 必须存在 `biadmin`，且不得依赖 `biadmin1`。

任一行数或 JSON 路径不符合上述基线时停止修复，不执行宽泛文本替换。

## 备份

1. 为当前运行镜像创建不可变回退标签，记录原镜像 ID。
2. 精确导出以下两条完整记录（包括密文列，但不输出明文 Secret）：
   - `dts_platform.public.infra_data_source` 固定镜像行。
   - `dts_platform.public.ingestion_task` 的 `id=12` 行。
3. 备份目录权限 `0700`、备份文件权限 `0600`。
4. 在 PostgreSQL 临时表中回读两份 CSV，各验证恰好一行后回滚验证事务。

## 代码发布顺序

1. 聚焦测试和生产编译通过。
2. 构建 `dts-platform:1.0.0` 新镜像。
3. 仅重建 `dts-platform` 容器。
4. 等待健康检查通过，确认启动日志无 Liquibase、默认湖或 Secret 解密错误。
5. 验证服务启动后的管理镜像已自动同步为 `/biadmin`，且 Props 只有系统标记和公开连接字段。

若第 3～5 步失败，立即用回退标签恢复原镜像并仅重建 `dts-platform`。

## 精确数据修复

1. 最终代码在 `ApplicationReadyEvent` 阶段从 Admin 权威配置自动同步
   `infra_data_source`，因此不再对镜像行执行人工 DML。
2. 启动健康后确认固定镜像已经为 `/biadmin`、全库镜像旧 URL 计数为 0。
3. 执行同目录的 `repair-findemo1.sql`。脚本先验证固定镜像，再以精确 ID、
   名称、目标 ID 和两个旧 JSON 路径为前置条件，在单事务内只更新任务 12。
4. 脚本要求影响恰好一行；否则抛错并回滚。禁止使用字符串全局替换。

## 发布后验证

- 固定镜像 JDBC 为 `/biadmin`，系统管理标记存在，`secure_props` 保持密文存储。
- 全库 `infra_data_source.jdbc_url` 不再包含 `/biadmin1`。
- 全库 `ingestion_task.destination_config` 不再包含 `biadmin1`。
- `findemo1` 的目标 ID 不变，两处 JDBC 快照均为 `/biadmin`。
- `biadmin` 数据库可连接；不创建 `biadmin1`。
- `dts-platform` 容器健康，默认湖状态接口不再把同步失败伪装为可用。
- 由用户在页面手工执行或重建任务并验收，不执行浏览器 E2E。

## 回滚

1. 停止新的入湖任务变更。
2. 从备份 CSV 恢复上述两条完整记录；恢复前分别验证：
   - 固定镜像仍为 `/biadmin`、系统管理标记存在且 `last_modified_by='system'`。
   - 任务 12 的 `last_modified_by='ops-default-lake-repair'`。
   发现任一记录漂移则停止自动回滚。
3. 将回退镜像重新标记为 `dts-platform:1.0.0`，仅重建 `dts-platform`。
4. 确认容器恢复健康并记录回滚原因。

## 本次执行证据

- 聚焦回归：42 个测试，0 失败、0 错误、0 跳过。
- Java 最终复审：无阻断项。
- 回退镜像：`dts-platform:rollback-default-lake-20260729-2238`，
  镜像 ID `6210741521a5...`。
- 最终镜像：`dts-platform:1.0.0`，镜像 ID `747bcd53acc2...`。
- 数据备份：`/var/backups/dts/default-lake-20260729-2238`，目录权限 `0700`，
  两个 CSV 权限 `0600`，均已用临时表回读验证为一行。
- 启动后固定镜像由代码自动同步到 `/biadmin`，任务 12 由
  `repair-findemo1.sql` 单事务修复。
- 最终状态：容器 `healthy`、重启次数 0；镜像和任务中的 `biadmin1` 计数均为 0；
  `biadmin` 上 `SELECT 1` 成功；Props 敏感键计数为 0。
