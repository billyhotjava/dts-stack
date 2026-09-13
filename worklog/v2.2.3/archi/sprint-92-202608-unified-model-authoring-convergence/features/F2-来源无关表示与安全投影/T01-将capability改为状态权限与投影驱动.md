# T01：将 capability 改为状态、权限与投影驱动

**优先级**：P0  
**状态**：IMPLEMENTED  
**依赖**：F0/T02

## 目标

移除 `implementationMode` 对 visual/code editability 的互斥判定，统一由 ModelStatus、权限、草稿状态和 projection trust 生成 allowedActions。

## 技术设计（Contract-first）

- **输入契约**：`RepresentationScope`、ModelStatus、technicalAuthorized、draft state、projection coverage/trust；账本 L05/L06/L20。
- **输出契约**：`allowedActions` 仅含 `OPEN_VISUAL,OPEN_CODE,EDIT_MODEL,EDIT_IMPLEMENTATION,SAVE,VALIDATE,COMMIT,FORK_DRAFT` 的适用子集；`provenance` 单列返回。
- **数据流**：representation/context loader → capability evaluator → authoring context → UI。
- **错误路径**：projection unknown 只移除结构化 implementation edit，不移除业务 ModelSpec edit；PUBLISHED 只给 OPEN/FORK；无技术权限不返回 OPEN_CODE/正文。
- **复用点**：扩展 `ModelVisualizationCapabilityEvaluator`/representation view；不新建第二 evaluator。

## 影响范围

`dts-platform` representation/capability DTO 与测试；`dts-platform-webapp` access adapter 后续由 F3 消费。

## 验证（RED→GREEN）

- [ ] 参数化单测覆盖 3 provenance × DRAFT/PUBLISHED × 权限 × FULL/PARTIAL/NONE。
- [ ] 断言 implementationMode 不出现在 allowedActions 决策分支。
- [ ] 无技术权限响应不含文件正文或正文 URL。

## Definition of Done

- [ ] 三种来源在相同状态/权限下得到相同主动作。
- [ ] PUBLISHED、无权限、projection 降级各自独立，不再混为“代码所有权”。
- [ ] 旧 BUSINESS/TECHNICAL representation read contract 兼容。
