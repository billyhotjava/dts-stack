# F3：实现方式与发布结果解耦

**优先级**：P0  
**状态**：DONE

## 目标

让“数据实现”清楚表达如何生成目标，让“发布结果”只表达已经生成了什么，并把普通配置与高级 dbt 放在同一实现入口。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| Implementation | 既有 `ModelImplementationWriteCommand` | ownership/inputMode/inputs/mappings/settings/materialization |
| Settings | `targetPhysicalName, loadStrategy, partitionFields, retentionDays` | 归 implementation revision |
| Validate | `POST .../implementation/inputs/validate` | 只读，返回 valid/code |
| Results | `GET .../lifecycle` + ReleaseCandidate | 只读 artifacts/events/registrations |

实现输入矩阵固定为：

| 模型类型 | PHYSICAL_ASSET | UPSTREAM_MODEL | GENERATED |
|---|---|---|---|
| DIMENSION | 允许 | 允许 | 允许 |
| FACT | 允许 | 允许 | 禁止 |
| SUMMARY | 禁止 | 允许 | 禁止 |
| APPLICATION | 禁止 | 允许 | 禁止 |

## UI/UX 规格

- **数据实现**：

  ```text
  [普通配置] [高级 dbt]
  输入 → 映射/转换 → 目标与物化 → 验证
  ```

- **发布结果**：

  ```text
  发布状态 | 真实物理对象 | DDL/构建/测试 | 登记与血缘
  未发布：暂无结果，[返回数据实现] [进入发布工作台]
  ```

- dbt 入口不再出现在发布结果页；
- DESIGNED 前数据实现锁定并解释“先完成逻辑设计”；
- 临时 ephemeral STG 只展示为技术证据，不登记物理资产。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 收敛ModelImplementation实现设置所有权 | P0 | DONE | F2 |
| T02 | 统一普通配置与高级dbt实现入口 | P0 | DONE | T01 |
| T03 | 将物理资产阶段收敛为发布结果 | P0 | DONE | T01、T02 |

## Definition of Ready

- [x] Implementation command 和 settings owner 已钉死
- [x] dbt/physical boundary 已钉死
- [x] 发布结果只读 contract 已钉死
- [x] F2 DESIGNED gate 通过实现

## 完成标准

- [x] 逻辑页不保存实现设置
- [x] 普通/dbt 绑定同一 modelSpec revision
- [x] 发布结果无 dbt 编辑入口
- [x] IT-06～IT-08 通过
