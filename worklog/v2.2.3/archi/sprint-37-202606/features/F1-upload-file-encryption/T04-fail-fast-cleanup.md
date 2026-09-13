# T04: 密钥缺失 fail-fast + cleanup 适配 .enc

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

机密级禁止明文回退：`DTS_INFRA_ENCRYPTION_KEY` 未配置时，上传直接拒绝（不退化为明文）；并使清理/回滚正确删除 `.enc` 文件。

## TDD 测试先行（RED）

- 扩展 `FileUploadServiceEncryptionTest`。
- 断言：当 `InfraSettingsCryptoService.isEncryptionReady()` 为 false 时，`handleUpload` 抛明确异常（如 `IllegalStateException("加密密钥未配置，禁止上传涉密文件")`），且 uploads 目录无任何文件落地。
- 断言：`cleanupForTask` 能删除 `.enc`（sourceConfig.hostPath 指向 `.enc`），回滚后文件不存在。
- 对比基线：现有 `InfraSettingsCryptoService.init()`（L35-43）在密钥缺失时仅 `LOG.warn` 并明文持久化——本 task 要求上传链路**不沿用**该回退。

## 技术设计（GREEN）

- `handleUpload` 入口增加 `if (!crypto.isEncryptionReady()) throw ...`（fail-fast，先于写盘）。
- `cleanupForTask`（L268-288）适配：hostPath 现为 `.enc`，逻辑不变（按 sourceConfig.hostPath 删），补测试覆盖。
- 启动期可选校验：若入湖文件上传功能启用但密钥缺失，记录醒目告警（不阻断服务启动，但阻断上传）。

## 影响范围

- `source/dts-ingestion/.../service/etl/FileUploadService.java`（`handleUpload` 增 fail-fast；`cleanupForTask` 验证，改既有 symbol → `gitnexus_impact`）
- `source/dts-ingestion/.../service/etl/rollback/DataRollbackService.java`（确认 `.enc` 清理路径，`:255`）
- 测试：`FileUploadServiceEncryptionTest`

## 验证

- [ ] 密钥缺失时上传被拒，零文件落地，无明文回退。
- [ ] `.enc` 文件可被 cleanup/Level3 回滚正确删除。

## 完成标准

- [ ] 不存在「密钥缺失 → 明文落盘」的退化路径。
