# Sprint-21 验收证据归档

本目录用于归档 Connector Center 工业级验收的现场证据。每次发布候选版本至少保留一次完整记录，输出文件建议随版本号或日期归档到发布包。

## 必填证据

| 证据 | 来源 | 判定 |
|---|---|---|
| 数据库源 E2E smoke | `it/scripts/connector-center-smoke.sh` 输出目录 | discover、precheck、ODS apply、建任务、执行、补数和 observability 均有响应 |
| 文件源 E2E smoke | `it/scripts/file-source-smoke.sh` 输出目录 | CSV/Excel prepare、parse、errors、summary 均有响应，`rowCount > 0` |
| 凭据脱敏审计 | `it/scripts/credential-redaction-audit.sh` 输出目录 | 用户侧 API 与 smoke/audit 输出中不包含 `DTS_SECRET_SENTINEL` |
| 方言验证矩阵 | `dialect-validation-matrix.md` | PostgreSQL/MySQL/Oracle/SQL Server/DM8 至少完成 metadata discover 基础字段 |
| 发布门禁 | Maven/pnpm/jq/git diff 输出 | 相关模块构建与审计 catalog JSON 校验通过 |

## 推荐归档结构

```text
it/evidence/20260430-rc1/
  db-source-smoke/
  file-source-smoke/
  credential-redaction/
  release-gate.txt
  acceptance-record.md
```

## 通过口径

- 用户侧接口只返回脱敏数据源详情；运行时明文凭据仅允许 `service:*` 内部服务 principal 携带 `X-DTS-Service-Token` 调用 `runtime-detail`。
- 审计 payload 只记录数据源 ID、任务 ID、动作、状态、错误摘要和影响范围，不记录 password/token/accessKey 等明文字段。
- 数据库源和文件源都必须进入接入运行中心或文件接入台账，且能够关联到 ODS/source/任务或文件批次。
- 现场未具备某类商业数据库时，必须记录原因、替代验证方式和补测计划，不能直接标记为已完成。
