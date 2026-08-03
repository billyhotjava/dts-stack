# T06：实现 source-only 与复杂依赖闭包

**优先级**：P1
**状态**：CODE_COMPLETE
**依赖**：F1/T02、T01～T04、D10

## 目标

在不新建 YAML/SQL parser 的前提下，扩展同一规范化 `ModelPackage` seam，使 source-only 只有在 enforced schema contract、可信完整字段和可证明依赖闭包同时成立时可导入；复杂或动态依赖稳定阻断受影响模型及其下游。

## Contract-first

- **输入**：FX-02 source-only 与 FX-03 complex/blocked；schema YAML、literal `ref/source`、macro/package 依赖事实。
- **输出**：字段 `DECLARED` provenance、`IMPORTABLE|STRUCTURE_VIEW_ONLY|BLOCKED`、受影响 caller/downstream 闭包、稳定 issues/recoveryAction；成功 apply ownership 固定为 `DBT_MANAGED`。
- **字段门禁**：model 必须显式 `contract.enforced=true`，每个 column 有非空且唯一 `name` 与显式 `data_type`；否则 `SOURCE_FIELDS_UNVERIFIED`，不得人工重建技术结构。
- **依赖门禁**：非 literal ref/source、自定义 macro 隐藏依赖、Jinja 控制流、adapter dispatch、缺失 package 或不可闭合依赖均 fail-closed；BLOCKED 项不可选择且不能被计入 PARTIAL。
- **复用**：只扩展 F1/T02 冻结的 normalized projection seam；禁止第二 YAML parser、正则 SQL 解析器或平行状态表。

## 验证

- [ ] FX-02 覆盖 enforced 完整字段、非 enforced、缺 type、重名和无字段。
- [ ] FX-03 覆盖动态 ref/source、macro 隐藏依赖、package 缺失、ephemeral/technical-only 与下游传播。
- [ ] 合格独立闭包可成功 apply；blocked 闭包不可选择且不会把成功 attempt 误记为 PARTIAL。
- [ ] 未实现或未认证组合稳定 fail-closed，不显示“已支持”。

## Definition of Done

- [ ] source-only 与 artifact-rich 共用相同 ModelPackage/import run/ModelSpec/Implementation owner；没有新增 parser 或台账。
