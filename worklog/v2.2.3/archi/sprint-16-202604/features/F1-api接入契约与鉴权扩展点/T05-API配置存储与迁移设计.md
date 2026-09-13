# T05: API 配置存储与迁移设计

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01, T02

## 目标

解决 API 配置比 JDBC 配置复杂、现有 props 字段容量和安全边界不足的问题。

## 范围

- 评估 `infra_data_source.props` 长度和类型是否迁移为 jsonb/clob。
- 设计 `data_source_secret` 或复用现有 secure props 的 secret reference 模型。
- 明确公共配置、敏感配置、运行时覆盖配置的落库位置。
- 设计迁移脚本、回滚策略和兼容读取逻辑。

## 已落地的存储变更

| changeset | 变更 | 状态 |
|---|---|---|
| `20260425_01_api_data_source_props_text.xml` | `infra_data_source.props` `varchar(2048)` → `text` | ✅ DONE |

注：选型未直接采用 `jsonb`，理由是当前 PG 版本 + `props` 跨 JDBC/File/API 复用，jsonb 强类型会破坏既有 `Map<String,String>` 反序列化。`text` 配合应用层 JSON parse 是最小风险路径。

## Secret 存储方案（决议为复用 `secureProps` blob + sidecar metadata，不新建表）

调研发现 platform 已有 `InfraSecretService`（AES/GCM）将多字段 secrets 整体加密落到 `infra_data_source.secure_props` blob。本批次（2026-04-27）决议在该机制之上加 sidecar：

- 明文 secrets：仍由 `InfraSecretService.applySecrets/readSecrets` 统一管理，落 `secure_props`/`secure_iv`/`secure_key_version` 三列。**不再单独建 `infra_data_source_secret` 表**。
- per-field metadata（providerId / fieldName / maskedDisplay / secretVersion / rotatedAt / status）：写入 `infra_data_source.props` 中保留键 `__apiSecretMeta`，由 `ApiSecretMetadataService` 维护；用户提交的 `props` 中携带该键会被 `ApiDataSourceSupport.normalizeProps` 强制清除。

理由：
- 不引入新表 → migration / rollback 风险最低；现有 JDBC 数据源完全不受影响。
- 加密路径已通过 prod 验证（`InfraSecretService` 已稳定运行）。
- 多 field 元数据是 sidecar 数据，不是事实数据；丢失后可由用户重新提交触发 v(prev+1) 轮换语义。

后续若要支持"历史 version 长期保留 + 跨 version 引用"，再单独评估 `infra_data_source_secret_version_history` 表（不在本批次范围）。

## `__apiSecretMeta` sidecar JSON 结构（落 `infra_data_source.props`）

```json
{
  "providerId": "apiKey",
  "fields": [
    {
      "fieldName": "value",
      "maskedDisplay": "sk***wxyz",
      "secretVersion": "v1",
      "rotatedAt": "2026-04-27T00:00:00Z",
      "status": "ACTIVE"
    }
  ]
}
```

- `fieldName` 来源于 ingestion 端 `ApiAuthProviderRegistry` 各 provider 的 sensitive field name（apiKey 的 `value`、bearerToken 的 `token`、basic 的 `password`、oauth2ClientCredentials 的 `clientSecret`、customSignature 的 `secret`、mtls 的 `certSecretRef`/`keySecretRef`）。
- `maskedDisplay` 由 `ApiSecretMetadataService.maskedDisplay` 计算：长度 <4 → `***`；<8 → 首字符+`***`；否则首 2+`***`+末 4。
- 同 fieldName 复写时 `secretVersion` 从 `v1` 起递增；providerId 切换时全部重置为 `v1`；未在本次提交的 field 保留旧 metadata（不递增 / 不更新 rotatedAt）。

## 拟用 changeset（仅用于后续 task；本 task 不再需要新建 secret 表）

| 拟用 changeset | 表 / 列 | 用途 |
|---|---|---|
| ~~`20260428_01_api_data_source_secret.xml`~~ | ~~新表 `infra_data_source_secret`~~ | **取消**：复用 `secure_props` blob + `props.__apiSecretMeta` |
| `20260428_02_api_resource_checkpoint.xml` | 新表 `ingestion_api_checkpoint` | F4/T04 cursor checkpoint，列见 F4/T04 |
| `20260428_03_api_execution_plan.xml` | 新表 `ingestion_execution_plan_snapshot`（可选，evt sourcing 时启用） | 留作 F4/T01 决策项 |

## 完成标准

- [x] API 配置不会被 2048 字符限制卡住 —— `props` 已扩到 `text`（changeset `20260425_01`）。
- [x] 密钥值不落普通配置字段 —— 平台侧 `ApiDataSourceSupport` 拒绝明文 token/secret 入 `props`；密钥实际由 `InfraSecretService` AES/GCM 加密落 `secure_props` blob；sidecar metadata 仅保留 maskedDisplay/version，不含明文。
- [ ] 老数据源读取不受影响 —— `text` 扩容兼容；`__apiSecretMeta` 仅对 API 类型出现，JDBC/File 数据源不受影响。需补 JDBC IT 回归用例（F2/T05）。
- [ ] 有 migration、rollback 和数据校验方案 —— `20260425_01` 含 `preConditions`，需补反向 `rollback`；本批次不再需要 `20260428_01` 新表，sidecar 是应用层逻辑无需 schema 变更。

## 实现进展 / 关联代码

- `source/dts-platform/src/main/resources/config/liquibase/changelog/20260425_01_api_data_source_props_text.xml` —— `props` text 化。
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/ApiSecretMetadata.java` —— sidecar 数据结构。
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/ApiSecretMetadataService.java` —— 计算 / 写入 / 读取 metadata；版本号递增；脱敏规则。
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/ApiDataSourceSupport.java` —— `normalizeProps` 强制清除用户提交的 `__apiSecretMeta`，避免污染。
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/InfraManagementService.java` —— `applyDataSource` 在 API 类型分支接入 metadata 计算；`getDataSourceDetail` 在 API 类型时清空明文 secrets，仅返回 `secretSummaries`。
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/infra/ApiSecretMetadataServiceTest.java` —— 10 个单测覆盖首次写入 / 版本递增 / provider 切换 / 字段省略 / 短 secret 脱敏 / props round-trip。
- 待办：JDBC 数据源 IT 回归用例（属 F2/T05）；`20260425_01` 反向 rollback 块；secret 轮换 REST 入口（在 F2/T04 第二批做）。

