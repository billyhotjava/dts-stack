# Sprint-21 验收记录模板

**版本/镜像**:
**环境**:
**执行人**:
**执行时间**:

## 发布门禁

| 检查项 | 命令/证据 | 结果 |
|---|---|---|
| dts-platform 编译 |  |  |
| dts-ingestion 编译 |  |  |
| dts-common 编译 |  |  |
| platform-webapp 构建 |  |  |
| audit catalog JSON |  |  |
| `git diff --check` |  |  |

## 数据库源 Smoke

| 数据库 | 数据源 ID | 输出目录 | 结果 | 备注 |
|---|---|---|---|---|
| PostgreSQL |  |  |  |  |
| MySQL |  |  |  |  |
| Oracle |  |  |  |  |
| SQL Server |  |  |  |  |
| DM8 |  |  |  |  |

## 文件源 Smoke

| 样例 | 输出目录 | rowCount | errorCount | 结果 |
|---|---|---:|---:|---|
| `budget-upload.csv` |  |  |  |  |

## 凭据脱敏审计

| 检查项 | 输出目录/文件 | 结果 |
|---|---|---|
| 用户侧数据源列表无明文凭据 |  |  |
| 用户侧数据源详情无明文凭据 |  |  |
| 用户侧 `/detail` 无明文 `secrets` |  |  |
| 用户 token 访问 `runtime-detail` 被拒绝 |  |  |
| 伪造 `X-DTS-Service` 但无服务令牌访问 `runtime-detail` 被拒绝 |  |  |
| 审计日志导出 CSV 不含 `DTS_SECRET_SENTINEL` |  |  |
| smoke/audit 输出不含 `DTS_SECRET_SENTINEL` |  |  |

## 结论

- 是否准入发布:
- 遗留风险:
- 回滚预案确认:
