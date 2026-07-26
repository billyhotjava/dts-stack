# F2：独立完成逻辑模型

**优先级**：P0  
**状态**：DRAFT

## 目标

让用户只填写业务语义和逻辑结构即可达到 DESIGNED，并且明确当前必须项与可选项。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| Field | `ModelField` | `name`=ASCII 技术编码；`displayName`=业务名称；role/type/nullable |
| Gate | `DESIGNED` | 类型专属逻辑闭合，不读取实现/发布证据 |
| Gate API | `GET .../{id}/stage-gates` | 顺序增加 DESIGNED，原字段兼容 |
| UI projection | 当前 gate → primary action | blocker 必须来自服务端当前 revision |

## UI/UX 规格

- **入口**：模型详情默认进入“逻辑设计”。
- **布局**：

  ```text
  [当前任务：完成逻辑设计  2项必须]
  ① 业务定义与每行含义
  ② 字段：技术编码 | 业务名称 | 类型 | 作用
  ③ 类型专属：维度/SCD 或事实/时间 或聚合/消费
  [保存逻辑设计]（保存后由服务端重新计算 DESIGNED）
  <查看以后阶段要求>
  ```

- **四态**：加载时不闪旧 blocker；错误保留表单；保存草稿和完成设计是不同反馈；只读显示 revision。
- **操作走查**：补粒度 → 添加字段 → 从 TIME 字段选择业务时间 → 点击完成逻辑设计 → DESIGNED。
- **兼容**：旧中文 name 显示迁移提示，不阻止读取。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 重构类型专属逻辑字段契约 | P0 | DRAFT | F1 |
| T02 | 增加 DESIGNED 服务端门禁 | P0 | DRAFT | T01 |
| T03 | 只呈现当前门禁并绑定主动作 | P0 | DRAFT | T02 |

## Definition of Ready

- [x] ModelSpec/Implementation 所有权已钉死
- [x] 四类 DESIGNED 必填矩阵已写明
- [x] Gate API 兼容方式已写明
- [ ] F0 通过且已提交 implementationPolicy 的兼容/迁移契约已冻结

## 完成标准

- [ ] 无来源/实现可达到 DESIGNED
- [ ] 时间/SCD/层级只能选择真实字段
- [ ] 物理实现字段不在逻辑表单
- [ ] IT-03～IT-05 通过
