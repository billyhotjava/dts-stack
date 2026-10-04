# T03: 扩展 DESIGNER 的技术表示只读能力

**优先级**: P0
**状态**: IN_PROGRESS（源码与聚焦自动化完成；真实 API/browser IT 待补）
**依赖**: F0/T01（后端能力契约与接管 bundle 形态相互独立）

## 目标

让 `DESIGNER_GENERATED` 模型的 TECHNICAL 表示返回一个**只读**能力，使代码模式在不改变实现所有权的前提下可见。这是 F2 整条竖线的前置条件。

## 为什么需要这个 Task

首版 F1/T01 假定「代码模式可见性以既有 `allowedActions` 为唯一依据」即可落地，但源码事实相反（复核结论 C）：

```java
// ModelVisualizationCapabilityEvaluator.java:52-58
if (scope == RepresentationScope.TECHNICAL) {
    if (ownership != ImplementationMode.DBT_MANAGED) {
        reasons.add(CapabilityReason.MODEL_REPRESENTATION_ADVANCED_REQUIRES_DBT_MANAGED);
        return new CapabilityDecision(VisualizationCapability.BLOCKED, reasons);
    }
    return new CapabilityDecision(VisualizationCapability.ADVANCED_DBT_IMPLEMENTATION, reasons);
}
// ModelRepresentationService.java:481-484
technicalActions() 只可能返回 List.of("OPEN_ADVANCED_DBT")
```

DESIGNER 模型的 TECHNICAL scope 恒为 `BLOCKED`、`allowedActions=[]`。在 fail-closed 规则下，代码模式永远不可见。这是**后端契约扩展**，首版没有任何 Task 拥有它。

## 技术设计 (Contract-first)

- **枚举扩展**:
  - `VisualizationCapability` 新增 `DESIGNER_DBT_PREVIEW`；
  - `allowedActions` 新增 `OPEN_DBT_PREVIEW`。
- **决策规则**（`ModelVisualizationCapabilityEvaluator.evaluate`）:

  | scope | ownership | 投影可信 | 结果 |
  |---|---|---|---|
  | TECHNICAL | DBT_MANAGED | 是 | `ADVANCED_DBT_IMPLEMENTATION` +`['OPEN_ADVANCED_DBT']`（**语义不变**） |
  | TECHNICAL | DESIGNER_GENERATED | 是 | `DESIGNER_DBT_PREVIEW` + `['OPEN_DBT_PREVIEW']`（**新增**） |
  | TECHNICAL | DESIGNER_GENERATED | 否 | `BLOCKED` + 既有 reasons（不变） |
  | TECHNICAL | 任一 | 无 implementation | 保持既有分支语义 |
  | BUSINESS | 任一 | — | 完全不变 |

- **权限**: TECHNICAL scope 仍需 `CATALOG_MAINTAINERS`（`ModelRepresentationResource.java:47-52`）。**不放宽**。只读账号请求 TECHNICAL 得到的行为与今日一致，由 F2/T03 在 UI 侧给出 reason 文案。
- **不变量**:
  - `OPEN_DBT_PREVIEW` **不得**携带任何写动作语义；接管动作的可用性由 F2 的 transition validate 单独判定，不塞进 `allowedActions`；
  - `MODEL_REPRESENTATION_ADVANCED_REQUIRES_DBT_MANAGED` 这个 reason 只在**确实请求高级编辑**时出现，不得因新增预览能力而消失或改语义；
  - 既有 `ADVANCED_DBT_IMPLEMENTATION` 的全部断言零回归。
- **错误路径**: 投影不可信（pin mismatch / fields untrusted / dynamic dependency / dependencies untrusted）优先级高于新能力——先 `BLOCKED`，不给预览。

## Definition of Ready

- [x] DESIGNER/DBT × BUSINESS/TECHNICAL 决策矩阵已冻结。
- [x] 新能力只读、无 transition 写动作的边界已确认。
- [x] 既有 `ADVANCED_DBT_IMPLEMENTATION` 语义零改动。

## 影响范围

- `ModelVisualizationCapabilityEvaluator.java`
- `ModelRepresentationContract.java`（枚举）
- `ModelRepresentationService.java`（`technicalActions`）
- 前端 TS contract 的能力码/动作码联合类型
- evaluator 与 representation 的既有单测（新增用例，不改既有断言）

## 验证 (RED→GREEN)

- [ ] RED：当前 DESIGNER + TECHNICAL 返回 `BLOCKED`，代码模式不可见。
- [ ] 决策矩阵五行全覆盖单测。
- [ ] 断言 DBT_MANAGED 的 TECHNICAL 结果**逐字段未变**（防止扩展时误改既有分支）。
- [ ] 断言投影不可信时不返回 `DESIGNER_DBT_PREVIEW`。
- [ ] 断言 `OPEN_DBT_PREVIEW` 出现时 `allowedActions` 中不含任何写动作。
- [ ] 只读账号请求 TECHNICAL 的行为与变更前一致。

## Definition of Done

- [ ] DESIGNER 模型的代码模式在能力层可见且只读。
- [ ] 既有 TECHNICAL/BUSINESS 断言零回归。
- [ ] 前后端能力码联合类型一致，F1/T01 的纯函数可直接消费。
- [ ] 无权限放宽、无新增写路径。
