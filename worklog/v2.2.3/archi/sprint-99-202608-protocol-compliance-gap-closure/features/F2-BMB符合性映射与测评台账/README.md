# F2: BMB 符合性映射与测评整改台账

**优先级**: P0（协议 2.3.2.10；机密级测评验收前置）
**状态**: DRAFT（依赖 F0 交付基线）

## 目标
甲方测评人员打开安全基线页，能按 BMB17.1/17.2-2024 条款号逐条看到平台的符合性判定，其中至少 4 项由平台自动校验产出机器证据；发现不符合项后可登记整改责任人、期限与整改证据，并按测评轮次导出一份完整证据包。

**闭合缺口**: P0-4（BMB17.x 符合性映射缺失）、P0-5（第三方测评对接 + 整改闭环台账缺失）。
**现状**（账本#7）：`SecurityBaselineService` 有 6 项笼统检查，5 项 `verifyMode=MANUAL`，未对齐任何 BMB 条款；`SecurityBaselineRemediation` 实体存在但无测评轮次概念。

## 范围边界
- **不做审批链**（ADR-99-01）：整改单只做「登记 → 指派责任人 → 状态流转 → 附证据」，谁有权关闭整改单由 `AssetAction` 权限判定，不引入多级审批。
- **不做测评机构的在线对接**：协议原文的"第三方测评对接"在本 Sprint 落为「按轮次组织证据 + 导出可交付证据包」，机构侧系统对接待其接口明确后另行立项。

## 契约定义 (Contracts)

| 类型 | 契约 | 关键字段/签名 |
|------|------|---------------|
| REST | `GET /api/security/baseline/checks?standard=BMB17.1&round={n}` | resp[]: `{checkKey, standardCode, clauseNo, title, category, severity, verifyMode, status, evidence[], lastVerifiedAt, remediation}` |
| REST | `POST /api/security/baseline/checks/{checkKey}/verify` | 触发一次 AUTO 校验；resp: `{status, evidence[], verifiedAt}`；MANUAL 项返回 409 |
| REST | `PUT /api/security/baseline/checks/{checkKey}`（既有，扩展） | body 增加 `clauseNo`、`assessmentRound`、`evidenceRefs[]` |
| REST | `GET /api/security/baseline/report?standard=BMB17.1&round={n}&format=zip` | 证据包（zip），非 zip 时回退既有 JSON 报告 |
| 数据 | `security_baseline_remediation`（既有，ALTER） | 新增 `standard_code varchar(32)`、`clause_no varchar(32)`、`assessment_round int` |
| 数据 | `security_baseline_verification`（新建） | `id, check_key, standard_code, clause_no, assessment_round, status, evidence_json, verified_at, verified_by`；uk(`check_key`,`assessment_round`,`verified_at`)；idx(`standard_code`,`clause_no`) |
| 迁移 | `sprint99-baseline-bmb.xml` | Expand-only：ALTER ADD COLUMN（可空）+ CREATE TABLE，不删不改既有列 |

## UI/UX 规格

- **入口与导航**: **不新增菜单**。在既有数据安全页 `pages/security/data-security.tsx` 增加「BMB 符合性」页签（与既有「安全基线」并列或替代其内容，实施时按现页签结构确定，不另开页面）。
- **布局线框**:
  ```
  ┌ 数据安全 ▸ BMB 符合性 ──────────────────────────────┐
  │ 标准[BMB17.1-2024 ▾] 轮次[第1轮 ▾]  [重新校验] [导出证据包] │
  │ ┌ 汇总条 ──────────────────────────────────────────┐ │
  │ │ 符合 12 · 不符合 3 · 部分符合 2 · 不适用 1        │ │
  │ └──────────────────────────────────────────────────┘ │
  │ ┌ 条款表（默认 10 条/页，切页大小刷新）────────────┐ │
  │ │ 条款号 │ 控制项 │ 判定 │ 方式 │ 校验时间 │ 整改 │ │
  │ │ 5.2.1  │口令策略│ ✓符合│ AUTO │ 08-24 10:12│  -  │ │
  │ │ 5.2.3  │失败锁定│ ✗不符│ AUTO │ 08-24 10:12│登记 │ │
  │ └──────────────────────────────────────────────────┘ │
  └──────────────────────────────────────────────────────┘
  ```
- **四态**: 空（该标准无条款 → 引导先执行 seed）/ 加载（表格骨架）/ 错误（校验接口失败 → 行内显示"校验失败"并保留上次结果）/ 成功（判定图标 + 证据可点开）。
- **关键交互**:
  - 点「重新校验」→ 逐条调 `verify`，AUTO 项实时刷新判定；MANUAL 项跳过并提示"需人工确认"
  - 点某行「证据」→ 抽屉展示机器证据原文（如 realm 策略串、备份日志片段）
  - 点「登记整改」→ 抽屉填责任人/期限/说明 → 保存后该行整改列显示状态徽标
  - 点「导出证据包」→ 后台生成 zip，前端显示进度并下载
- **分页约定**: 默认 10 条/页，切换条数必刷新（domain-dts B 分页统一约定）。
- **兼容**: Chrome95。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | BMB17.x 条款字典与检查项映射 | P0 | DRAFT | F0/T01 |
| T02 | 自动校验器（AUTO 判定 + 机器证据） | P0 | DRAFT | T01、F1/T03 |
| T03 | 测评轮次与整改登记（无审批链） | P0 | DRAFT | T01 |
| T04 | 证据包导出与 UI 接入 | P0 | DRAFT | T02、T03 |

## Definition of Ready
- [ ] 契约已钉死（REST + 表结构已写死，但 **BMB 条款清单需实施期录入**，见风险）
- [x] 竖切片已画通
- [x] UI 落点已命名（数据安全页新增页签，不新增菜单）
- [ ] 依赖已就绪（依赖 F0 基线、F1/T03 Validator）
- [x] 验收可验证

> **DoR 未过原因**：条款清单来源需确认——BMB17.1/17.2-2024 全文非公开，实施期须由甲方/测评机构提供条款目录。**在拿到条款目录前 T01 保持 DRAFT**，可先做表结构与"标准/轮次"骨架。

## 完成标准
- [ ] 条款表按标准+轮次可查，判定与证据均可追溯到机器产物
- [ ] ≥4 项 AUTO 判定（口令策略 / 失败锁定 / 审计开启 / 备份可恢复）
- [ ] 整改登记闭环：登记 → 指派 → 补证据 → 关闭，全程有审计
- [ ] 证据包解压后目录完整，测评人员可直接使用
