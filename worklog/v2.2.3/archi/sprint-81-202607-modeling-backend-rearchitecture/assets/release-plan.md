# 审计单租户参数退役发布安全计划（Gate G3）

**变更类型**：部署配置能力收敛  
**风险等级**：中（两个服务需要按 Ingestion → Platform 顺序重建；Platform 通过固定的 `SPRING_APPLICATION_JSON` 兼容桥同时支持旧镜像和新源码）

## 1. 迁移策略

| 阶段 | 内容 | 本次是否包含 | rollback 段 |
| --- | --- | --- | --- |
| Expand | 无 schema、数据或 API 变更 | 否 | 不适用 |
| Migrate | 将审计所有者固定为内部 `SINGLE_TENANT/default`，保留 outbox `tenant_id` | 是 | 回滚至发布前镜像 |
| Contract | 从 Compose、`init.sh` 和运行时代码退役 `AUDIT_TENANCY_MODE`、`AUDIT_TENANT_ID` | 是 | 发布前镜像仍兼容旧容器环境 |

## 2. 兼容性

| 消费方 | 证据 | 是否受影响 | 处置 |
| --- | --- | --- | --- |
| `dts-platform` 审计 outbox | `AuditTenantResolver`、基础 `application.yml` | 是 | 内部固定 `SINGLE_TENANT/default`；继续拒绝 `MULTI_TENANT` |
| `dts-ingestion` 凭据恢复审计 | `IngestionSecretRestoreAuditService`、`application.yml` | 是 | 改读内部 `auditing.tenant-id=default` |
| app/dev/legacy Compose | `tests/test_audit_single_tenant_compose.sh` | 是 | 三套 Compose 在旧变量缺失时均须成功渲染，不注入旧变量，并固定内部 `SINGLE_TENANT/default` 配置 |
| `init.sh` | `bash -n init.sh` | 是 | 不再生成或校验旧变量 |

## 3. 数据策略

- 不修改数据库结构或存量审计数据。
- `tenant_id` 列、`__legacy_unscoped__` 历史证据、事件归属校验和按租户重放隔离全部保留。
- 无数据回填、无不可逆数据动作。

## 4. 回滚

发布前已运行的精确镜像已保留：

- `dts-platform:rollback-audit-single-tenant-20260802` → `sha256:a442755886a9084a7396d56df07bd51a5ffa40e093792b31e9e964735c93e6f3`
- `dts-ingestion:rollback-audit-single-tenant-20260802` → `sha256:ec7fbb6c0da4d0baa75b762c2ee9cceb766cc40f87d099065f55572a6ecdc603`

回滚时把 `.env` 中 `IMAGE_DTS_PLATFORM`、`IMAGE_DTS_INGESTION` 临时指向上述标签，并使用受控的旧 Compose 配置重建这两个服务；回滚后检查 `/management/health` 与中央审计投递。

**演练结果**：已使用上述精确镜像依次重建 Ingestion、Platform；两服务均恢复 `healthy`，容器环境中不存在退役变量，Platform 读取到固定的 `SINGLE_TENANT/default`。未执行反向线上回滚演练，Gate G3 仍记为 `GAP`。  
**不可逆部分**：无。

## 5. 部署顺序与影响面

1. 已用发布前精确镜像先重建 `dts-ingestion`，确认健康且旧变量不存在。
2. 已用同一 Platform 镜像和固定内部 Spring 配置重建 `dts-platform`，确认健康且无旧占位符错误。
3. 只读核验 `platform_audit_outbox`：`default` 租户证据仍存在，状态为 `SENT=48`、`DEAD=8`；本次无 schema 或数据写操作。
4. 后续从已归档、可复现的变更集构建新镜像；不得直接从包含其他未归档改动的共享工作区构建同标签生产镜像。新镜像已内置相同单租户配置，兼容桥可在所有部署镜像升级完成后单独评审退役。

影响模块：`dts-platform`、`dts-ingestion`、app/dev/legacy Compose、`init.sh`。Sprint-83 不在范围内。
