# T02: 状态和 blocker 契约回归

**优先级**: P0  
**状态**: DONE
**依赖**: F3/T03

## 目标

确保规划、标准和维度模型的状态不会因为缺少上下文而误报完成。

## 技术设计

- vitest 行为回归：`resolvePlanningStatus`、维度候选门禁、session/URL 不一致裁决、跨存储失效降级的组合矩阵。
- 与 Sprint-62 verification 语义对齐：unknown 一律"待确认"，不出纯绿。

## 影响范围

- `warehousePlanningContext` 与门禁纯函数的 vitest 增补
- 既有 journey 测试全量回归（白名单扩展影响面）

## 验证

- [x] ready/missing/blocked 组合全部覆盖。
- [x] 版本失效和 storage 异常有明确恢复动作。
