# T01: runner 解密核心（.enc → tmpfs 明文）

**优先级**: P0
**状态**: READY
**依赖**: F1-T01（加密格式契约）

## 目标

在 `AddaxEnvRunner` 增加解密能力：读取 `.enc`（F1 格式），用 AES-GCM 解密到 `TMPDIR`（tmpfs）下的明文文件，字节级与原文件一致。

## TDD 测试先行（RED）

- 扩展 `services/dts-airflow/runner/src/test/java/com/yuzhi/dts/addax/AddaxEnvRunnerTest.java`。
- 用 F1 的 fixture（或测试内同款加密）生成 `.enc`，断言 `decryptToTmp(encPath, key, keyVersion)` 还原字节 == 原文件。
- 断言：解密输出落在 `TMPDIR`，文件名不可预测（随机后缀），权限 0600。
- 断言：IV 从 `.enc` 文件头读取（前 12B），与 F1 写入一致；GCM tag 校验失败（改字节）时抛异常、不产出明文。

## 技术设计（GREEN）

- 新增工具类 `AddaxFileCrypto`（runner 内，或共享 dts-common）：`AES/GCM/NoPadding`，与 `InfraSettingsCryptoService` **字节级同款**（IV 12B、tag 128bit、密钥来自 env `DTS_INFRA_ENCRYPTION_KEY`）。
- `AddaxEnvRunner` 增 `decryptToTmp(Path enc, ...)`：读文件头 IV → 解密 → 写 `Files.createTempFile(TMPDIR, ..., 原扩展名)`，设 POSIX 0600。
- 密钥从 `env.get("DTS_INFRA_ENCRYPTION_KEY")` 取；keyVersion 校验（与 job/env 传入一致）。

## 影响范围

- `services/dts-airflow/runner/src/main/java/com/yuzhi/dts/addax/AddaxEnvRunner.java`（改既有 symbol `run`，需 `gitnexus_impact`）
- `services/dts-airflow/runner/src/main/java/com/yuzhi/dts/addax/AddaxFileCrypto.java`（新增）
- `services/dts-airflow/runner/src/test/java/com/yuzhi/dts/addax/AddaxEnvRunnerTest.java`

## 验证

- [ ] `.enc` 解密字节级还原；IV/tag 与 F1 一致。
- [ ] 明文落 TMPDIR、0600、随机名；tag 校验失败不产明文。

## 完成标准

- [ ] runner 解密与 F1 加密形成可往返的字节级契约。
