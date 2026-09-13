# T04: 修复 cheap compile 发现的破坏

**优先级**: P0
**状态**: DONE_NOT_REQUIRED
**依赖**: T01-T03

## 目标

T01-T03 发现的 compile error / type mismatch，集中在本任务内修复并归档原因，避免分散在多个 Feature 里造成漂移。

## 背景

cheap compile 的价值在于「一次性把跨模块漂移暴露并修完」。修复必须最小改动、不引入新功能；任何「顺便重构」都拒绝并新开 task。

## 技术设计

1. 收集 T01-T03 evidence 中的 ERROR 列表。
2. 按模块分类：
   - dts-platform 内部：直接改调用方
   - platform → metrics contract 不一致：以 platform 为事实源，metrics 跟随
   - webapp ↔ backend：以 backend 为事实源，OpenAPI 重新生成
3. 每个修复都必须在 commit message 引用 cheap compile evidence line 号。
4. 修复后重跑 T01-T03 直到 exit 0。

## 影响范围

- 视 evidence 而定；不允许引入新业务逻辑。

## 验证

- [x] T01-T03 全部 exit 0
- [x] 无 compile error，无需修复 commit

## 完成标准

- [x] 全部 cheap compile 通过。
- [x] 本轮未发现破坏，无修复 commit。
