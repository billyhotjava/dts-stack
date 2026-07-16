# F5: dbt 产物生成与 SQL 双模式

**优先级**: P0
**状态**: IN_PROGRESS（生成、导入、漂移与发布门禁已完成；真实 dbt test 受环境源表缺失阻断）

## 目标

保证普通建模最终产出真正可执行的 dbt 项目文件，同时让高级开发直接维护 SQL 并回写登记信息。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | ModelSpec 到 dbt SQL/schema/tests/docs 编译器 | P0 | DONE | F4-T03 |
| T02 | dbt manifest/SQL 导入与字段快照 | P0 | DONE | F3-T02 |
| T03 | drift、dbt parse/test 和产物发布门禁 | P0 | IN_PROGRESS | T01,T02 |

## 完成标准

- [x] 设计器生成物能被 `dbt parse` 使用；`dbt test` 已执行但当前环境缺少 3 张源表。
- [ ] dbt 原生模型可以导入、展示和运行。
- [x] SQL、字段、粒度漂移可以阻断运行提交并给出修复路径；发布门禁 API 已返回稳定 blocker code。
