# F1: dbt meta 语义契约标准化

**优先级**: P1
**状态**: READY

## 目标
把 dbt schema.yml 已有的 `meta.semantic_type` 约定标准化并扩展为 SP-2 的语义富化契约：列级 `semantic_type ∈ dimension|metric|time` + `standard_code`、model 级 `grain`。这是 F2/F3/F4 的契约基准。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 定义 meta 契约规范 + 文档（semantic_type/grain/standard_code/time） | P1 | READY | - |
| T02 | dbt 侧约定落地（现有 semantic_type 补 grain/standard_code/time + 样例模型） | P1 | READY | T01 |

## 完成标准
- [ ] 文档明确：列 `meta.semantic_type ∈ {dimension, metric, time}`、列 `meta.standard_code`、model `meta.grain: [col,...]`。
- [ ] `services/dts-dbt/models/dws/semantic/schema.yml` 等样例补全新 meta，可作 F2/F3 测试夹具。
- [ ] 向后兼容：无 meta 的列默认无角色（不破现有 dbt 编译/同步）。
