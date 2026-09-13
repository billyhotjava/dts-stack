# T02: job.json 路径改写 + finally 擦除

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

`AddaxEnvRunner.run()` 把 job.json 中引用的 `.enc` 密文路径改写为解密后的 tmpfs 明文路径，Addax 读明文；作业结束（成功或失败）在 finally 擦除明文。

## TDD 测试先行（RED）

- 扩展 `AddaxEnvRunnerTest`。
- 断言：给定引用 `/opt/addax/jobs/uploads/x.xlsx.enc` 的 job.json，`run()` 后传给 `runAddax` 的 job 内路径被替换为 tmpfs 明文路径（如 `${TMPDIR}/addax-plain-*.xlsx`）。
- 断言：`runAddax` 返回后（mock 成功与失败两种），tmpfs 明文文件**已被删除**（复用现有 `finally Files.deleteIfExists` 模式，L51-53）。
- 断言：原 `.enc` 不被修改、不被删除（密文留存由 ingestion 侧 cleanup 负责）。
- 断言：非加密 job（无 `.enc` 引用，向后兼容）走原路径不受影响。

## 技术设计（GREEN）

- 在 `run()` 的 `renderTemplate` 后、`writeTempJob`/`runAddax` 前插入：扫描 rendered job 中的 `.enc` 文件引用（reader 文件路径字段）→ 逐个 `decryptToTmp` → 字符串替换为明文路径。
- 扩展现有 `finally`（L51-53）：除删 tempFile 外，删除所有本次解密产生的明文 tmpfs 文件（收集到 list，finally 遍历 deleteIfExists）。
- 路径识别尽量精确（匹配 sourceConfig 注入的 `_filePath`/`_containerPath` 对应值，避免误替换）。

## 影响范围

- `services/dts-airflow/runner/src/main/java/com/yuzhi/dts/addax/AddaxEnvRunner.java`（`run` 主流程，改既有 symbol → `gitnexus_impact`）
- `services/dts-airflow/runner/src/test/java/com/yuzhi/dts/addax/AddaxEnvRunnerTest.java`

## 验证

- [ ] job 路径被改写为 tmpfs 明文；Addax 读到明文。
- [ ] 成功/失败两种结局都擦除明文；`.enc` 不受影响。
- [ ] 非加密 job 向后兼容。

## 完成标准

- [ ] 明文生命周期 = 单次作业窗口，结束即焚。
