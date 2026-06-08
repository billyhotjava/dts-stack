# Sprint-37: 数据入湖上传文件加密专项（宿主机磁盘明文消除）

**时间**: 2026-06
**状态**: PLANNING
**类型**: Security / Compliance / Implementation Plan（dts-ingestion + services/dts-airflow/runner + dts-airflow DAG + docker compose）
**目标**: 针对**数据入湖任务中上传的 Excel/CSV 文件**，实现「磁盘恒为密文 + 明文仅在容器内存(tmpfs)运行期短暂存在」，使**宿主机用户（含 root）无法从文件系统目录查看明文**，且**完全不改变 Addax 入湖功能**。以 TDD 驱动（RED→GREEN→REFACTOR）。

## 立项背景（现状调查结论）

经代码与部署核查（见 `assets/upload-file-exposure-investigation.md`）：

1. 入湖上传文件由 `FileUploadService.handleUpload()` **明文落盘**（`Files.copy`，仅算 SHA-256，无加密）。
2. 落盘目录 `{jobDir}/uploads/` 是 **bind mount**（宿主机 `{STACK_ROOT}/services/dts-airflow/dags/uploads`），dts-ingestion 写、Airflow/Addax 读，三方共享同一宿主机目录。
3. 部署 init 显式 `chmod o+r`（`docker-compose-app.yml:200-201`），文件**全员可读**。
4. **无任何机制会自动删除这些文件**：容器 `auto_remove` 是 bind 不删宿主机文件；`--force-recreate` 不删 bind；`dts-reset` 的 `rm -rf` 列表（`dwh ods ads exchange __pycache__`）**不含 uploads**；正常入湖成功不清理（仅 Level 3 `FULL_CASCADE` 回滚才删）。
5. 结论：一旦现场使用 Excel/CSV 上传入湖，明文将**长期、world-readable** 驻留宿主机，违反协议 2.3.2.10 机密级（BMB17.1/17.2-2024）涉密数据存储要求。

> 本专项独立于 Sprint-36（数据安全与机密级合规整改），因其为现场实锤、范围聚焦的单点修复，优先级最高，单独成 sprint。

## 安全目标与防护边界

**目标（必达）**：宿主机文件系统目录（`ls`/`cat` 任意磁盘路径，**含 root**）查看 uploads 只得到密文；明文不落任何宿主机磁盘。

**已知残余边界（必须如实记录）**：因 Addax 必须读取明文才能入湖，明文在 Addax 容器运行期间存在于容器 **tmpfs（内存）**。理论上 root 仍可通过 `docker exec` 进入运行中的 Addax 容器、或 dump 进程内存看到明文——这是「Addax 必须读明文」架构下的物理下限，无法消除。本专项把明文暴露面压缩到「仅运行中容器的内存、仅作业执行窗口、容器 `auto_remove` 即焚」，**满足「root 不能从宿主机目录查看文件」的口径**；若需连内存也防护，须更换为支持密文直读的入湖引擎，超出本专项范围。

## 方案设计

```
上传(dts-ingestion)                  入湖执行(Airflow→Addax 容器)
─────────────────                    ──────────────────────────
MultipartFile                         AddaxEnvRunner.run()
  └ randomIv + AES-GCM 加密             ├ 读 job.json（引用 *.enc 密文路径）
  └ 写 uploads/{name}.enc(密文)         ├ 【新增】解密 *.enc → tmpfs 明文(内存)
  └ iv/keyVersion/sha256→sourceConfig  ├ 改写 job 路径指向 tmpfs 明文
  └ 表头解析：内存解密后 POI(不落明文)   ├ 调 addax.sh 入湖（Addax 无感）
                                       └ finally 擦除 tmpfs 明文（+容器即焚）
```

- **复用现成加密**：`InfraSettingsCryptoService`（dts-ingestion，AES/GCM/NoPadding，`encrypt/decrypt/randomIv/currentKeyVersion`），密钥 `DTS_INFRA_ENCRYPTION_KEY`。
- **解密注入点**：`AddaxEnvRunner`（`services/dts-airflow/runner/`，dts 自有 wrapper）在 `renderTemplate` 后、`runAddax` 前插入解密；复用其现成 `finally Files.deleteIfExists` 擦除模式。
- **tmpfs**：DAG 的 `DockerOperator` 给 Addax 容器挂 tmpfs 并把 `TMPDIR` 指向它（`AddaxEnvRunner.writeTempJob` 已用 `TMPDIR`），临时 job 与解密明文都进内存。
- **Addax 零改动**：它从 job.json 指定路径读明文，不感知加解密。

## 加密文件格式契约（F1 写入、F2 读取，必须一致）

