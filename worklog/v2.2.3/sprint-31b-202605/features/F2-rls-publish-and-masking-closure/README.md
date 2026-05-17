# F2: RLS publish gate 与 column masking 收口

**优先级**: P0
**状态**: IN_PROGRESS

## 目标

把 Sprint-31A RX/T05 已经在 preview 阶段落地的 platform RLS predicate 注入，扩展到 publish 阶段，并加入 column masking 与 audit；让 metric-pack manifest 的 `security.apply_rls=true` 不再是"自我声明已生效"。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | publish gate 复用 policy contract | P0 | READY | Sprint-31A RX/T05 |
| T02 | column masking 加入 policy endpoint 与 SQL 生成器 | P0 | DONE | T01 |
| T03 | manifest `apply_rls=true` 降级为声明 | P0 | DONE | T01 |
| T04 | RLS 注入 audit | P0 | READY | T01, T02 |
| T05 | live IT 验证多 dialect | P1 | READY | T01-T04 |

## 完成标准

- [ ] dbt publish 阶段调用同一 `/api/internal/v1/asset-permission/policy`，preview 与 publish 口径完全一致。
- [x] column masking 进入候选 dbt SQL 与 schema.yml；masked dimension 使用 `dts_mask(...)` 候选宏，masked metric input 直接阻断 preview。
- [x] manifest `security.apply_rls` 仅作为声明，运行时强制由 platform policy 决定；true+空策略会失败，false+platform 策略会 override 并告警。
- [ ] 每次注入 predicates / masked columns 都写入 audit；可按 `pack_id` / `asset_id` / `actor` 查询。
- [ ] 至少 PostgreSQL 与 Doris 两个 dialect 的 SQL golden file 存在并通过 dry-run。
