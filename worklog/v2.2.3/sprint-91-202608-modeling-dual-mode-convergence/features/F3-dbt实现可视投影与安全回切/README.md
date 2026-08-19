# F3: dbt 实现可视只读投影

> **SUPERSEDED_BY_SPRINT_92（2026-08-19）**：可信 BUSINESS projection 继续复用，但“DBT_MANAGED 可视化恒为只读”被替代为来源无关、逐节点 `FULL/PARTIAL/NONE` 投影；局部不可投影不得锁死整个 visual。以下只读结论仅记录 Sprint-91 历史实现。

> **范围已缩减**（2026-08-13 架构复核）。回切已移出 Sprint-91，移交说明见 `assets/sprint-92-back-conversion-handoff.md`；本 Feature 只有只读投影一个活动 Task。

**优先级**: P1
**状态**: IN_PROGRESS（T02 源码与聚焦自动化完成；等待 F0 真实浏览器基线）

## 目标

DBT_MANAGED 模型进入可视化模式时可查看可信的业务投影但不能误改 SQL，并且**不出现任何看起来可用、实际不可用的回切入口**。

## Feature 关联

- 上游：F1 的 visual view 与 BUSINESS representation；F2/F4 提供当前 DBT implementation 事实。
- 下游：F6/T02 在同一 visual view 展示只读依赖，F5/Sprint-93 使用相同 pins 显示发布与治理证据。
- 约束：本 Feature 只读，不反向修改 implementation，不另建回切、依赖或治理写路径。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| BUSINESS representation | 既有 `GET .../representations?representationScope=BUSINESS`（**零改动**） | DBT_MANAGED 得 `BUSINESS_VISUAL_READ` + `allowedActions=['OPEN_VISUAL']`，无 `EDIT_VISUAL`；fields/dependencies 不可信则 `BLOCKED` + `capabilityReasons[]` |
| 回切 | **本 Sprint 不提供** | 不新增端点，不在 UI 提供动作 |
| 旧端点 | `POST .../convert-to-designer-generated` | **原样保留、不改、不委托、不下线** |

本 Feature 不引入任何写路径，因此没有 ETag / 幂等 / 审计的新增契约。

## 为什么砍掉回切

首版设计声称复用 `DbtCompatibilityEvaluator` 的安全子集规则。源码核对结论（复核结论 B、账本 #20）：

1. `DbtCompatibilityEvaluator` 评估的是 ZIP 包级 runtime certification 与 adapter 支持度，入参是 `ModelPackage`，**不做 SQL→字段映射判断**——它不是回切规则；
2. 真正的子集规则 `ModelConversionClassifier` 要求 `dts_semantic.yml` 提供的 `SemanticMetadata`（现网模型语义在 ModelSpec 里，不在 dbt 文件里），且 `MACRO` 命中 `{{`、`CONSERVATIVE_COMPLEXITY` 命中 `with`/`(select`/`::`——而平台自己生成的 SQL 恰恰全部包含；
3. 因此**刚接管、一字未改的模型，回切预检必然 `allowed=false`**。

做一个必然拒绝的按钮，比不做更有害。补齐它需要新建一套基于 ModelSpec 既有语义的子集规则，是独立工作量，顺延 Sprint-92。

## UI/UX 规格

- **可视化模式**: DBT_MANAGED 默认展示只读基本信息、字段、来源和依赖投影，顶部状态“当前由代码维护 · 可视化只读”。
- **无回切入口**: 不显示“转为可视化维护”按钮，也不显示置灰版本。说明固定为：“本模型由代码维护，请在代码模式修改实现。”，不在产品页面承诺未来路线图。
- **投影不可信**: 逐条展示 `capabilityReasons`（动态依赖、字段/依赖不可信、制品版本不匹配等），并给出“在代码模式查看实现”的指引。
- **无伪编辑**: 不得出现可修改但保存被后端拒绝的控件。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T02 | 实现 dbt 模型只读投影 | P1 | IN_PROGRESS | F0/T01、F1/T02 |

## Definition of Ready

- [x] 可信投影和 fail-closed 规则已明确
- [x] 回切范围已按源码事实裁剪，理由与移交条件已成文
- [x] 旧 endpoint 的处置已明确（原样保留）
- [ ] T02 等待登录/browser harness

## 完成标准

- [ ] DBT 模型可视化投影只读且可信，不绕过代码所有权。
- [ ] 页面无回切入口、无置灰假按钮，用户能理解当前维护方式与去处。
- [ ] 旧转换接口、历史 artifact 与统一发布链均无回归（本 Feature 未触碰它们）。
