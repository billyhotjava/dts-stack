# T04: 解密失败/密钥缺失处理

**优先级**: P0
**状态**: READY
**依赖**: T01-T03

## 目标

密钥缺失、解密失败（tag 校验失败/密文损坏/keyVersion 不匹配）时，作业以明确错误码失败退出，绝不回退到明文路径、不把密文当明文喂给 Addax、不残留任何明文。

## TDD 测试先行（RED）

- 扩展 `AddaxEnvRunnerTest`。
- 断言：`DTS_INFRA_ENCRYPTION_KEY` 缺失 + job 含 `.enc` → `run()` 返回非零错误码（复用 `EX_CONFIG=78`），不调用 `runAddax`，无明文产出。
- 断言：密文被改字节（GCM tag 失败）→ 返回错误码，stderr 给出不含明文/不含密钥的诊断信息。
- 断言：keyVersion 不匹配（job 元数据 vs env）→ 失败，提示密钥版本不符。
- 断言：任一失败路径执行后，TMPDIR 无明文残留（finally 已擦除）。

## 技术设计（GREEN）

- 在 `run()` 解密前置校验：env 密钥存在性、keyVersion 一致性；缺失/不符直接返回 `EX_CONFIG`。
- 解密异常（`AEADBadTagException` 等）捕获 → 返回错误码 + 安全诊断（不打印明文片段、不打印密钥）。
- 复用 finally 擦除确保失败路径也清理。
- 不实现任何「解密失败则用密文/明文继续」的回退。

## 影响范围

- `services/dts-airflow/runner/src/main/java/com/yuzhi/dts/addax/AddaxEnvRunner.java`
- `services/dts-airflow/runner/src/test/java/com/yuzhi/dts/addax/AddaxEnvRunnerTest.java`

## 验证

- [ ] 密钥缺失/解密失败/版本不符均失败退出，无明文回退。
- [ ] 诊断信息不泄露明文与密钥。
- [ ] 失败路径无明文残留。

## 完成标准

- [ ] 不存在「失败 → 明文泄露/绕过加密」的退化路径。
