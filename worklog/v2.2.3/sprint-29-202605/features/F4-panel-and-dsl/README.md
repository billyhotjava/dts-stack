# F4: Panel 配置抽屉 + DSL 序列化 + 接入点

**优先级**: P0
**状态**: READY
**依赖**: F3（节点已就绪）

## 目标

完成"配置 → 持久化 → 加载 → 接入页面"闭环：右侧 NodePanel 抽屉编辑节点参数；前端把画布 graph 序列化为 DSL JSON；后端 IngestionTask 新增 `graph_dsl` jsonb 字段；OrchestrationPage 落地真实可用入口。

## DSL Schema（草案，T03 落地时定稿）

```json
{
  "dslVersion": "1.0",
  "nodes": [
    { "id": "n1", "type": "Source", "position": {"x":0,"y":0}, "data": {...} }
  ],
  "edges": [
    { "id": "e1", "source": "n1", "target": "n2", "sourceHandle": "out", "targetHandle": "in" }
  ],
  "viewport": { "x": 0, "y": 0, "zoom": 1 }
}
```

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | `NodePanel` 抽屉壳 + 关闭/锁定/表单容器 | P0 | READY | F3-T07 |
| T02 | 6 类节点的配置表单（每节点自带 schema） | P0 | READY | T01 |
| T03 | DSL 序列化：画布 → JSON（含 viewport） | P0 | READY | T02 |
| T04 | DSL 反序列化：JSON → 画布；幂等性单测 | P0 | READY | T03 |
| T05 | dts-platform 后端：`IngestionTask.graph_dsl jsonb` 字段 + Liquibase + GET/PUT API | P0 | READY | T03 |
| T06 | OrchestrationPage 接入：拆 Tab「编排画布 / 运行实例」+ 顶部"保存 DSL"按钮 | P0 | READY | T04, T05 |

## 完成标准

- [ ] 选中任意节点 → 右侧 NodePanel 自动打开 → 改参数 → 实时同步到 store
- [ ] 点保存 → 后端 graph_dsl 字段成功写入；刷新页面后画布 100% 还原（节点位置、连线、参数、viewport）
- [ ] DSL JSON schema 在 `assets/dsl-schema.json` 文档化，包含 `dslVersion` 字段
- [ ] OrchestrationPage 老的 Airflow DAG 列表保留为「运行实例」Tab，老链接不破
- [ ] 后端 IT 测试：保存 → 读取 → 反序列化 三步均通过；单测覆盖率 ≥ 80%
