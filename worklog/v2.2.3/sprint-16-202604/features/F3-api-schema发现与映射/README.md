# F3: API schema 发现与 ODS 映射

**优先级**: P0
**状态**: DRAFT
**依赖**: F1, F2

## 目标

建立 API 响应到 ODS 表结构的产品化流程，包括 record path、字段推断、类型映射、字段编辑、schema drift 策略和目录/血缘命名。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | JSON record path 与响应解析器 | P0 | DRAFT |
| T02 | Schema inference 与类型映射 | P0 | DRAFT |
| T03 | Resource 到 ODS 表映射契约 | P0 | DRAFT |
| T04 | Schema drift 策略与兼容报告 | P0 | DRAFT |
| T05 | 目录、血缘与 dbt source 命名规则 | P1 | DRAFT |

## 完成标准

- [ ] Preview 样本能推断可编辑字段。
- [ ] ODS 映射能稳定生成目标表和 dbt source。
- [ ] Schema drift 有明确 notify、block、append column 策略。
- [ ] 字段敏感标记能传递到治理链路。

