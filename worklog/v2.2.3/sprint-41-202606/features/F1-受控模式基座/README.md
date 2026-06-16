# F1: 受控模式基座（governanceMode + 切换骨架 + 存量兼容）

**优先级**: P0
**状态**: READY

## 目标
引入模型级 `governanceMode`（CONTROLLED|PERMISSIVE）作为绞杀者开关：受控模型走 F2/F3 的严格路径，存量/PERMISSIVE 模型行为字节不变。F2、F3 均依赖本 feature 的切换点。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | governanceMode 模型属性 + Liquibase changelog（默认值保证存量兼容） | P0 | DONE | - |
| T02 | 受控模式判定与分流骨架（buildMetricExpression / 校验入口） | P0 | READY | T01 |

## 完成标准
- [ ] 模型实体/DTO 含 `governanceMode`，新模型默认 CONTROLLED、存量默认 PERMISSIVE。
- [ ] `SemanticModelingService` 有统一受控判定点，F2/F3 可挂载；PERMISSIVE 路径与现状字节不变。
- [ ] Liquibase 变更可前滚，存量数据不受影响。
