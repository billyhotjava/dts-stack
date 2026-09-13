# 发布安全计划 (Gate G3)

**变更类型**: schema
**风险等级**: 低

## 1. 迁移策略

| 阶段 | 内容 | 本次是否包含 | rollback 段 |
|---|---|---:|---|
| Expand | 放宽 `metadata_standard.source_system` 的非空约束，支持标准内容包暂不代填客户来源系统 | 是 | 有，已在 PostgreSQL 17.4 演练 |
| Migrate | 无数据回填 | 否 | - |
| Contract | 无字段或能力删除 | 否 | - |

## 2. 兼容性

| 消费方 | 证据 | 是否受影响 | 处置 |
|---|---|---:|---|
| 标准包应用服务 | `StandardPackageApplyServiceTest`、`StandardPackageImportServiceTest` | 是 | 允许包内数据元保持空来源，回归测试通过 |
| 数据元手工新增/编辑接口 | `MetadataStandardUpsertRequest.sourceSystem` 保留 `@NotBlank` | 否 | 页面/API 手工维护仍要求客户填写来源系统 |
| 既有数据与查询 | GitNexus `detect_changes` 为 LOW，未识别受影响执行流 | 否 | 旧代码继续写入非空值，查询契约未改变 |

## 3. 回填策略

- dry-run：不涉及回填；本次只删除列的非空约束。
- 分批规模：不适用。
- 未命中处置：不自动猜测或代填客户业务元数据。

## 4. 回滚

- 回滚命令：对该 changeset 执行 Liquibase rollback 1。
- 演练结果：2026-07-29 在隔离 PostgreSQL 17.4 中验证升级、空值写入、回滚保护、清理后恢复非空约束，1/1 通过。
- 不可逆部分：无数据删除。若标准包已写入 `source_system IS NULL` 的数据元，迁移会拒绝回滚；应先通过标准包历史回滚该批次，或由客户补全真实来源系统后再回滚，禁止代填虚构值。

## 5. 部署顺序与影响面

1. 构建新的 `dts-platform` 制品。
2. 仅重建 `dts-platform` 容器，启动时执行 Liquibase expand 迁移。
3. 验证容器健康、changeset 已执行且 `source_system` 可空。
4. 保留原预检记录，由用户重新点击“确认应用”。

影响模块：`source/dts-platform` 的数据标准持久化与标准包应用链路；无前端变更。
