# Sprint-72 Go / No-Go 证据清单

**当前状态**: NO-GO_PRODUCTION_ROLLOUT（编码与自动化验证已完成）

本清单是统一验证阶段的唯一放行入口。编码阶段不填写“通过”，每项必须链接到真实命令输出、
数据库查询、容器状态或 Chrome 95 截图。

| 证据域 | 必需证据 | 路径 | 当前结论 |
|--------|----------|------|----------|
| 实现范围 | GitNexus changed symbols / affected flows | `evidence/go-no-go/gitnexus-detect-changes.md` | REVIEWED_CRITICAL |
| 静态质量 | `git diff --check` | `evidence/go-no-go/diff-check.txt` | PASS |
| focused tests | F1～F8 后端与前端定点测试 | `evidence/focused/` | PASS |
| Feature 组合 | 接入、传播、消费、大屏、生命周期、迁移 | `evidence/feature/` | PASS_AUTOMATED |
| 数据库迁移 | 空库 + 现有库 Liquibase、触发器与回滚边界 | `evidence/migration/` | PASS |
| 真实运行 | PostgreSQL、JDBC、API、Excel/CSV、dbt、OpenLineage | `evidence/runtime/` | PASS_ISOLATED / PARTIAL_PRODUCTION |
| 权限矩阵 | 人员密级、旧链接、缓存、管理员旁路 | `evidence/classification-matrix/` | PASS_AUTOMATED / PARTIAL_PRODUCTION |
| 销毁安全 | 临时销毁/恢复/双人永久销毁隔离证明 | `evidence/destruction-proof/` | PASS |
| Chrome 95 | 接入、台账、生命周期、大屏编辑/发布/访问 | `evidence/chrome95/` | PARTIAL |

## 放行规则

- 任一证据域为 `PENDING` 或 `FAILED`，结论必须为 **NO-GO**。
- 存量 dry-run 存在 `BLOCKED_DOWNGRADE`、未知密级或双读差异时不得冻结旧写入口。
- 永久销毁仅在隔离测试副本验证，不得以用户业务数据做演练。
- 所有证据齐全后，再将本文件状态改为 `GO`，并同步 Sprint/Feature/Task 状态为 `DONE`。

## 本轮边界

- F1～F8 编码和自动化测试已完成。
- 本轮未重建或发布容器；现网 dry-run 数据不能代表最终源码。
- 下一放行阶段必须先部署最终源码，再重新 dry-run 并处理阻断/双读差异，最后补生产外部链与真实身份矩阵。
