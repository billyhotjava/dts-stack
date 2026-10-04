# T02：增加 DESIGNED 服务端门禁

**优先级**：P0  
**状态**：DONE
**依赖**：F2/T01

## 目标

把“逻辑已完成”变成独立、可测试且不依赖数据实现的服务端事实。

## 技术设计（Contract-first）

- **输入契约**：当前 ModelSpec view/revision/checksum。
- **输出契约**：Stage 顺序固定 `DRAFT_SAVE, DESIGNED, IMPLEMENTATION_READY, RELEASE_READY`。
- **DESIGNED 规则**：严格按 `assets/requiredness-matrix.md` §2；不得访问 ModelImplementation、build、quality、classification evidence。
- **IMPLEMENTATION_READY 规则**：先继承 DESIGNED，再只校验唯一 ModelImplementation current revision 和 validation evidence。
- **错误路径**：证据读取失败对相关后续 gate fail closed；不影响 DRAFT_SAVE/DESIGNED。
- **复用点**：账本 L05/L06；现有 `evaluateAll`、GateView、repairRoute。
- **实现方案**：拆分 `logicalBlockers`、`implementationBlockers`、`releaseBlockers`；删除 ModelSpec `sourceRefs/dependsOn` 作为实现就绪真值的双重判断。

## UI 交互规格

后端 blocker message 使用业务中文，repairRoute 精确落到逻辑字段/类型专属区；内部 code 在展开详情可见。

## 影响范围

ModelSpecStageGateService、API enum/type、gate guidance、backend/frontend tests。

## 验证（RED→GREEN）

- [x] 无来源/无实现但逻辑闭合：DESIGNED READY、IMPLEMENTATION_READY BLOCKED
- [x] 未闭合逻辑但已有旧实现：DESIGNED/后续均 BLOCKED
- [x] 每类 DESIGNED 规则参数化测试
- [x] 同一根因只出现一个 blocker

## Definition of Done

- [x] 架构：阶段依赖单向且服务端权威
- [x] UI：repairRoute 对应真实控件
- [x] 切片：IT-03/IT-04 gate 结果正确