- 密文文件名：`{原存储名}.enc`（如 `a1b2c3d4_data.xlsx.enc`）。
- 文件布局：`[IV: 12 bytes][AES-GCM ciphertext + 16 bytes tag]`（IV 置于文件头，自包含）。
- 算法：`AES/GCM/NoPadding`，tag 128 bit，密钥来自 `DTS_INFRA_ENCRYPTION_KEY`（两侧同源）。
- 元数据：`keyVersion`、`originalName`、明文 `sha256`、`fileSize` 写入 `IngestionTask.sourceConfig` JSON（用于校验与密钥版本路由）。
- 密钥未配置：**机密级禁止明文回退**——上传与解密均 fail-fast 拒绝，不得退化为明文存储（区别于 `InfraSettingsCryptoService` 现有的明文告警回退）。

## 分层决策（改动边界）

| 层 | 改动 | 不动 |
|----|------|------|
| dts-ingestion | `FileUploadService` 加密落盘 + 表头内存解密 + cleanup 适配 `.enc` | 上传 REST 契约、源类型判定 |
| runner | `AddaxEnvRunner` 增解密→tmpfs→改写路径→擦除 | Addax 二进制、job 模板渲染逻辑 |
| DAG/编排 | `AirflowDagService` 注入 tmpfs mount + 密钥环境；去掉 uploads 的 `o+r` | DockerOperator 其余配置、bind 结构 |
| 密钥 | 复用 `DTS_INFRA_ENCRYPTION_KEY` + `keyVersion`，两侧一致校验 | 不新造密钥体系 |

## Feature 顺序

| ID | Feature | 优先级 | Task 数 | 状态 | 依赖 |
|----|---------|--------|---------|------|------|
| F1 | 上传文件 AES-GCM 加密存储 | P0 | 5 | READY | — |
| F2 | Addax 运行期 tmpfs 解密 | P0 | 5 | READY | F1（加密格式契约） |
| F3 | 安全验证、回归与 IT 准入 | P0 | 5 | READY | F1, F2 |

**统计**: READY=15, IN_PROGRESS=0, DONE=0, BLOCKED=0

## 完成标准

- [ ] 上传的 Excel/CSV 以 AES-GCM 加密为 `{name}.enc` 落盘，宿主机 `cat` 仅得密文；明文 sha256/iv/keyVersion 入 sourceConfig。
- [ ] 表头解析在内存完成（解密后 POI/CSV 解析），不产生任何明文临时文件。
- [ ] `AddaxEnvRunner` 运行期把密文解密到 tmpfs 明文供 Addax 读取，作业结束（含失败）擦除明文。
- [ ] DAG 给 Addax 容器挂 tmpfs 且 `TMPDIR` 指向它；密钥环境注入；compose 去掉 uploads 的 `o+r`。
- [ ] 密钥缺失时上传与解密均 fail-fast，无明文回退。
- [ ] Addax 入湖功能回归：加密前后入湖结果（行数/字段/类型）一致。
- [ ] IT 证据：宿主机（含 root）`ls`/`cat` uploads 仅见密文；磁盘无明文残留；Addax 入湖成功。
- [ ] 全部改动 TDD，覆盖率 ≥80%，加解密/密钥路径分支覆盖；security-reviewer 无 CRITICAL/HIGH。

## 非目标 / 已知边界

- 不覆盖非上传来源（数据库 JDBC 源不产生上传文件，不在范围）。
- 不防护「root 通过 docker exec 进运行中容器 / dump 进程内存」读取 tmpfs 明文（架构物理下限，见上「残余边界」）。
- 不实现密钥轮换的自动重加密（仅预留 keyVersion 字段与路由；批量重加密排 Backlog）。
- 不改 Addax 二进制、不引入新入湖引擎。
- 不触碰 Sprint-36 的 6 个 feature。

## TDD 约定

- 每个 task RED→GREEN→REFACTOR，测试先行；覆盖率 ≥80%，加解密/密钥/擦除路径要求分支覆盖。
- `AddaxEnvRunner` 复用其现有 `AddaxEnvRunnerTest` 模式扩展解密用例（纯 Java 单测，无需容器）。
- 改既有 symbol 前 `gitnexus_impact`；Java 侧禁用 `Optional.get()`，用 `orElseThrow()`。

## 评审机制

1. **加密评审**（F1）：算法/IV/tag/密钥来源正确，无明文回退，表头解析不落明文。
2. **解密链路评审**（F2）：tmpfs 真在内存、明文路径不落 bind、finally + auto_remove 双重擦除、解密失败不泄露明文。
3. **安全验证评审**（F3）：root 视角实测仅见密文、磁盘零明文残留、Addax 入湖回归通过；security-reviewer 终审。

## 相关材料

- 现状调查报告: `worklog/v2.2.3/sprint-37-202606/assets/upload-file-exposure-investigation.md`
- 上游差距分析: `worklog/v2.2.3/sprint-36-202606/assets/protocol-gap-analysis-v3.md`
- 加密服务: `source/dts-ingestion/.../service/infra/InfraSettingsCryptoService.java`
- Addax wrapper: `services/dts-airflow/runner/src/main/java/com/yuzhi/dts/addax/AddaxEnvRunner.java`
- DAG 生成: `source/dts-ingestion/.../service/etl/AirflowDagService.java`
- 集成测试: `worklog/v2.2.3/sprint-37-202606/it/README.md`
