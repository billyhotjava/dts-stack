# F3: 轻量填报 (dts-intake)

**优先级**: P1
**状态**: READY
**设计阶段**: 骨架
**依赖**: F0, F1-lite, F2

## 目标

建立独立的轻量级填报服务 `dts-intake`，替代低质量 Excel 的数据采集路径：表单可配置、字段带校验、下拉**查 MDM**、提交走 F1-lite `ApprovalPort`，审批通过后数据以结构化形式入 ODS。表单和提交数据带**密级属性**。Excel 与填报**并行**，按数据质量灰度迁移。

## 设计红线（brainstorm 已对齐）

- **Excel 不强下线**：填报先跑通一张最差的表，成功后再逐步扩展
- **填报不造主数据**：所有下拉查 MDM（包括 person），填报系统自身不存主数据副本
- **提交走审批**：默认**所有提交均走审批**（F1-lite Stub 阶段会自动通过，full 阶段按规则审批）
- **表单可配置**：字段、校验、下拉来源、审批链都通过配置定义
- **密级继承**：每张表单在配置时设置 `form_classification`（`PUBLIC/INTERNAL/SECRET/CONFIDENTIAL`），该表单所有提交数据继承此密级；提交人密级必须 ≥ 表单密级才能填报
- **密级来源 dts-common**：不硬编码，沿用 `SecurityLevelCatalog`

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | [服务初始化](T01-服务初始化.md) | P0 | READY | F0, F1-lite, F2 |
| T02 | [表单配置模型（含 form_classification）](T02-表单配置模型.md) | P0 | READY | T01 |
| T03 | [填报运行时前端](T03-填报运行时前端.md) | P0 | READY | T02 |
| T04 | [提交与 ApprovalPort 集成](T04-提交与审批集成.md) | P0 | READY | T02, F1-lite |
| T05 | [落 ODS 结构化 schema（带 classification）](T05-落ODS结构化schema.md) | P0 | READY | T04 |
| T06 | [试点表迁移](T06-试点表迁移.md) | P1 | READY | T05 |

## 完成标准

- [ ] 表单可由业务自行配置，字段 / 校验 / 下拉 / 审批链 / 密级全可配
- [ ] 填报人密级 < 表单密级时，UI 拦截 + 后端拒绝（引用 SecurityLevelCatalog 判定）
- [ ] 至少 1 张真实低质量 Excel 表完成填报路径迁移
- [ ] 填报数据落入结构化 `ods_intake_*` 表（非大杂烩 varchar）、带 classification 列，可直接被 dbt 引用
- [ ] 填报入库 100% 经过 F1-lite ApprovalPort
