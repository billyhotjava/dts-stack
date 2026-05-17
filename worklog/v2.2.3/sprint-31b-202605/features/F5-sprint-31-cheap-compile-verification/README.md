# F5: Sprint-31 cheap compile 前置验证

**优先级**: P0
**状态**: READY

## 目标

Sprint-31B 完成所有代码改动后，立即跑 `mvn compile` 与 `pnpm tsc --noEmit`（不跑测试），把 Sprint-31A 阶段累计的跨模块签名漂移在 cheap stage 暴露并修复；完整 IT、build、容器重建仍统一留给 Sprint-32 最终阶段。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | `mvn -pl dts-platform compile` | P0 | READY | F1-F4 |
| T02 | `mvn -pl dts-metrics compile` | P0 | READY | F1-F4 |
| T03 | webapp `pnpm tsc --noEmit` | P0 | READY | F1-F4 |
| T04 | 修复发现的编译破坏 | P0 | READY | T01-T03 |

## 完成标准

- [ ] 三个 cheap compile 全绿。
- [ ] 任何 compile error 在本 Sprint 内修复并归档原因。
- [ ] evidence 写入 `it/evidence/cheap-compile/`。
