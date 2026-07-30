# DTS v2.2.3 完整数据治理 Demo

## 1. 分类入口

`it` 根目录只负责分类导航。两套 Demo 相互隔离，可以由不同测试同事并行实施：

| 分类 | 业务场景 | 实施入口 | 源 Schema | 模型前缀 |
|---|---|---|---|---|
| Project | 研发项目任务、进度、风险和成本健康度 | [project/README.md](project/README.md) | `it_demo_src` | `it_demo_` |
| Finance | 成本中心预算、执行、应付和预测治理 | [finance/README.md](finance/README.md) | `it_fin_demo_src` | `it_fin_demo_` |

## 2. 目录结构

```text
it/
├── README.md      # 分类导航和公共约束
├── project/       # 项目健康度完整 Demo
└── finance/       # 财务预算治理完整 Demo
```

每个分类目录均独立包含：

- 架构与治理设计。
- DTS UI 手工实施 Runbook。
- 验收清单和本地验证报告。
- 模型字段矩阵和对象登记表。
- 源数据 SQL、dbt 工程和证据目录。

## 3. 公共约束

- 两套 Demo 均使用合成数据，不修改、不删除、不回填任何客户 ODS。
- 两套 Demo 不共用源表、ODS、业务对象编码、ModelSpec 或指标编码。
- 只复用租户内语义一致且版本现行的公共单位、日期维度等标准对象。
- ModelSpec 是唯一逻辑模型台账；dbt 节点绑定对应 ModelSpec 的精确 revision。
- 密级与业务标签分别治理，不能用标签代替安全密级。
- UI 保存、编译、质量、物化、调度、权限和消费必须分层留证。

## 4. 测试同事开始方式

1. 根据分工选择 Project 或 Finance。
2. 进入对应目录的 `README.md`。
3. 运行该目录 `sql/01-source-bootstrap.sql`。
4. 按 `03-ui-runbook.md` 逐页创建和验证。
5. 在 `assets/demo-object-register.csv` 登记实际 ID、revision、run ID 和证据。
6. 使用 `04-acceptance-checklist.md` 完成基线、阻断、修复和消费验收。

根目录不再存放任何具体业务 Demo 制品。
