# F3: API schema snapshot 与 stg 映射

**优先级**: P0
**状态**: DRAFT
**依赖**: F1, F2

## 目标

建立 API 响应到 schema snapshot 和 stg 映射的产品化流程，包括 record path、字段推断、类型映射、stg 字段编辑、schema drift 策略和目录/血缘命名。ODS 按 Sprint-18 约束只保存原始 record 和 `_dts_*` 技术字段。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | JSON record path 与响应解析器 | P0 | DRAFT |
| T02 | Schema inference 与类型映射 | P0 | DRAFT |
| T03 | Resource 到 ODS 原样落地契约 | P0 | DRAFT |
| T04 | Schema drift 策略与兼容报告 | P0 | DRAFT |
| T05 | 目录、血缘与 dbt source 命名规则 | P1 | DRAFT |

## 完成标准

- [ ] Preview 样本能生成 schema snapshot，保留字段顺序、类型、nullable、样本和路径。
- [ ] ODS 契约能稳定生成原始 record 表和 dbt source，且不做字段改名/类型标准化。
- [ ] Schema drift 有明确 notify、block、append-to-stg 策略。
- [ ] stg 字段敏感标记能传递到治理链路。
