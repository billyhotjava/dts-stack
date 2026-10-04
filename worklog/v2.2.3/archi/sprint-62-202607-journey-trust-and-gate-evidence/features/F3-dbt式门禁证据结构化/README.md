# F3: dbt式门禁证据结构化

**优先级**: P0
**状态**: DONE

## 目标
把 development/evidence 阶段的证据从"跳转链接"升级为 dbt build 式的结构化门禁：落标覆盖率、编译、测试、运行四项 checks，各自三态，聚合出 verdict，直通验收包。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 门禁证据数据模型与mock契约 | P0 | DONE | - |
| T02 | 阶段卡与相关页面渲染门禁卡 | P0 | DONE | T01 |
| T03 | 验收包接入结构化门禁 | P0 | DONE | T01 |

## 完成标准
- [ ] 门禁模型四项 checks，各 ready|missing|blocked + detail + evidenceUrl，缺 API 标 apiName。
- [ ] 聚合 verdict：任一 blocked→fail；有 missing→warn；全 ready→pass。
- [ ] 工作台 development/evidence 阶段卡内嵌门禁摘要；验收包按 checks 聚合并导出。
- [ ] source-contract 覆盖组合与聚合逻辑。
