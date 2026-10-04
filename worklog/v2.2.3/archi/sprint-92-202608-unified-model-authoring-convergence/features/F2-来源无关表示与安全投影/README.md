# F2：来源无关表示与安全投影

**优先级**：P0  
**状态**：IMPLEMENTED（golden 样本自动化通过；真实样本 E2E 待验收）

## 目标

让平台生成、手工代码和 ZIP 导入模型拥有相同的 visual/code 入口；provenance 只用于展示和审计，复杂实现按节点安全降级而不是把整个 visual 锁死。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| capability | `AuthoringContextView.allowedActions` | `OPEN_VISUAL,OPEN_CODE,EDIT_MODEL,EDIT_IMPLEMENTATION,SAVE,VALIDATE,COMMIT,FORK_DRAFT`；不按 implementationMode 互斥 |
| provenance | `AuthoringProvenance` | `{origin:SYSTEM_GENERATED|MANUAL_CODE|DBT_ZIP_IMPORT,sourceKind,lossless,bundleChecksum}`；无权限时不含文件正文 |
| projection | `AuthoringProjection` | `{coverage:FULL|PARTIAL|NONE,lossless,managedPaths,rawNodes,reasons}` |
| node | `ProjectionNode` | `{nodeId,kind,editable,sourcePath,line?,column?,checksum}` |
| rewrite fence | managed path/node checksum | visual 仅重写已拥有区域；unmanaged 文件任何变化均冲突 |
| 复用 | `DbtSqlProjectionParser`、static validator、compiler、bundle manifest | 扩展 adapter/golden case，不建第二 parser |

## UI/UX 规格

- 来源显示为只读 badge，例如“来源：dbt ZIP 导入”，不得用于 disabled 条件。
- FULL：结构化字段/来源/转换全部可视编辑。
- PARTIAL：结构化节点 + 原始代码节点混合；原始节点可在同页代码编辑器中修改。
- NONE：业务元数据仍可编辑；实现区域用单一 raw node 打开同一 bundle，并解释原因。
- projection 错误不猜测、不清空、不自动格式化用户文件。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 将 capability 从 ownership 改为状态权限与投影驱动 | P0 | IMPLEMENTED | F0/T02 |
| T02 | 实现 FULL/PARTIAL/NONE 与原始代码节点 | P0 | IMPLEMENTED | T01 |
| T03 | 保留 ZIP/手工 bundle 并建立无损 rewrite 围栏 | P0 | IMPLEMENTED | T01、T02、F1/T02 |

## 实施证据（2026-08-20）

- capability 已改为由模型状态、当前权限和 projection 决定，provenance 不再作为可编辑性开关。
- 投影已返回 `FULL/PARTIAL/NONE`、managed paths、raw nodes 和 reasons，歧义表达式按 fail-closed 降级。
- visual 只能重写受管节点；unmanaged bundle 变化会触发冲突。
- 自动化详见 `../../it/evidence/20260820-automated.md`。

## Definition of Ready

- [x] provenance、projection、node 和 rewrite fence 已定义。
- [x] 任意 SQL 的安全降级路径明确。
- [x] 指定复用 parser/validator/compiler/bundle owner。
- [ ] 三类真实样本及 coverage 基线待 F0/T02；golden 自动化样本已覆盖三种 coverage。

## 完成标准

- [ ] implementationMode 不再导致 visual/code 整页互斥。
- [ ] 三类来源均能打开并保存同一 authoring draft。
- [ ] PARTIAL/NONE 不丢文件、不猜测改写，unmanaged checksum 零变化。
- [ ] provenance 与技术正文权限分离，审计不含正文。
