# Sprint-37 集成测试与准入计划

## 目标

实测证明：入湖上传的 Excel/CSV 在宿主机磁盘恒为密文，宿主机（含 root）从文件系统目录无法查看明文；明文仅在 Addax 容器 tmpfs（内存）运行期短暂存在、作业即焚；且 Addax 入湖功能加密前后结果一致。

## 证据目录

| 证据 | 路径 | 状态 |
|------|------|------|
| 宿主机不可见验证 | `it/evidence/host-invisibility/` | READY |
| Addax 入湖回归 | `it/evidence/addax-regression/` | READY |
| 异常与边界 | `it/evidence/edge-cases/` | READY |
| 安全复审 | `it/evidence/security-review/` | READY |
| 加密样例 fixture | `it/fixtures/` | READY |

## 验收命令草案

```bash
# 后端单测（加密 + runner 解密）
cd source && ./mvnw -q -pl dts-ingestion -Dtest='*FileUploadServiceEncryption*,*AirflowDagService*' test
cd services/dts-airflow/runner && ./mvnw -q -Dtest='AddaxEnvRunnerTest' test
```

```bash
# 宿主机不可见实测（真实 compose，优先鲲鹏/麒麟）
bash worklog/v2.2.3/sprint-37-202606/it/verify-host-invisibility.sh
# 关键断言：
sudo strings "${STACK_ROOT}/services/dts-airflow/dags/uploads/"*.enc | grep -i "<已知关键字>" && echo FAIL || echo "PASS: 密文无明文关键字"
sudo grep -rl "<已知关键字>" "${STACK_ROOT}/services/dts-airflow/dags/" && echo FAIL || echo "PASS: bind 树零明文"
```

```bash
# 运行期明文确实只在容器内存 tmpfs（宿主机磁盘看不到）
docker exec <addax容器> sh -lc 'echo TMPDIR=$TMPDIR; ls -la $TMPDIR'   # 作业窗口内可见明文
# 同一时刻宿主机：
sudo ls -la "${STACK_ROOT}/services/dts-airflow/dags/uploads"           # 仅 .enc
```

```bash
# Addax 入湖回归（加密前后 ODS 结果一致）
bash worklog/v2.2.3/sprint-37-202606/it/verify-addax-regression.sh
```

```bash
# 异常与边界
bash worklog/v2.2.3/sprint-37-202606/it/verify-edge-cases.sh

# 门禁套件
tests/run_gates.sh
```

## 阻断条件（出现即不予准入）

- 宿主机（含 root）能从文件系统目录 `cat`/`strings`/`grep` 出上传文件明文。
- 作业前/中/后 bind 树（dags/uploads）出现任何明文 Excel/CSV。
- 无密钥即可从 `.enc` 读出明文内容（加密失效）。
- 密钥缺失或解密失败时退化为明文落盘 / 明文喂 Addax / 明文残留。
- Addax 入湖结果加密前后不一致（行数/字段/类型/内容偏差）。
- 明文 tmpfs 路径或密钥出现在 bind 日志、审计、异常消息中。
- security-reviewer 存在未闭合 CRITICAL/HIGH。

## 残余边界（已接受，随发布交付）

明文在 Addax 运行期存在于容器 tmpfs（内存），root 仍可经 `docker exec` 进运行中容器或 dump 进程内存读取。本专项达成口径为「宿主机文件系统目录不可见明文」，不含运行中容器内存防护（架构物理下限，详见 `features/F3-.../T05-release-admission.md`）。

## TDD 与影响分析约定

- 每个 task 先红后绿；runner/ingestion 纯 Java 单测优先，容器级实测归 F3。
- 改既有 symbol 前 `gitnexus_impact`，提交前 `gitnexus_detect_changes()`。
- Java 侧禁用 `Optional.get()`，统一 `orElseThrow()`。
