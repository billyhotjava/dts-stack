# F3: 敏感数据自动识别与密级/脱敏联动

**优先级**: P0（协议 2.3.2.5 明列；机密级测评必备）
**状态**: DRAFT（依赖 F0 交付基线）

## 目标
数据安全管理员对选定范围发起一次扫描，平台自动列出疑似敏感字段（身份证、手机号、银行卡、地址、姓名等）及其判定依据；管理员批量确认后，自动生成脱敏规则并在查询期生效，同时给出密级建议供人工采纳。

**闭合缺口**: M05 P1「敏感数据自动识别引擎」——协议明列能力，且是 BMB 测评中"是否知道自己有哪些敏感数据"的直接证据。
**现状**（账本#11）：全仓零实现；脱敏规则 100% 人工标注。

## 与密级控制面的边界（关键）

> **识别引擎只产出候选，不改密级**（ADR-99-03，domain-dts D1）。

平台已有完整密级控制面（账本#2～#5）：统一密级目录、入湖封存、准入校验、沿血缘只升不降传播。本 Feature **不得**绕过它：

| 允许 | 禁止 |
|------|------|
| 产出「建议密级」字段，人工确认后调既有 `CatalogClassificationService` 提交 | 扫描结果直接写 `classification` 字段 |
| 读 `SecurityLevelCatalog` 做展示与建议 | 自建一套敏感度分级枚举 |
| 确认后写既有 `CatalogMaskingRule` | 新建第二套脱敏规则表 |
| 以 `(asset_type, asset_key, column_name)` 锚定发现项（A3） | 为敏感数据另建资产表 |

## 契约定义 (Contracts)

| 类型 | 契约 | 关键字段/签名 |
|------|------|---------------|
| REST | `GET/POST/PUT/DELETE /api/security/sensitive/rules` | `{id, code, name, category(ID_CARD\|PHONE\|BANK_CARD\|EMAIL\|ADDRESS\|NAME\|CUSTOM), matchType(REGEX\|DICT\|COLUMN_NAME), pattern, confidence(0-100), suggestedMaskingStrategy, suggestedClassification, enabled}` |
| REST | `POST /api/security/sensitive/scans` | body: `{scope:{assetType, assetKeys[]}\|{domainId}, ruleCodes[]?, sampleRows:int(默认1000)}`；resp: `{scanId, status}` |
| REST | `GET /api/security/sensitive/scans/{scanId}` | `{scanId, status(RUNNING\|SUCCEEDED\|FAILED\|PARTIAL), scannedColumns, findingCount, startedAt, finishedAt, error}` |
| REST | `GET /api/security/sensitive/findings?scanId=&status=&assetKey=` | `{id, assetType, assetKey, columnName, ruleCode, matchedSamples[](脱敏后), hitRate, confidence, status(NEW\|CONFIRMED\|REJECTED), suggestedMaskingStrategy, suggestedClassification, currentClassification}` |
| REST | `POST /api/security/sensitive/findings/confirm` | body: `{findingIds[], applyMasking:bool, applyClassificationSuggestion:bool}`；resp: `{maskingRulesCreated, classificationRequestsSubmitted, skipped[]}` |
| 数据 | `catalog_sensitive_rule` | `id, code(uk), name, category, match_type, pattern, confidence, suggested_masking_strategy, suggested_classification, enabled, created_*` |
| 数据 | `catalog_sensitive_scan` | `id, scope_json, rule_codes, status, scanned_columns, finding_count, started_at, finished_at, error, created_by` |
| 数据 | `catalog_sensitive_finding` | `id, scan_id(fk), asset_type, asset_key, column_name, rule_code, hit_rate, confidence, samples_json(脱敏后), status, resolved_at, resolved_by`；uk(`scan_id`,`asset_type`,`asset_key`,`column_name`,`rule_code`)；idx(`asset_type`,`asset_key`) |
| 迁移 | `sprint99-sensitive-discovery.xml` | 3 张新表，Expand-only |

## UI/UX 规格

- **入口与导航**: 不新增菜单。数据安全页 `pages/security/data-security.tsx` 新增「敏感数据识别」页签。
- **布局线框**:
  ```
  ┌ 数据安全 ▸ 敏感数据识别 ─────────────────────────────┐
  │ [规则库] [扫描任务] [发现结果] ← 三个子页签           │
  │ ── 发现结果 ─────────────────────────────────────── │
  │ 范围[主题域▾] 规则[全部▾] 状态[待确认▾]  [发起扫描]  │
  │ ┌────────────────────────────────────────────────┐ │
  │ │☑ 资产 │ 字段 │规则│命中率│置信│当前密级│建议密级│ │
  │ │☑ 客户表│id_no│身份证│98%│ 高 │INTERNAL│SECRET │ │
  │ └────────────────────────────────────────────────┘ │
  │ [批量确认并生成脱敏规则] [提交密级建议] [忽略]       │
  └──────────────────────────────────────────────────────┘
  ```
- **四态**:
  - 空：未扫描过 → 空状态图 + "发起第一次扫描"主按钮
  - 加载：扫描进行中 → 进度条（已扫列数/总列数）+ 结果表骨架
  - 错误：扫描失败 → 显示失败原因与已扫描部分结果（PARTIAL 不丢已有发现）
  - 成功：结果表 + 汇总条（发现 N 项，涉及 M 张表）
- **关键交互**:
  - 「批量确认并生成脱敏规则」→ 二次确认弹窗列出将创建的规则数 → 成功后 toast 并跳转脱敏规则列表
  - 「提交密级建议」→ **不直接改密级**，弹窗说明"将提交密级变更建议，由密级控制面按只升不降规则处理"
  - 建议密级低于当前密级时该行禁用勾选 + tooltip"密级不可降级"（账本#4）
  - 样本值展示一律脱敏（`110***********1234`），**原文不得出现在前端**
- **分页**: 默认 10 条/页，切换条数刷新。
- **兼容**: Chrome95。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 敏感规则库领域模型与预置规则 | P0 | DRAFT | F0/T01 |
| T02 | 扫描引擎（正则+字典+列名启发） | P0 | DRAFT | T01 |
| T03 | 发现结果与确认闭环 | P0 | DRAFT | T02 |
| T04 | 脱敏规则生成与密级建议联动 | P0 | DRAFT | T03 |

## Definition of Ready
- [x] 契约已钉死  - [x] 竖切片已画通  - [x] UI 落点已命名  - [ ] 依赖 F0  - [x] 验收可验证

## 完成标准
- [ ] 预置 ≥6 类敏感规则，扫描能在真实 catalog 上跑出发现项
- [ ] 确认后自动创建 `CatalogMaskingRule` 并在查询期真实脱敏
- [ ] 密级建议走既有密级控制面，降级建议被拒绝
- [ ] 样本值全链路脱敏，原文不落前端、不落日志
