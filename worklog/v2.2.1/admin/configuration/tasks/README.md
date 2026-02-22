# Admin 配置中心任务清单（v2.2.1）

> 目标：把可运行期参数从 `.env` 迁移到 `dts-admin` 可视化管理，并保留编排/镜像/底座参数在运维层。
> 状态定义：`planned` / `in-progress` / `done` / `blocked`

## 阶段总览

| 阶段 | 目标 | 状态 |
|---|---|---|
| P0 | 参数分层、数据模型、基础能力打通 | done |
| P1 | 高风险参数治理（SSO/PKI/MDM）与运维可用性 | done |
| P2 | 护栏、漂移检测、回归门禁 | done |

## 强制顺序（最小可执行）

1. `P0-01` 参数目录与迁移边界
2. `P0-02` 密钥与脱敏策略
3. `P0-03` 配置元数据模型扩展
4. `P0-04` Admin 集成配置控制台统一入口
5. `P1-01` 重启生效标记与操作引导
6. `P1-02` OIDC/PKI 安全编辑闭环
7. `P1-03` MDM 网关参数纳管
8. `P1-04` 配置分组细化与环境参数自动纳管
9. `P2-01` 非运行期参数防误改护栏
10. `P2-02` `.env` 与 DB 配置漂移检测
11. `P2-03` 发布回归矩阵与切换预案

## 文件索引

- `P0-01-env-catalog-and-migration-boundary.md`
- `P0-02-secret-masking-and-storage-policy.md`
- `P0-03-system-config-metadata-extension.md`
- `P0-04-admin-integration-settings-unification.md`
- `P1-01-restart-aware-config-ux.md`
- `P1-02-oidc-pki-safe-editing-flow.md`
- `P1-03-mdm-gateway-config-visualization.md`
- `P1-04-config-grouping-and-env-sync.md`
- `P2-01-non-runtime-config-guardrails.md`
- `P2-02-env-db-drift-detection.md`
- `P2-03-release-gate-and-rollback-plan.md`
- `status-board.md`

## 管理规则

- 每个任务卡必须包含：范围、子任务、验收标准、风险与回滚。
- P0 完成前不进入 P1/P2 代码实现。
- 每个任务落地后必须补充：影响文件、回归命令、结果证据。
