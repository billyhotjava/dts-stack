# P2-03 治理权限矩阵硬化（v2.2.1）

更新时间：2026-02-22（UTC）

## 角色矩阵（当前实现）

| 能力域 | 接口示例 | 维护角色（可写） | 普通员工（ROLE_EMPLOYEE） |
|---|---|---|---|
| 质量规则维护 | `POST /api/governance/quality/rules` | `ROLE_ADMIN / ROLE_OP_ADMIN / ROLE_INST_DATA_OWNER / ROLE_DEPT_DATA_OWNER / ROLE_INST_LEADER / ROLE_DEPT_LEADER` | 拒绝（403） |
| 质量运行触发 | `POST /api/governance/quality/runs` | 同上 | 拒绝（403） |
| 合规批次维护 | `POST/PUT/DELETE /api/governance/compliance/*` | 同上 | 拒绝（403） |
| 问题闭环写操作 | `POST/PUT /api/governance/issues*` | 同上 | 拒绝（403） |
| 公共码表写操作 | `POST/PUT/DELETE /api/governance/reference-codes*` | 同上 | 拒绝（403） |
| 治理查询接口 | `GET /api/governance/*` | 允许 | 允许（受部门/密级可见性过滤） |

## 双重校验结论

1. 注解层：治理写接口统一使用 `@PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)`。
2. 服务层：问题单链路包含 `ensureWritable`、部门与密级可见性校验（`IssueTicketService`）。
3. 本轮补齐：`POST /api/governance/quality/runs` 已补充写权限注解，避免普通角色触发巡检执行。

## 回归脚本

- 脚本：`worklog/v2.2.1/platform/governance/scripts/run-p2-03-permission-smoke.sh`
- 目标：
  - maintainer 写接口应为“非 401/403”
  - employee 写接口应为 `403`
  - 双角色读接口应为 `200`
- 运行示例：

```bash
API_BASE="http://127.0.0.1:18080" \
TOKEN_MAINTAINER="..." \
TOKEN_EMPLOYEE="..." \
ACTIVE_DEPT="D01" \
worklog/v2.2.1/platform/governance/scripts/run-p2-03-permission-smoke.sh --strict
```

## 风险与后续

1. 当前权限矩阵基于角色 + 服务层部门/密级过滤；尚未引入“治理功能级白名单”。
2. 建议在 P3 增补契约门禁：扫描新增写接口是否存在 `@PreAuthorize`。

