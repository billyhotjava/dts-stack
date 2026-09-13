# F2: Addax 运行期 tmpfs 解密

**优先级**: P0
**状态**: READY

## 目标

让 Addax 入湖时能读到明文，但明文只在容器 **tmpfs（内存）** 短暂存在、作业结束即焚，宿主机磁盘始终只有 F1 产出的 `.enc` 密文。Addax 二进制零改动。

## 协议依据与缺口

- 协议条款：2.3.2.10 机密级；承接 F1 的「磁盘恒密文」，解决「Addax 必须读明文」的供给问题。
- 注入点（证据见 `../../assets/upload-file-exposure-investigation.md`）：Addax 由 dts 自有 wrapper `AddaxEnvRunner`（`services/dts-airflow/runner/`）启动，其 `run()` 在 `renderTemplate` 后、`runAddax` 前是天然解密插入点；`writeTempJob` 已用 `TMPDIR`。

## 加密文件格式契约（与 F1 共享）

读取 F1 约定：`{name}.enc` = `[verLen:1B][keyVersion][IV:12B][AES-GCM ciphertext+16B tag]`，`AES/GCM/NoPadding`，密钥 `DTS_INFRA_ENCRYPTION_KEY`。runner 从文件头读 keyVersion，与 env `DTS_INFRA_KEY_VERSION` 校验一致后解密（P0-3）。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | runner 解密核心（读 .enc → AES-GCM 解密 → tmpfs 明文，与 F1 同款实现） | P0 | READY | F1-T01 |
| T02 | job.json 文件路径改写指向 tmpfs 明文 + finally 擦除 | P0 | READY | T01 |
| T03 | AirflowDagService 注入 tmpfs mount + TMPDIR + 密钥环境 | P0 | READY | T01 |
| T04 | 解密失败/密钥缺失处理（作业失败，不泄露明文、不回退明文路径） | P0 | READY | T01-T03 |
| T05 | 单元/集成测试（runner 解密往返、擦除、DAG 渲染、与 F1 fixture 互通） | P0 | READY | T01-T04 |

## 完成标准

- [ ] `AddaxEnvRunner` 把 `.enc` 解密到 tmpfs 明文供 Addax 读取，作业结束（含失败）擦除。
- [ ] job.json 中文件路径被改写为 tmpfs 明文路径；密文路径不直接喂给 Addax。
- [ ] DAG 给 Addax 容器挂 tmpfs 且 `TMPDIR` 指向它；`DTS_INFRA_ENCRYPTION_KEY`/keyVersion 注入容器环境。
- [ ] 解密失败或密钥缺失时作业失败退出，无明文落 bind、无明文残留。
- [ ] 与 F1 加密的 fixture 解密往返一致。

## TDD 约定

- runner 为独立 maven 工程，纯 Java 单测（扩展 `AddaxEnvRunnerTest`），无需容器即可覆盖解密/擦除/路径改写。
- 改既有 symbol（`AddaxEnvRunner.run`、`AirflowDagService` DAG 模板）前 `gitnexus_impact`；禁用 `Optional.get()`。
- 加解密实现与 F1 同源：抽到 dts-common 或 runner 内置同款 AES-GCM（密钥/IV/tag 布局必须与 F1 字节级一致）。
