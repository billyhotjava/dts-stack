# F9: LLM 接口预留

**优先级**: P2
**状态**: READY

## 目标
定义 IndicatorSuggestionProvider 接口和 NoOp 实现，为 v2.3.0 LLM 接入预留扩展点。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | SPI 接口定义 | P2 | READY | F3/T02 |
| T02 | NoOp 实现 + 集成点 | P2 | READY | T01 |

## 完成标准
- [ ] 接口定义清晰，3 个扩展方法
- [ ] NoOp 实现不影响现有流程
- [ ] DbtIndicatorGenerator 中有条件调用 Provider
