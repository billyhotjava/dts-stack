# T05: F1 单元/集成测试与覆盖率

**优先级**: P0
**状态**: READY
**依赖**: T01-T04

## 目标

汇总 F1 测试，确保加密存储链路覆盖率 ≥80%（加密/密钥/解析/清理分支覆盖），并产出可复用的测试夹具供 F2/F3 引用。

## TDD 测试先行（RED）

测试已在 T01-T04 先行编写；本 task 补齐边界与集成：

- 加密往返：xlsx/csv/大文件（>50MB 评估）/空文件/超长文件名/中文名 各一例，解密后与原文件逐字节一致。
- 密钥版本：`encKeyVersion` 写入正确；不同 keyVersion 的解密路由（为 F2 预留）。
- 异常：密钥缺失拒绝、损坏密文（截断/改字节）解密抛错不泄露明文。
- 完整性：明文 sha256 与解密后摘要一致。
- 夹具：导出一个「加密样例文件 + 元数据」fixture 到 `it/fixtures/`，供 F2 runner 测试与 F3 IT 复用。

## 技术设计（GREEN）

- JUnit5 + AssertJ；密钥用测试固定值（`@BeforeEach` 注入测试 `InfraSettingsCryptoService`）。
- JaCoCo 覆盖率门禁纳入 `tests/run_gates.sh`（ingestion 模块）。

## 影响范围

- `source/dts-ingestion/src/test/java/.../service/etl/FileUploadServiceEncryptionTest.java`
- `worklog/v2.2.3/sprint-37-202606/it/fixtures/`（加密样例 + 元数据）
- `tests/run_gates.sh`（覆盖率门禁）

## 验证

- [ ] F1 链路覆盖率 ≥80%，加密/密钥/解析/清理分支覆盖。
- [ ] 损坏密文不导致明文泄露或进程崩溃。

## 完成标准

- [ ] F1 全绿，fixture 可供 F2/F3 复用。
