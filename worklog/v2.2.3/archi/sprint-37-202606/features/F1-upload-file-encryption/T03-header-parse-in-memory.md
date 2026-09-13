# T03: 表头解析改内存解密（不落明文）

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

上传后解析表头（Excel/CSV）的逻辑改为：从密文解密到**内存流**再交 POI/CSV 解析，全程不产生明文临时文件。

## TDD 测试先行（RED）

- 扩展 `FileUploadServiceEncryptionTest`。
- 断言：对加密上传的 xlsx，`parseExcelHeaders` 仍返回正确的列名/类型（与明文解析结果一致）。
- 断言：对加密上传的 csv，`parseCsvHeaders` 返回正确表头。
- 断言：解析过程中 uploads 目录及 TMPDIR **不出现任何明文文件**（解析前后目录快照只含 `.enc`）。

## 技术设计（GREEN）

- 当前 `parseExcelHeaders`(L154) / `firstSheetName`(L118) 用 `WorkbookFactory.create(filePath.toFile())` 直接读磁盘文件——加密后该文件是密文，必须改为：解密到 `byte[]`/`ByteArrayInputStream` → `WorkbookFactory.create(inputStream)`。
- `parseCsvHeaders`(L182) 已用 `InputStream`，改为传入解密后的内存流。
- 抽取私有 `decryptToMemory(Path encPath, byte[] iv)` 复用 `InfraSettingsCryptoService.decrypt`；iv 从文件头读取。
- 注意 POI 大文件内存占用，必要时限制表头解析仅读前若干行/首 sheet（现已是首 sheet + header/sample 两行）。

## 影响范围

- `source/dts-ingestion/.../service/etl/FileUploadService.java`（`parseExcelHeaders`/`firstSheetName`/`parseCsvHeaders` 改内存流，改既有 symbol → `gitnexus_impact`）
- 测试：`FileUploadServiceEncryptionTest`

## 验证

- [ ] 加密文件表头解析结果与明文一致。
- [ ] 解析全程零明文临时文件。

## 完成标准

- [ ] 表头解析不破坏「磁盘恒密文」不变量。
