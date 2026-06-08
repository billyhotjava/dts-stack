# T01: 上传加密落盘（AES-GCM + IV 文件头）

**优先级**: P0
**状态**: READY
**依赖**: —

## 目标

`FileUploadService.handleUpload` 落盘时，用 AES-GCM 加密上传字节流为 `{storedName}.enc`，IV 置于文件头，磁盘不再出现明文。

## TDD 测试先行（RED）

- 新增 `FileUploadServiceEncryptionTest`（`dts-ingestion/src/test/java/.../service/etl/`）。
- 断言：上传一个已知内容的 xlsx/csv，落盘文件名以 `.enc` 结尾；读回文件头 12B 为 IV、其后为密文；用同密钥 `decrypt(cipher, iv)` 还原字节与原文件逐字节相等（GCM 往返）。
- 断言：落盘文件内容**不包含**原文件可识别明文片段（如 xlsx 的 `PK\x03\x04` ZIP 魔数、csv 表头串）。
- 断言：两次上传同一文件，IV 不同（randomIv 每次新生成）。

## 技术设计（GREEN）

- 复用 `InfraSettingsCryptoService`（`encrypt(byte[],iv)`/`randomIv()`，AES/GCM/NoPadding），注入到 `FileUploadService`。
- 改 `FileUploadService.java:88-92`：不再 `Files.copy` 明文；改为读入字节 → `iv=randomIv()` → `cipher=encrypt(bytes,iv)` → 写 `[iv][cipher]` 到 `hostPath=uploadsDir.resolve(storedName + ".enc")`。
- 大文件以分块/流式 GCM 处理避免一次性全量内存（评估 `CipherOutputStream` + `GCMParameterSpec`）。
- `containerPath` 同步加 `.enc` 后缀（供 F2 在容器内定位密文）。

## 影响范围

- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/FileUploadService.java`（改既有 symbol `handleUpload`，需 `gitnexus_impact`）
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/infra/InfraSettingsCryptoService.java`（复用，必要时补流式 API）
- `source/dts-ingestion/src/test/java/.../service/etl/FileUploadServiceEncryptionTest.java`（新增）

## 验证

- [ ] 落盘为 `.enc`，文件头 12B IV + GCM 密文，可解密还原。
- [ ] 密文不含明文魔数/表头。
- [ ] 每次上传 IV 唯一。

## 完成标准

- [ ] 入湖上传不再产生任何明文文件。
