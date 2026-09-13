# Sprint-36: 数据安全与机密级合规整改专项（202606）

**时间**: 2026-06
**状态**: PLANNING
**类型**: Security / Compliance / Implementation Plan（dts-keycloak + dts-admin + dts-platform + dts-admin-webapp + dts-platform-webapp）
**目标**: 闭合协议 2.3.2.5（数据安全）与 2.3.2.10（安全保密）中阻断**机密级（BMB17.1/17.2-2024）测评验收**的 P0 缺口，并补齐数据安全域最关键的 P1 能力（敏感数据自动识别）。按"配置基线 → 会话整改 → 权限模型 → 识别引擎 → 合规台账 → 评审准入"顺序，以 **TDD 驱动**拆成可执行 feature/task，每个 task 测试先行（RED→GREEN→REFACTOR）。

## 立项依据

详见差距分析报告 `assets/protocol-gap-analysis-v3.md`（覆盖协议 11 模块，13 项 P0 + 29 项 P1）。报告核心结论：sprint-32~35 未闭合 M05/M10 的任何前序缺口，机密级测评最基础的口令控制、操作权限矩阵、敏感识别、BMB 符合性映射均缺失。逐模块证据底稿见 `assets/gap-evidence/M01..M11`。

## 主题聚焦决策（为什么本期选 M05 + M10）

协议 13 项 P0 横跨 6 个模块，单 sprint 不可全包。本期选取**数据安全与机密级合规**作为单一主题，依据：

1. **验收硬门槛**：BMB17.1/17.2-2024 机密级离线+在线测评是整个项目交付的前置条件，测评不通过则其余功能无法验收。安全合规 P0 是所有 P0 中最高优先级。
2. **P0 最密集**：本主题独占 6 项 P0（强密码、失败锁定、会话整改、操作权限矩阵、BMB 映射、测评对接），是 P0 收益最高的单主题。
3. **技术内聚**：全部落在 Keycloak realm / dts-admin 安全配置 / IAM 策略 / Catalog masking / SecurityBaselineService 这一条安全栈上，无跨域耦合。
4. **单 sprint 可落地**：以配置 + 中等规模功能为主（口令策略是配置项、权限矩阵是新增实体 + 切面、识别引擎是离线扫描服务），不涉及高可用集群/压测那类需要大规模架构改造与环境投入的工作（后者排入 Sprint-38）。

其余 P0（M04 生命周期/销毁、M09 告警规则、M11 高可用/性能）与 P1 功能增强按报告 §6 roadmap 排入 Sprint-37+。

## 合规标尺

- **BMB17.1-2024 / BMB17.2-2024**：涉密信息系统分级保护「机密级」设计与测评要求（协议 2.3.2.10 明列）。
- 口令控制、登录失败锁定、会话控制、错误屏蔽、密钥管理、审计留痕为机密级最基础控制项。
- 与甲方 PKI/CA 兼容 + USBKey 登录（2.3.2.10-3）现已覆盖（`PkiVerificationService`/`SecurityPkiBinding`，有测试），本期不重做，仅纳入符合性映射台账。

## 分层决策（控制项归属边界）

| 控制层 | 现有事实源 | 本期改动 | 边界约束 |
|--------|-----------|---------|----------|
| 身份与口令 | Keycloak realm `realm-dts.json` | 增 passwordPolicy + bruteForce；dts-admin 启动校验 | 口令策略只由 Keycloak 权威，应用侧只做存在性校验，不另造口令体系 |
| 会话 | `PortalSessionRegistry` / `AdminSessionRegistry` / 前端 token 存储 | strip 前端裸 token / 生产 console / TEST_SESSION 旁路；idle 软锁 | 不改既有 inactivity filter 后端契约，只闭合前端泄露面与旁路 |
| 资产操作授权 | `IamDatasetPolicy`（OBJECT/ROW/FIELD） | 新增 `IamAssetActionPolicy`（动作维度）+ `AccessChecker.canPerform` | 复用审计 OperationType 枚举对齐动作语义，不再造动作源 |
| 敏感识别 | 无（脱敏规则人工标注） | 新增 `SensitiveRule` + `SensitiveScanService` + 监控查询 | 扫描结果一键转 `CatalogMaskingRule`，与现有分类映射/脱敏联动，不替换现有脱敏执行 |
| 合规台账 | `SecurityBaselineService`（6 项笼统基线） | 扩展为按 BMB17.x 条款编号的可追溯检查项 + 测评整改工作流 | 台账承载证据归档，不等同测评结论；代码侧只提供支撑 |

## Feature 顺序

| ID | Feature | 优先级 | Task 数 | 状态 | 依赖 |
|----|---------|--------|---------|------|------|
| F1 | 口令策略与登录失败锁定 | P0 | 5 | READY | — |
| F2 | 会话安全 P0 整改 | P0 | 5 | READY | — |
| F3 | 操作权限矩阵 | P0 | 5 | DONE | — |
| F4 | 敏感数据自动识别引擎 | P1 | 5 | READY | — |
| F5 | BMB17.x 符合性映射与测评整改台账 | P0 | 5 | READY | F1, F2, F3 |
| F6 | 安全合规评审、回归与 IT 准入 | P0 | 5 | READY | F1-F5 |

