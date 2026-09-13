# F3：资产语义与统计投影

**优先级**：P0

**状态**：CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E

**证据**：`../../assets/implementation-evidence-20260810.md`

## 目标

将所有稳定真实关系纳入唯一资产身份，拆分来源/登记/状态并用有界统计投影支撑域导航。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 实现资产纳管、正交状态与统计投影 | CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E | F0/T01（生产容量与 E2E 仍阻塞） |

## Feature DoD

- [ ] ADR-86-04/05/16/18/19 与 E2E-B 真实切片通过。
- [ ] CatalogAssetKey 仍是唯一物理资产身份；统计无请求内全量扫描。
