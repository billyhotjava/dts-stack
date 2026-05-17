# F3: 代码质量与安全 hardening

**优先级**: P0
**状态**: DONE

## 目标

修复 Sprint-31A RX 阶段性提交里出现的反模式与安全口子：setter 注入、未版本化 endpoint、未授权 silent 200、observability 缺失、lifecycle 状态映射错位。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | setter 注入改构造器注入（`@Lazy` 解循环依赖） | P0 | DONE | - |
| T02 | policy endpoint 版本化 | P1 | DONE | Sprint-31A RX/T05 |
| T03 | 未授权 policy 调用返回 HTTP 403 | P0 | DONE | T02 |
| T04 | policy observability（warn + metric） | P0 | DONE | F1/T02 |
| T05 | lifecycle 状态映射对齐既有数据 | P0 | DONE | F1/T06 |

## 完成标准

- [x] `IndicatorService` / `ModelingSqlModelService` / `ApiCatalogService` 全部回到构造器注入；当前未新增 `@Lazy`，完整 Spring context 验证留给 F5。
- [x] policy endpoint 路径携带 `/v1/`。
- [x] v1 policy 未授权返回 HTTP 403；legacy path 保留 silent 200 + `"1=0"` 兼容旧客户端。
- [x] policy dataset 未命中时输出 warn + metric counter；v1 严格策略缺 dataset 时返回 HTTP 422，legacy path 继续 200 兼容旧客户端。
- [x] code asset writer 不会把现有 ACTIVE 状态 model 误打成 `PENDING_GOVERNANCE`；公共 mapper 已覆盖 Indicator / SQL Model / API / DataStandard / Glossary，历史 backfill 已接入 Liquibase。
