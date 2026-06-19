# F2: 治理与安全

**优先级**: P1
**状态**: READY

## 目标

收编旁路区「治理 Govern」与「安全 Security」两域：治理中心、权限审计、质量规则（管理视角）、数据安全、数据集访问审批。按「先占位后充实」——每页至少**可点入口 + mock 列表骨架**。指标治理视角已在 S6 阶段④指标收口，本 Feature 仅保留治理入口骨架，不重复实现指标能力。命名对齐现网 `GovernanceCenterPage` / `PermissionAuditPage` / `QualityRulesPage` / `data-security` / `DatasetAccessApprovalPage`。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| [T01](./T01-治理中心与权限审计.md) | 治理中心 + 权限审计 | P1 | READY | S1 |
| [T02](./T02-数据安全与数据集访问审批.md) | 数据安全 + 数据集访问审批 | P1 | READY | S1 |

## 完成标准

- [ ] 治理域（`GovernanceCenter`/`PermissionAudit`/`QualityRules` 管理）与安全域（`data-security`/`DatasetAccessApproval`）所有页面在左轨「平台·治理/安全」下有可点入口、路由可达。
- [ ] 每页至少呈现 CompactTable mock 列表骨架（默认 10 条/页，状态点 token）。
- [ ] 全部经分域 mock service 取数，返回 `Promise<Result<T>>`，`VITE_USE_MOCK` 开关下可注入/重置样例。
- [ ] 占位页不报错、不断路由；纳入全局搜索 ⌘K 索引（F4-T02）。
- [ ] 指标治理视角不在本 Feature 重复实现（已在 S6 阶段④收口）。
