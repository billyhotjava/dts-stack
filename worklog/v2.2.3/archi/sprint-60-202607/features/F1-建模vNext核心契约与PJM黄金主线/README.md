# F1: 建模 vNext 核心契约与 PJM 黄金主线

**优先级**: P0
**状态**: DONE

## 目标

建立不绑定具体行业的 BusinessObject、WarehousePlan、ModelSpec、DbtArtifact 和 PipelineRun 契约，并用 PJM 项目节点计划闭环作为第一条可执行样例。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | BusinessObject 与 ModelSpec 契约 | P0 | DONE | - |
| T02 | PJM 黄金主线 fixture 与对象映射 | P0 | DONE | T01 |
| T03 | 旧 dbt/旧语义模型兼容与版本策略 | P0 | DONE | T01 |

## 完成标准

- [x] 对象类型、业务主键、粒度、来源、标准绑定和实现模式均有明确字段。
- [x] PJM fixture 不包含平台硬编码逻辑，能被普通用户路径和 dbt 导入路径共同使用。
- [x] 明确旧 `/api/semantic/*`、旧 dbt 模型和新 ModelSpec 的转换关系。
