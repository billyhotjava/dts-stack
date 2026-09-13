# T02: 凭据加密存储与secretRef解析

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

客户 API 凭据（token/apiKey/basic密码/clientSecret/签名密钥）与 JDBC 密码同等待遇：dts-platform 侧加密落库进数据源 `secrets`；dts-ingestion 进程内解密使用；`secretRef` = secrets map 键名（不再是 env 变量名）。

## 技术设计

- 复用 `InfraSettingsCryptoService`（AES-GCM + `InfraSecurityProperties.keyVersion`）加密存储，对齐文件上传加密链路。
- `IngestionSourceResolver` 新增 `resolveApiInfo(UUID dataSourceId)`：返回 `ResolvedApiSource(baseUrl, defaultHeaders, authConfig, decryptedSecrets)`，参考既有 `resolveJdbcInfo(:80-89)` 与 `applySecrets(:191)` 的实现形态。
- 明文生命周期：仅在执行器内存中存活，用完即弃；禁止写日志/落盘/进 DAG 文件/进 Airflow Variable。
- 凭据轮换：复用 keyVersion 滚动重加密机制；OAuth2 access_token 仅内存缓存（F2-T03）。
- 审计：记录「任务 × 凭据使用时间」，不记明文。

## 影响范围

- `service/etl/IngestionSourceResolver.java`（新增 API 解析分支）
- dts-platform 数据源 secrets 写入/脱敏回显
- 删除 `_secret(os.environ)` 语义依赖（实际删除在 F5-T02）

## 验证

- [x] 单测：`resolveApiInfo` 返回 baseUrl/defaultHeaders/auth/decryptedSecrets，API 分支不走 JDBC reader config（`IngestionSourceResolverTest`）
- [x] 单测：jwtLogin 动态 secret placeholder 生成掩码与版本 metadata（`ApiSecretMetadataServiceTest`）
- [x] 数据源 secrets 落库为密文；GET 接口仅回 secretRef+掩码
- [x] 日志/异常栈无明文断言

## 完成标准

- [x] API 凭据链路与 JDBC 密码链路同构，安全口径一致

## 进展记录

- 2026-06-12: `InfraManagementServiceTest#createDataSource_apiTypeStoresSecretsViaSecretServiceAndReturnsMaskedMetadata` 覆盖 API 数据源创建时明文只进入 `InfraSecretService.applySecrets`，实体仅保留密文 `secureProps` 与 `__apiSecretMeta` 掩码 metadata，普通 DTO 不回显明文。
- 2026-06-12: `InfraManagementServiceTest#recordConnectionTest_apiDataSourceRequestRedactsPropsAndSecrets` 覆盖连接测试日志 payload 的 API props/secrets 递归脱敏，`auth.value`、`token` 等明文字段不进入 `infra_connection_test_log.request_payload`。
- 2026-06-12: 已跑 `(cd source && ./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-platform -Dtest=ApiDataSourceSupportTest,ApiSecretMetadataServiceTest,InfraSecretServiceTest,InfraManagementServiceTest,InfraDataSourceResourceTest test)`。
- 2026-06-12: live 发现 `dts-platform` 容器未绑定 `dts.platform.infra.encryption-key`，已补 `application.yml` 绑定与 app/dev/legacy compose 透传；重建平台后通过 `it/scripts/api-secret-security.sh` 验证 API 数据源真实创建、DB 密文落库、用户/analytics 详情不回显、runtime-detail 仅有效 `dts-ingestion` 服务 token 可解密，证据见 `it/evidence/api-secret-security-20260612.txt`。
