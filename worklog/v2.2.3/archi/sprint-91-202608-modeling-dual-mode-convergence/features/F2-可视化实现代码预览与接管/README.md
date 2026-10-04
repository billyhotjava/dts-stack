# F2: 可视化实现代码预览与接管

> **SUPERSEDED_BY_SPRINT_92（2026-08-19）**：本 Feature 的 preview、bundle freeze、CAS、幂等与审计实现继续作为兼容 seam；“接管代码实现”按钮、ownership 翻转和不可逆产品文案不再是目标交互。新实现由 `../../../sprint-92-202608-unified-model-authoring-convergence/` 的统一 authoring draft 承接。以下内容保留为历史设计与已完成证据，不继续扩展。

**优先级**: P0
**状态**: IN_PROGRESS（T01/T02/T03 源码与聚焦自动化完成；等待真实事务与浏览器 IT）

## 目标

可视化模型进入代码模式时先看到与当前版本严格绑定的只读 dbt 代码；只有用户明确确认“接管代码实现”后，平台才原子地把模型和实现转为 DBT_MANAGED，**并保证转换后的模型仍能编译、物化、发布**。

## Feature 关联

- 上游：F1 提供唯一 code view；F0/T02 固定 canonical bundle/compile 契约。
- 下游：F4 编辑接管后的 bundle；F6/T03 以统一 dependency snapshot 初始化后续草稿；F7/T03 复用 preview/freeze/import seam；F5 回归接管后的统一发布。
- 约束：本 Feature 只改变 ownership 与制品来源，不创建依赖图、物化计划或发布分支。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| REST | `GET /api/modeling/model-specs/{id}/implementation/dbt-preview` | query: `modelRevision:int`、`implementationRevision:int`；返回 revision/checksum + `files[]`（**恒 3 项**，含 `nodeKind`/`artifactTypes`）+ `previewChecksum` + `readOnly:true` |
| REST | `POST .../{id}/implementation/ownership-transitions/validate` | headers 两个 ETag；body `{targetOwnership:'DBT_MANAGED'}`；返回含 `reversible:false`；无写入 |
| REST | `POST .../{id}/implementation/ownership-transitions` | body `{targetOwnership,previewChecksum,idempotencyKey}`——**不含 `projectKey`/`dbtUniqueId`**（ADR-91-08）；同事务推进 model/implementation revision |
| 制品 | 接管落地类型集合 | **必须等于 `{SQL,SCHEMA,CONFIG}`**，与既有 draft commit 同构；`stg_*.sql` 随整包冻结进 `CONFIG`。不新增 artifact 类型、不放宽 `compile()` 门禁 |
| 身份 | dbt project/node | 服务端派生 `dts` / `model.dts.model_<uuid>`；接管前后 selector 不变 |
| 权限 | 三个端点 | `CATALOG_MAINTAINERS`；越权 403 |
| 审计 | `MODEL_IMPLEMENTATION_OWNERSHIP_TRANSITION` | source/target ownership、actor、model/implementation before/after、transitionId；不记录文件正文 |

## UI/UX 规格

- **代码模式初态**: 只读 Monaco 展示平台生成的 3 个文件；主模型与 `.yml` 为主视图，`stg_*.sql` 标注为“系统生成的中间节点”并默认折叠。顶部说明“当前仍由可视化配置维护”。
- **主动作**: “接管代码实现”。点击后先调用 validate，再展示确认框。
- **确认框必须明示不可逆**（ADR-91-09）：“接管后本版本**无法转回可视化维护**；可视化字段将转为只读，发布入口不变。” 依据 validate 返回的 `reversible:false` 渲染，不得写死。
- **确认成功**: 刷新 ModelSpec/lifecycle/representation，以返回的新 revision 打开可编辑 dbt 草稿。
- **失败**: ETag 冲突提示“模型已被他人更新，请刷新后重试”；预览已过期必须重新生成，不允许带旧 checksum 强行提交。
- **取消**: 不调用 commit endpoint，ownership/revision 不变。
- **只读账号**: 代码模式仍可进入，但显示“需要维护权限才能查看实现代码”的空态——TECHNICAL scope 需 `CATALOG_MAINTAINERS`，本 Sprint 不放宽。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 提供版本钉定的生成 dbt 只读预览 | P0 | IN_PROGRESS | F0/T02、F1/T01 contract |
| T02 | 实现原子且可审计的代码接管命令 | P0 | IN_PROGRESS | F0/T02、T01 |
| T03 | 落地代码预览与显式接管交互 | P0 | IN_PROGRESS | F0/T01、F1/T02、T01、T02 |

## Definition of Ready

- [x] Preview 与 transition 请求/响应已钉死
- [x] 事务顺序、身份派生与审计 owner 已明确
- [x] UI 确认、不可逆告知与失败反馈已定义
- [ ] **制品落地契约待 F0/T02 确证** —— 首版「生成 SQL/SCHEMA 作为首个 source bundle」已被证伪（复核结论 A）
- [ ] T03 等待真实登录/browser harness

## 完成标准

- [ ] 仅查看代码不会产生写请求或 revision。
- [ ] 接管成功后 model 与 implementation ownership 同为 DBT_MANAGED，且可创建首个草稿。
- [ ] **接管后立即 `compile` 成功**，制品类型集合等于 `{SQL,SCHEMA,CONFIG}`，`CONFIG` 内含 stg。
- [ ] 接管前后 `dbtUniqueId` 与物化 selector 不变。
- [ ] 重复请求、并发请求、过期 preview、越权和编译失败均有稳定行为与测试。