**统计**: READY=25, IN_PROGRESS=0, DONE=5, BLOCKED=0

## 完成标准

- [ ] Keycloak realm 配置 `passwordPolicy`（长度 ≥12、大小写+数字+特殊字符、口令历史、有效期）+ `bruteForceProtected:true` + 失败锁定阈值；dts-admin 启动时校验策略存在，缺失则 fail-fast。
- [ ] 前端不再持久化裸 token（迁移到 httpOnly cookie 或受控存储），生产构建 strip 所有 `console.log(Authorization)`，`TEST_SESSION_ENABLED`/`handleDevFallback` 旁路在生产 profile 下硬关闭并有启动断言。
- [x] 操作权限矩阵：`IamAssetActionPolicy` 支持 资产/库表 × 角色/部门/用户 × {新增/删除/修改/复制/导入/导出/归档/销毁} 集中配置；各业务动作入口接入 `AccessChecker.canPerform(resource, action)`；前端数据安全页提供动作矩阵并通过独立审批流生效。
- [ ] 敏感识别：`SensitiveRule`（REGEX/DICTIONARY/AI）CRUD + `SensitiveScanService` 对 catalog 字段元数据 + 抽样数据扫描产出候选敏感字段；提供敏感数据监控查询视图；扫描建议可一键生成 `CatalogMaskingRule`。
- [ ] `SecurityBaselineService.DEFINITIONS` 由 6 项笼统基线扩展为按 BMB17.1/17.2-2024 条款编号的可追溯检查项；建立测评整改工作流（NOT_STARTED→IN_PROGRESS→DONE/WAIVED，支持两轮迭代）+ `exportReport` 产出测评整改证据包。
- [ ] 全部新增/改动代码 TDD：单元 + 集成测试先行，覆盖率 ≥80%；安全敏感改动经 security-reviewer 评审无 CRITICAL/HIGH。
- [ ] IT 证据覆盖：弱口令被拒、连续失败触发锁定、越权动作被 `canPerform` 拦截、敏感扫描命中样例字段、BMB 台账导出整改证据包。

## 非目标

- 不在本期实现密钥分级/轮换/KMS 与国密 SM2/SM3/SM4 替换（M10 P1，排 Backlog；本期仅在台账记录现状与整改计划）。
- 不实现备份实际执行引擎（M05 P1，排 Backlog）。
- 不实现脱敏「任务编排→执行→结果查看」完整闭环（M05 P1）；本期敏感识别只到"扫描→建议→转规则"，脱敏执行沿用现有查询期即时脱敏。
- 不触碰 M04 生命周期/销毁、M09 告警规则、M11 高可用/性能（排 Sprint-37/38）。
- 不重做已覆盖的 PKI/CA + USBKey 登录。

## TDD 执行约定

每个 Task 强制 RED→GREEN→REFACTOR：

1. **RED**：先写失败测试（单元/集成），明确断言协议要求的行为；运行确认 FAIL。
2. **GREEN**：写最小实现使测试通过。
3. **REFACTOR**：在测试保护下重构，保持绿灯。
4. **覆盖率**：模块 ≥80%；安全敏感路径（鉴权/口令/会话）要求分支覆盖。
5. **影响分析**：改任何既有 symbol 前先 `gitnexus_impact`，提交前 `gitnexus_detect_changes()`（见 CLAUDE.md）。
6. **Optional 规范**：Java 侧禁用 `Optional.get()`，统一 `orElseThrow()`（modernizer 强制）。

## 评审机制

1. **配置评审**（F1/F2）：确认口令策略/锁定阈值/会话整改符合机密级最低要求，且生产 profile 无旁路。
2. **权限模型评审**（F3）：确认动作枚举完整覆盖协议 8 动作，`canPerform` 在所有写动作入口生效，无默认放行。
3. **识别引擎评审**（F4）：确认扫描不外泄样本数据、结果可追溯、与脱敏规则联动闭环。
4. **合规评审**（F5）：确认 BMB17.x 条款映射可追溯、测评台账支持两轮迭代证据归档。
5. **安全评审**（F6）：security-reviewer 全量复审，无 CRITICAL/HIGH 方可准入。

## 相关材料

- 差距分析报告 v3: `worklog/v2.2.3/sprint-36-202606/assets/protocol-gap-analysis-v3.md`
- 逐模块证据底稿: `worklog/v2.2.3/sprint-36-202606/assets/gap-evidence/M01..M11.md`
- 协议转录全文: `dts.md`（仓库根）
- 前序架构评审: `worklog/v2.2.3/architecture-review.md`
- 前序 gap 计划: `worklog/v2.2.3/protocol-gap-plan.xlsx`
- sprint-22 会话评审（F2 输入）: `worklog/v2.2.3/sprint-22-202604/review/session-management-audit.md`
- 集成测试计划: `worklog/v2.2.3/sprint-36-202606/it/README.md`
