# F1: 上传文件 AES-GCM 加密存储

**优先级**: P0
**状态**: READY

## 目标

让 `dts-ingestion` 在接收入湖上传的 Excel/CSV 时，用 AES-GCM 加密为 `{name}.enc` 落盘，宿主机磁盘恒为密文；表头解析在内存完成不落明文；密钥缺失时 fail-fast 拒绝，禁止明文回退。

## 协议依据与缺口

- 协议条款：2.3.2.10（安全保密）+ BMB17.1/17.2-2024 机密级涉密数据存储加密。
- 当前缺口（证据见 `../../assets/upload-file-exposure-investigation.md`）：`FileUploadService.java:89` `Files.copy` 明文落盘，bind mount + `chmod o+r`（compose:200-201）导致宿主机（含 root）可直接查看明文。

## 加密文件格式契约（与 F2 共享，不可单方变更）

- 文件名：`{原存储名}.enc`（如 `a1b2c3d4_data.xlsx.enc`）。
- 布局：`[IV:12B][AES-GCM ciphertext + 16B tag]`（IV 置文件头，自包含）。
- 算法：`AES/GCM/NoPadding`，tag 128bit，密钥 `DTS_INFRA_ENCRYPTION_KEY`。
- 元数据：`keyVersion`/`originalName`/明文`sha256`/`fileSize` → `IngestionTask.sourceConfig`。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 上传加密落盘（randomIv + AES-GCM，IV 文件头布局，写 .enc） | P0 | READY | — |
| T02 | 加密元数据写入 sourceConfig（iv/keyVersion/sha256/originalName/size） | P0 | READY | T01 |
| T03 | 表头解析改内存解密（解密到内存流后 POI/CSV 解析，不落明文临时文件） | P0 | READY | T01 |
| T04 | 密钥缺失 fail-fast（禁明文回退）+ cleanup/rollback 适配 .enc | P0 | READY | T01 |
| T05 | 单元/集成测试（加密往返、密钥缺失拒绝、表头解析、cleanup） | P0 | READY | T01-T04 |

## 完成标准

- [ ] 上传产物为 `{name}.enc`，宿主机 `cat` 仅得密文；IV 在文件头，可被 F2 解密。
- [ ] iv/keyVersion/sha256/originalName/fileSize 落 sourceConfig，供 F2 路由与校验。
- [ ] 表头解析全程内存，无明文临时文件产生。
- [ ] `DTS_INFRA_ENCRYPTION_KEY` 缺失时上传被拒（明确错误），绝不明文落盘。
- [ ] `cleanupForTask` 正确删除 `.enc`。

## TDD 约定

- 每个 task RED→GREEN→REFACTOR；覆盖率 ≥80%，加密/密钥分支覆盖。
- 改既有 symbol（FileUploadService）前 `gitnexus_impact`；Java 侧禁用 `Optional.get()`，用 `orElseThrow()`。
