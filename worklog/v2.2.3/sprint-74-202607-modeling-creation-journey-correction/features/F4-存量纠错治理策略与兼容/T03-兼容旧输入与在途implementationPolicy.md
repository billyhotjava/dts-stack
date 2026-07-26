# T03：兼容旧输入与在途 implementationPolicy

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F3

## 目标

在不删除历史字段的前提下，把旧 ModelSpec 输入和在途实现策略投影到唯一 ModelImplementation owner。

## 技术设计（Contract-first）

- **输入契约**：旧 ModelSpec snapshot、可选 current ModelImplementation 和 dry-run 批次参数。
- **输入兼容**：旧 `sourceRefs/dependsOn/generationStrategy`、可能存在的 `implementationPolicy` snapshot。
- **canonical 输出**：`ModelImplementationWriteCommand`，输入类型保持 PHYSICAL_ASSET/UPSTREAM_MODEL/GENERATED；settings 采用 F3/T01。
- **输出契约**：逐模型迁移决策 `ELIGIBLE|CONFLICT|ORPHAN|SKIPPED`、原因码、目标 implementation revision/checksum 和前后计数。
- **优先级**：已有 current ModelImplementation 为真值；旧字段只用于迁移 preview，不双写。
- **迁移三件套**：
  - dry-run：总数/eligible/conflict/orphan/reason；
  - apply：按 modelSpecId 批次、幂等、追加实现 revision；
  - rollback：恢复旧读适配开关，不删除新对象。
- **错误路径**：新旧真值冲突进入人工清单；不得自动覆盖 current implementation。
- **复用点**：账本 L06/L11/L13/L18；既有 `ModelImplementationCompatibilityAdapter`。
- **实现方案**：扩现有 adapter，不新增 mapper/service 台账；迁移完成和零旧写证明前不删字段。

## UI 交互规格

历史模型显示“实现信息待迁移”及预检入口；冲突显示两个 revision 和选择依据；不得显示英文内部错误句。

## 影响范围

CompatibilityAdapter、snapshot codec、migration command、gate blocker 去重、历史 UI、tests。

## 验证（RED→GREEN）

- [ ] 旧三种输入各自正确投影
- [ ] implementationPolicy settings 正确投影
- [ ] current implementation 冲突不覆盖
- [ ] dry-run/apply 重跑/rollback/计数对账

## Definition of Done

- [ ] 架构：零双写、零平行 owner
- [ ] UI：兼容问题可修复，不伪装为空
- [ ] 切片：IT-11 通过
