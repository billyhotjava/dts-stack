# T02: PJM 黄金主线 fixture 与对象映射

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01

## 目标

把 PJM“项目节点计划闭环”固定为端到端回归夹具，同时验证同一契约可扩展到质量、风险和预算对象。

## 技术设计

- 使用 `worklog/v2.2.3/s10/v4/pjm/dbt_model` 作为输入样例。
- 登记 `project-node` FACT 对象，粒度为 `project_no + subsystem + node_task + plan_date`。
- 绑定 DWD、DWS、ADS 三个 ModelSpec，并保存旧 dbt 模型的 `legacyRef`。
- fixture 只保存业务语义和模型关系，不把 PJM 字段写入通用代码。

## 影响范围

- `worklog/v2.2.3/sprint-60-202607/assets/pjm-golden-path.md`
- `source/dts-platform-webapp` 测试 fixture
- `source/dts-platform` 集成测试 fixture

## 验证

- [x] 可从 fixture 解析对象、粒度、标准字段和目标模型。
- [x] DWD、DWS、ADS 依赖方向正确。
- [x] 旧模型可被标记为 `LEGACY_READONLY`，不影响新版本编译。

## 完成标准

- [x] fixture 已被前端契约测试、后端契约/编译器测试共同引用；真实 API 运行和 Playwright 仍待接入。
