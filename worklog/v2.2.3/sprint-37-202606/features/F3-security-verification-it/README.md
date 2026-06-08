# F3: 安全验证、回归与 IT 准入

**优先级**: P0
**状态**: READY

## 目标

以实测证明本专项达标：宿主机（含 root）从文件系统目录只能看到密文、磁盘零明文残留；同时 Addax 入湖功能不回归（加密前后入湖结果一致）。security-reviewer 终审后准入。

## 协议依据与缺口

- 协议条款：2.3.2.10 机密级 + 2.3.3-12 测试完备性。
- 本 feature 为质量门禁，验证 F1（加密存储）+ F2（运行期解密）共同达成「宿主机目录不可见明文」目标与「Addax 无感」约束。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | root 不可见 + 磁盘零明文残留 实测验证 | P0 | READY | F1, F2 |
| T02 | Addax 入湖功能回归（加密前后结果一致） | P0 | READY | F1, F2 |
| T03 | 异常与边界（密钥缺失/解密失败/大文件/并发/失败残留） | P0 | READY | F1, F2 |
| T04 | security-reviewer 全量复审 | P0 | READY | T01-T03 |
| T05 | 发布准入 checklist + 残余边界登记 | P0 | READY | T01-T04 |

## 完成标准

- [ ] 实测：宿主机 root `ls`/`cat` uploads 与 dags 树仅见 `.enc` 密文；作业前/中/后扫描磁盘无明文 Excel/CSV。
- [ ] 实测：Addax 入湖加密前后行数/字段/类型一致，DAG 正常成功。
- [ ] 异常路径不泄露明文、不残留明文、不绕过加密。
- [ ] security-reviewer 无 CRITICAL/HIGH。
- [ ] 残余边界（运行期 tmpfs 内存明文可被 root docker exec 读取）在准入文档中明确记录并被接受。

## TDD 约定

- 端到端实测在真实 compose（优先信创鲲鹏/麒麟环境）执行并归档证据。
- 改既有 symbol 前 `gitnexus_impact`，提交前 `gitnexus_detect_changes()`。
