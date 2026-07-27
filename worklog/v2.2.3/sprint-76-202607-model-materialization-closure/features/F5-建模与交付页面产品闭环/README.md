# F5：建模与交付页面产品闭环

**优先级**：P0
**状态**：DRAFT
**依赖**：F2/F3 契约冻结，最终验收依赖 F4

## 目标

让普通维度建模与高级 dbt 建模都呈现“验证/构建/提交上线”的一致主路径，同时清楚区分已生成制品、已核验关系、审核发布、执行绑定和生产运行；模型详情提供单模型快捷入口，计划交付工作台提供同一 Candidate 真值的批量、角色动作和审计视图。

## 契约定义

| 页面 | 控件/状态 | 调用 |
|---|---|---|
| 模型详情-数据实现 | 验证实现、构建、提交上线、查看执行详情 | validate/compile、Build/Publish Intent、深链 |
| 高级 dbt 页面 | 构建、提交上线 | 同一 Build/Publish Intent；无 ModelSpec 上下文时提交上线阻断 |
| 计划交付工作台 | 开始构建/重试/reviewer/operator 动作 | ReleaseCandidate workspace/role-aware commands |
| 模型详情-发布结果 | 只读物理资产、运行/发布时间线 | lifecycle/registration/catalog |

## UI/UX 规格

```text
数据实现
  [验证实现] → 已生成构建制品（未建表）
  [构建] → 自动准备候选并显示排队/运行/核验
  [提交上线] → 记录 Publish Intent → 质量检查 → 提交审核，停止于人工边界
  [查看执行详情] → 交付工作台

交付工作台 / 构建
  QUEUED → RUNNING → 核验中
                    ├─ 失败：原因 + 修复/重试
                    └─ 成功：database.schema.table + run id

发布结果
  未发布：说明真实关系可能已构建，但尚不可消费
  审核中/待发布：显示当前人工任务和工作台深链
  已发布/部署中：物理资产卡 + binding deployment
  上线完成：PUBLISHED + ACTIVE binding + relation healthy
  已发布/运行异常：资产仍可见，显示唯一修复动作
```

四态：空、加载、错误、成功；运行中和部分失败作为业务状态额外覆盖。兼容 Chrome95。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 提供模型详情快捷构建与提交上线并复用候选控制面 | P0 | DRAFT | F1/T01、F2/T01、F4/T01 |
| T02 | 在交付工作台呈现真实构建与关系核验证据 | P0 | DRAFT | F2/T03、F3/T03 |
| T03 | 将发布结果绑定真实输出资产与失败恢复 | P0 | DRAFT | F4/T03 |

## Definition of Ready

- [x] 页面/路由/控件命名完成。
- [x] 每个动作对应唯一 API。
- [x] 四态和 happy path 完整。
- [ ] 后端 screen DTO 契约冻结。

## 完成标准

- [ ] 任何页面不把 compile 显示为物化。
- [ ] 模型详情和高级页面均不能绕过 ReleaseCandidate。
- [ ] “提交上线”只推进质量与提交审核，reviewer/operator action 仅存在于交付工作台。
- [ ] 单模型用户无需先手工创建 candidate 或填写 DAG 技术字段。
- [ ] 构建证据和发布资产分区清晰。
- [ ] Chrome95、窄屏、键盘和错误恢复可用。
