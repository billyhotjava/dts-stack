# T03：迁移已提交 implementationPolicy 并兼容旧输入

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F3

## 目标

在不删除历史字段的前提下，把 Sprint-73 已提交的 ModelSpec `implementationPolicy` 与旧输入安全投影到唯一 ModelImplementation owner，并停止新增双写。

## 技术设计（Contract-first）

- **输入契约**：旧 ModelSpec snapshot、可选 current ModelImplementation 和 dry-run 批次参数。
- **输入兼容**：旧 `sourceRefs/dependsOn/generationStrategy`、已发布契约可能产生的 `implementationPolicy` snapshot。
- **canonical 输出**：`ModelImplementationWriteCommand`，输入类型保持 PHYSICAL_ASSET/UPSTREAM_MODEL/GENERATED；settings 采用 F3/T01。
- **输出契约**：逐模型迁移决策 `ELIGIBLE|CONFLICT|ORPHAN|SKIPPED`、原因码、目标 implementation revision/checksum 和前后计数。
- **优先级**：已有 current ModelImplementation 为真值；旧字段只用于读取兼容和迁移 preview，不再作为 canonical 新写 owner。
- **迁移三件套**：
  - dry-run：总数/eligible/conflict/orphan/reason；
  - apply：按 modelSpecId 批次、幂等、追加实现 revision；
  - rollback：恢复旧读适配与旧页面可读能力，不删除新 implementation revision，也不改写历史 ModelSpec snapshot。
- **错误路径**：新旧真值冲突进入人工清单；不得自动覆盖 current implementation。
- **复用点**：账本 L06/L11/L13/L18；既有 `ModelImplementationCompatibilityAdapter`。
- **实现方案**：扩现有 adapter，不新增 mapper/service 台账；旧 ModelSpec API 可回读并原样 round-trip 已存值，但修改该字段返回 `MODEL_IMPLEMENTATION_SETTINGS_MOVED` 和数据实现修复入口；迁移完成、零旧写和零消费者证明前不删字段。

## UI 交互规格

历史模型显示“实现信息待迁移”及预检入口；冲突显示两个 revision 和选择依据；不得显示英文内部错误句。

## 影响范围

CompatibilityAdapter、snapshot codec、migration command、gate blocker 去重、历史 UI、tests。

## 验证（RED→GREEN）

- [ ] 旧三种输入各自正确投影
- [ ] 已提交 implementationPolicy settings 正确投影
- [ ] 旧客户端原样 round-trip 不清空历史值，修改旧字段得到明确迁移提示
- [ ] current implementation 冲突不覆盖
- [ ] dry-run/apply 重跑/rollback/计数对账

## Definition of Done

- [ ] 架构：新写零双写、零平行 owner，历史 snapshot 保持可读
- [ ] UI：兼容问题可修复，不伪装为空
- [ ] 切片：IT-11 通过
