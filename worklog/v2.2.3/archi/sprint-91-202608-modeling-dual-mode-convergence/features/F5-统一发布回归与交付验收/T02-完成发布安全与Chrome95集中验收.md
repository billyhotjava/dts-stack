# T02: 完成发布安全与 Chrome 95 集中验收

**优先级**: P0
**状态**: BLOCKED
**依赖**: F0/T01、F0/T03、F5/T01；F1～F4、F6～F8 全部编码与聚焦测试完成

## 目标

在全部编码完成后执行一次集中 build/E2E，形成可回滚、可复核的 Sprint-91 交付证据。

## 技术设计 (Contract-first)

- **输入契约**: 完整 change set；F0 账号/双模式及全链路样本/browser harness；IT-01～IT-16 已具备自动化基础。
- **输出契约**: `assets/release-plan.md`、必要的运行说明、IT-01～IT-16 实际证据；Sprint Gate G3/G4/DoD 更新。
- **数据流**: focused tests → affected modules build → 部署/刷新运行实例 → 一条集中浏览器 journey → Chrome95 smoke → 审计/DB pins 核验 → 回滚演练。
- **错误路径**: 构建、登录、迁移、浏览器或发布任一失败即保持 Sprint 非 DONE；只做针对性修复/重跑，不重复全套扫描。
- **发布安全**: 本 Sprint 默认无 schema 迁移；若实施期出现迁移需求必须先更新 ADR 并走 expand-only 审核。前端可回滚到旧 bundle，后端新端点为 additive；旧 deep link/convert endpoint 保留。

## Definition of Ready

- [ ] F5/T01 与 IT-01～IT-16 的自动化前置全部完成。
- [ ] 受影响镜像、回滚 digest、目标 Chrome 95、账号和 ODS/物理结果核验路径均可用。
- [ ] 完整 change set 已冻结；本 Task 开始后只允许针对失败断点的小修复。

## 浏览器验收步骤

1. 维护账号打开 DESIGNER 样本，visual/code 切换并确认只读预览（3 个文件，stg 标注为系统节点）。
2. 点击接管，确认框出现**「本版本不可转回可视化维护」**告知；确认后编辑两个文件，保存、校验、提交；制造一次旧 ETag 冲突并确认无覆盖。
3. 切 visual，确认只读，且**页面上不存在回切按钮（含置灰态）**，只有说明文案。
4. 不使用 ZIP，从真实菜单依次创建/打开 DIM、FACT、DWS、ADS，确认 FACT 同时保存 ODS 基础来源和 DIM 引用；用手工 dbt 提交四个实现，declared/parsed 依赖全为 MATCHED。
5. 对 ADS 发起单表物化，核对 preview 中缺失上游为 BUILD、已有精确上游为 REUSE；创建候选后按拓扑完成 build/test/quality/显式 review/publish，并核验物理表字段和行数。
6. 用可视化配置完成同一结构的字段映射、过滤、关联和聚合，确认生成 implementation 经相同依赖计划与候选链完成；ZIP 只做等价性回归，不作为主链前置。
7. 在模型列表多选执行批量物化；再对 ADS 执行一次二次物化，确认模型/实现/资产身份不变，candidate/execution/observation 新增。
8. 只读/部门受限账号重复打开模型：visual 只读、code 显示需权限空态、无越权写动作；直接调用越界维护 API 返回 403。`xiezm` 的每个 lifecycle 状态仍通过显式命令推进，不自动审批/发布。
9. 从资产、元数据、血缘、质量入口沿同一 correlation 核对发布证据；不允许人工补登记后才可见。
10. 用旧 `open=advanced` 深链进入并确认归一化；检查空/加载/错误/成功状态、console 与 network。
11. `view=visual` 下确认建模页未请求 monaco chunk；切到 `view=code` 后才加载。

## 影响范围

- `assets/release-plan.md`、必要运行说明
- `it/` IT-01～IT-16
- Sprint/Feature/Task 状态与追溯矩阵

## 验证

- [ ] 前端 source-contract/Vitest/build；后端 modeling focused tests。
- [ ] 一次集中浏览器 E2E，失败只针对性重跑。
- [ ] 主链全程通过 UI 操作，禁止 ZIP/数据库脚本/API 脚本代替手工建模步骤；数据库只用于只读证据核验。
- [ ] Chrome95/legacy build 无语法、布局或 runtime API 错误；确认**未引入** Monaco worker（ADR-91-06）。
- [ ] 建模页首屏 JS 体积对照 `assets/nfr-budget.md` 的体积预算，超出即阻断。
- [ ] 回滚旧前端 bundle 后新后端 additive API 不影响旧工作台；必要时回滚后端仍可使用旧 convert endpoint。

## Definition of Done

- [ ] G3 发布安全、G4 可运维性和 DoD 全部有真实证据。
- [ ] IT-01～IT-16 无占位项，测试/构建/部署/浏览器/物理数据/治理投影状态分开陈述。
- [ ] `gitnexus_detect_changes()` 与最终 focused review 确认未引入平行页面、parser 或发布控制面。
