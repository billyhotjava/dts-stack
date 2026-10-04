# 交付基线核验 (Gate G0)

**状态**: PENDING — 由 F0/T01 执行后回填
**为什么必须先做**: domain-dts 已登记「浏览器验收基线（登录/DNS）长期不稳（Sprint-61~64）」，本 Sprint 有 3 个带 UI 的 Feature，基线不过则它们全部停在 DRAFT。

## 待核验项（不得留空，不得写"应该可以"）

| # | 项 | 期望 | 实测 | 结论 |
|---|----|------|------|------|
| 1 | 运行实例 | `docker compose -f docker-compose-app.yml up` 后核心服务健康 | | PENDING |
| 2 | 登录 | 本地账号可登录；若现场仅 PKI，记录该形态（影响 F1/T03 是阻断还是告警）| | PENDING |
| 3 | 数据安全页可达 | `pages/security/data-security.tsx` 打开并截图（F2/F3 的挂载点）| | PENDING |
| 4 | 资产台账可达 | F3 扫描范围选择的上游 | | PENDING |
| 5 | Chrome95 executable | 路径可用；不可用则明确 BLOCKED 及影响面 | | PENDING |
| 6 | actuator prometheus 端点 | 四服务 `/management/prometheus` 均可访问（F4 前置）| | PENDING |

## 结论
- [ ] 全部 PASS → Gate Registry G0 置 PASS，F2/F3/F4 由 DRAFT 转 READY
- [ ] 任一 FAIL → 登记阻断原因，受影响 Feature 保持 DRAFT，**不得绕过验证开始编码**
