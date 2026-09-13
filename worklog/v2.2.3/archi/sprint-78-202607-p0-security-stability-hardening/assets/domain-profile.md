# Sprint-78 领域与数据画像（G0）

**用途**：domain-grounding 产物——本 Sprint 作用域的安全/运维域事实画像，全部条目可追溯到 sprint README 的勘察账本。

## 作用域

本 Sprint 不触碰业务数据模型，作用于四个安全/运维面：

| 面 | 现状事实 | 影响对象 |
|---|---|---|
| 用户初始口令 | 创建路径统一经审批 applyCreate，口令为常量 `"sa"` + `temporary=false`（账本#1/#2）；重置路径已支持 temporary（账本#4） | 全部新建账号的最终用户；authadmin（口令交付对象） |
| TLS 私钥 | 3 个 p12 受 Git 跟踪、口令 `"password"`（账本#7/#8）；tls profile 休眠，TLS 由 Traefik 终止（账本#9）；部署期 certs 链已存在（账本#10） | 交付物分发面；启用 tls profile 的部署 |
| PostgreSQL | 单实例 10 库（账本#11）；无定时备份，仅升级快照（账本#12）；`services/dts-pg/data` 为客户状态红线 | 全部业务的可恢复性 |
| Hetu 代理 | 9 组路由代理到外部 `hetu.upstream:7778`（账本#13/#14）；移除决策 2026-04 已做；前端已有向 `/bi` 的重定向（账本#15） | 使用旧 BI 入口的用户；外部暴露面 |

## 业务不变量（本 Sprint 必须保持）

1. 三员分离与审批流不变：F1 只改口令生成与交付，不改审批链路与角色边界。
2. 默认数据湖约束不受影响：本 Sprint 不涉及数据源/数据集选择。
3. TLS 终止现状不变：Traefik 仍是默认终止点；tls profile 仅作为显式启用的可选项。
4. 客户持久化状态不动：`services/dts-pg/data`、`.env` 既有键值、Keycloak realm 数据均不删除（devops-runbook）。
5. 离线可执行：全部变更与验证不依赖外网（release-offline-upgrade）。

## 词汇表（本 Sprint 内统一）

- **一次性口令**：仅在审批执行响应内存中出现的初始口令明文，不落库、不可再查。
- **delivered 元数据**：审计中 `initialPasswordDelivered: true/false` 的记录，不含口令。
- **hetu 代理面**：compose labels + file provider 中全部 hetu-* router/service/middleware 与 host alias 的合称。
- **轮换**：作废 Git 历史泄露的旧私钥，以部署期重新生成的新证书材料替代。
