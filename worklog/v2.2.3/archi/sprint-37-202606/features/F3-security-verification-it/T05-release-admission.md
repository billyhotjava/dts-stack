# T05: 发布准入 checklist + 残余边界登记

**优先级**: P0
**状态**: READY
**依赖**: T01-T04

## 目标

汇总准入结论，登记已知残余边界，确认达标并可发布。

## 准入 checklist（RED — 全部勾选方可发布）

- [ ] F1：上传产物恒为 `.enc`，密钥缺失拒绝，表头解析不落明文（F1 全绿）。
- [ ] F2：runner 解密到 tmpfs、路径改写、即焚；DAG 注入 tmpfs/TMPDIR/密钥（F2 全绿）。
- [ ] F3-T01：宿主机 root 实测仅见密文，磁盘零明文残留。
- [ ] F3-T02：Addax 入湖加密前后结果一致。
- [ ] F3-T03：异常路径不泄露/不残留/不绕过。
- [ ] F3-T04：security-reviewer 无 CRITICAL/HIGH。
- [ ] 覆盖率 ≥80%（ingestion + runner），纳入 `tests/run_gates.sh`。
- [ ] `gitnexus_detect_changes()` 改动范围符合预期。
- [ ] 信创（鲲鹏/麒麟）环境实测通过并归档。

## 残余边界登记（必须随发布交付，不可隐藏）

- 明文在 Addax 容器运行期存在于 **tmpfs（内存）**；root 可通过 `docker exec` 进运行中容器或 dump 进程内存读取——此为「Addax 必须读明文」架构物理下限，本专项不消除。
- 防护达成口径：**宿主机文件系统目录（含 root 的 `ls`/`cat`/`grep`/离线取盘）不可见明文**。
- 如需消除内存明文暴露，须更换支持密文直读的入湖引擎（排 Backlog，超本专项范围）。
- 密钥轮换的批量重加密未实现（仅 keyVersion 路由），排 Backlog。

## 影响范围

- `worklog/v2.2.3/sprint-37-202606/it/README.md`（准入结论回填）
- `it/evidence/`（各 feature 证据汇总）

## 验证

- [ ] checklist 全勾。
- [ ] 残余边界写入交付文档。

## 完成标准

- [ ] Sprint-37 达标，可发布；边界透明可追溯。
