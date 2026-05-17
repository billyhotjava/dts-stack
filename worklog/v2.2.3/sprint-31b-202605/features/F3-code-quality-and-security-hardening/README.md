# F3: 代码质量与安全 hardening

**优先级**: P0
**状态**: READY

## 目标

修复 Sprint-31A RX 阶段性提交里出现的反模式与安全口子：setter 注入、未版本化 endpoint、未授权 silent 200、observability 缺失、lifecycle 状态映射错位。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | setter 注入改构造器注入（`@Lazy` 解循环依赖） | P0 | READY | - |
| T02 | policy endpoint 版本化 | P1 | READY | Sprint-31A RX/T05 |
| T03 | 未授权 policy 调用返回 HTTP 403 | P0 | READY | T02 |
| T04 | policy observability（warn + metric） | P0 | READY | F1/T02 |
| T05 | lifecycle 状态映射对齐既有数据 | P0 | READY | F1/T06 |

## 完成标准

- [ ] `IndicatorService` / `ModelingSqlModelService` 全部回到构造器注入；循环依赖用 `@Lazy` 解。
- [ ] policy endpoint 路径携带 `/v1/`。
- [ ] policy 未授权返回 HTTP 403，不再 silent 200 + `"1=0"`。
- [ ] policy dataset 未命中时输出 warn + metric counter。
- [ ] code asset writer 不会把现有 ACTIVE 状态 model 误打成 `PENDING_GOVERNANCE`。
