# T16：ODS 正向建模与全层逻辑引用

**优先级**：P1  
**状态**：IN_PROGRESS
**依赖**：T15 冻结 K31/K32、数据库/快照兼容、字段映射和引用状态。

## 目标

没有接入任务和物理来源时，用户能创建 ODS 逻辑模型，并保存引用该 ODS 的 DWD、DWS、ADS 设计；四层均可独立暂存和重开编辑。

## 技术设计

唯一规则见 [F3 K31/K32 与阶段矩阵](../../assests/F3-modeling-data-boundary.md)，复用 C19–C21。

- 输入：现有模型/定义/草稿命令，拟增贴源类型 SOURCE、layer=ODS；字段定义、已有 planId/domainId 等上下文；下游使用 `{modelSpecId,revision}` 与已冻结字段映射，不要求 sourceBindingId。
- 输出：同一 ModelSpec 身份、修订/ETag、可恢复 snapshot、逻辑依赖和字段来源；不写接入任务、物理表或新资产。已选择的真实 sourceRefs 继续严格核验，不伪造 CONFIRMED。
- 数据流：“创建贴源表”→三端同一契约→ModelSpecApplicationService/草稿 owner→现有修订与依赖存储→目录/编辑页重读。通过逻辑来源解析让下游设计与运行物理绑定解耦。
- 只新增 ODS 贴源类型，不将其归为事实表，也不要求事实形态、SCD、指标或接入连接。保留 DWD/DWS/ADS 已有合法层级规则，扩展 DWD 对 ODS 逻辑输入的允许范围。
- 草稿可不完整；完成设计时验证字段/类型/映射和引用闭环。上游版本改变需显式刷新引用并提示受影响字段，不静默追最新版本。
- 复用依赖循环检测、租户隔离、版本并发、名称唯一性；缺失/无权/删除引用、重复字段和版本冲突返回结构化错误并保留输入。
- 旧四类模型、物理来源模型及其导入导出继续可读写；新增类型的存储 CHECK/快照/包 schema 只按 T15 前向兼容方案变更。

## UI 交互

现有创建菜单启用“创建贴源表”；设计页显示名称/层级、字段类型与约束，沿用已选计划/业务域上下文，不要求重复配置。下游来源选择区可选择本计划中合法的 ODS 模型及版本，清楚区分“已设计”和“已物化”。

空态可直接新建；加载时保留当前输入；错误定位字段/引用；保存后可关闭重开。走查：零接入创建 ODS→DWD 字段映射→DWS 汇总→ADS 设计→分别重开核对身份、字段和依赖。

## 影响范围

ModelWorkbenchCreateMenu、modelWorkbenchService、modelSpecV2Contract；ModelSpecContract/ApplicationService/SnapshotCodec、model-spec-v2.schema.json、依赖校验/编译投影、已有包格式；必要迁移按 T15，不新增接入类型或 STG 建模。

## 验证与 DoD

- [ ] RED→GREEN：Java/TS/Schema 对 ODS 创建/更新/恢复一致；无来源设计成功且接入任务/物理表/资产写次数为零。
- [ ] IT-19：四层设计及引用保存、未完成草稿暂存、上游新修订、缺失字段、循环/越权/冲突均有行为证明。
- [ ] 旧四类/真实来源/旧快照兼容；如有迁移，清洁库与升级库均验证。
- [ ] 真实页面四态与身份/修订/依赖存储对应；Chrome 95 分项证据由 T20 汇总。
- [ ] DoR：T15 的 K31/K32 及基线已冻结；DoD：上述契约、界面、竖切片通过。本次未实施。

## 2026-09-07 实施进度

此处原为2026-09-07实施记录。后续已正式部署4e30da13d并有Chrome95分项证据；2026-09-08整改测试及最新交付状态见 [整改验证](../../it/evidence/current-environment/remediation-runtime-20260908.md)。完整DoD仍按实际验收逐项关闭。
