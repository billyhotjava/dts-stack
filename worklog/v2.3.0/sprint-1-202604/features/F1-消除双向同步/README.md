# F1: 消除双向同步 (Phase 1)

**优先级**: P0
**状态**: READY

## 目标
将 dts-admin 从"双向同步"改为"只读 Keycloak + 直写 Keycloak"，消除本地 → Keycloak 的回写链路，Keycloak 成为唯一写入源。

## 当前双向同步链路

```
人员导入 → PersonProfile → AdminKeycloakUser → Keycloak (回写)
组织同步 → OrganizationNode → Keycloak groups (回写)
Keycloak → AdminKeycloakUser (定时拉取快照)
```

## 目标架构

```
人员导入 → 直接写 Keycloak (Admin API) → 触发缓存刷新
组织同步 → 直接写 Keycloak groups → 触发缓存刷新
Keycloak → 按需查询 + TTL 缓存 (替代定时全量同步)
```

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 人员导入直写 Keycloak | P0 | READY | v2.2.3 F1 |
| T02 | 组织同步直写 Keycloak groups | P0 | READY | v2.2.3 F1 |
| T03 | 取消定时全量快照同步 | P0 | READY | T01, T02 |
| T04 | display-names 接口改为查 Keycloak | P1 | READY | T03 |

## 完成标准
- [ ] admin 不再有 "写本地 → 同步到 Keycloak" 的链路
- [ ] 人员导入直接操作 Keycloak Admin API
- [ ] 组织同步直接操作 Keycloak Group API
- [ ] 定时快照任务移除或降级为缓存预热
