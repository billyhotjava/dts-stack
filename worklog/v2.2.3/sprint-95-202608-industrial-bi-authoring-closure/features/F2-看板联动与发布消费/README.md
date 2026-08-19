# F2：看板联动与发布消费

**优先级**：P0  **状态**：DONE

## 目标与契约

看板作者在既有编辑器中为已发布分析配置筛选参数映射和定向 cross-filter，保存后由现有 Dashboard publication 钉定。

- UI：每个卡片编辑头部的“参数映射”“联动设置”。
- 数据：复用 `parameter_mappings` 与 `visualization_settings`，不建表。
- 运行：点击来源 mark，仅向配置目标发送筛选；再次点击或清除恢复全部。
- 发布：现有 validate/publish 保留卡片 revision、参数和映射快照。

## Task

| ID | Task | 状态 |
|---|---|---|
| T01 | 接通参数映射、定向联动与发布回归 | READY |
