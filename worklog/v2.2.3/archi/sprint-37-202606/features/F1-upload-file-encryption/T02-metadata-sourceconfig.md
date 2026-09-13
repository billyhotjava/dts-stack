# T02: 加密元数据写入 sourceConfig

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

把解密所需的元数据（keyVersion、原文件名、明文 sha256、fileSize）写入 `IngestionTask.sourceConfig`，供 F2 解密时路由密钥版本与校验完整性。

## TDD 测试先行（RED）

- 扩展 `FileUploadServiceEncryptionTest`。
- 断言：`FileUploadResult` 含 `keyVersion`（取 `InfraSettingsCryptoService.currentKeyVersion()`）、`originalName`、明文 `sha256`、`fileSize`。
- 断言：sha256 是**明文**的摘要（解密后校验用），非密文摘要。
- 断言：落库的 sourceConfig JSON 含上述字段（key 命名约定：`encKeyVersion`/`encOriginalName`/`plainSha256`/`plainSize`/`encrypted=true`）。

## 技术设计（GREEN）

- 扩展 `FileUploadService.FileUploadResult`（record，L51-61）增 `keyVersion`、`encrypted` 标志；`sha256` 改为对**明文**计算（当前 `sha256(hostPath)` 是对落盘内容算的，加密后需在加密前对明文算）。
- 调用方（写 sourceConfig 处）把这些字段持久化；约定 key 名供 F2 读取。
- `_filePath`/`_containerPath`/`_originalName` 等既有 key（`AddaxJobService.java:235`）保持兼容，新增 `_encrypted`/`_encKeyVersion`。

## 影响范围

- `source/dts-ingestion/.../service/etl/FileUploadService.java`（`FileUploadResult` 扩展、sha256 改明文）
- `source/dts-ingestion/.../service/etl/AddaxJobService.java`（sourceConfig key 透传，改既有 symbol → `gitnexus_impact`）
- `source/dts-ingestion/.../web/rest/FileUploadResource.java`（返回新字段）
- 测试：`FileUploadServiceEncryptionTest`

## 验证

- [ ] sourceConfig 含 keyVersion/originalName/plainSha256/plainSize/encrypted。
- [ ] sha256 为明文摘要，可用于 F2 解密后完整性校验。

## 完成标准

- [ ] F2 仅凭 sourceConfig + .enc 即可完成解密与校验。
