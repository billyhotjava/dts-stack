# Sprint-99: 协议合规硬门槛缺口闭合（第一波）

**时间盒**: 2026-08-24 ～ 2026-09-11（15 个工作日）
**状态**: DRAFT（G0 交付基线未过；F1 为纯配置/后端，可先行 READY）
**类型**: Security / Compliance / Observability / Implementation
**Owner / Reviewer**: xiezm
**目标**: 让甲方安全测评人员能在平台内**看到并导出**一份对齐 BMB17.1/17.2-2024 条款的符合性证据，其中口令策略、登录失败锁定、敏感数据识别、系统告警四项由平台**真实执行并自动判定**，而不是人工填写的截图台账。

## 背景与价值

`assets/protocol-gap-review-20260821.md`（v4 复核）实测结论：协议 13 项 P0 至今闭合 2 项（会话安全、操作权限矩阵），**M10 安全保密 4 项 P0 原封不动**。sprint-36 计划的合规专项状态仍为 `PLANNING`，从未执行；sprint-36→98 的产能全部投入建模/BI/资产域。

机密级（BMB17.1/17.2-2024）测评是整个项目的**验收前置条件**，测评不通过则其余功能无法验收。其中 P0-1/P0-2 是改一个 realm JSON 的配置项，滞留两个半月属计划失焦而非技术困难。

**不做的代价**：测评轮次一旦启动，四项最基础的控制项（口令、锁定、敏感数据识别、告警）现场不可证明，直接判不符合，项目交付停摆。

## 范围边界（本 Sprint 明确不做）

> **审批模型未定 → 一切审批流接线本 Sprint 不做。**

客户侧审批模型（谁审、几级、会签还是或签、是否走外部 OA）尚未确定。凡是需要「先定审批模型才能定契约」的缺口，本 Sprint 一律不接线，也不预先发明一套：

| 缺口 | 处置 |
|------|------|
| P0-10 文件维护审批流接线 | 移出，`BLOCKED-审批模型未定` |
| M04 元数据审核-发布工作流 | 移出，`BLOCKED-审批模型未定` |
| P0-7 生命周期各阶段的**审批入口** | 移出（生命周期的状态机/双态/统计部分排 Sprint-100） |
| F2 测评整改单的**多级审批** | 本 Sprint 只做「登记 + 责任人 + 状态流转 + 证据附件」，不做审批链 |

**留 seam 不留实现**：所有"需要有人点同意"的位置，本 Sprint 统一收敛为既有的 `CatalogLifecycleRequestService` request/decision 骨架（账本#12），由具备 `AssetAction` 权限的角色直接确认。待客户审批模型确定后，只在该 seam 后面接入，不改调用方。**禁止新建第二套审批表或审批服务**（domain-dts A4）。

## 架构决策记录 (ADR)

| # | 决策点 | 选择 | 理由 | 影响 |
|---|--------|------|------|------|
| ADR-99-01 | 审批模型未定时如何推进 | 只做「申请→登记→状态」，审批决策收敛到既有 lifecycle request seam | 客户模型未定，提前发明必返工；A4 禁平行实现 | F2/F3 不含审批链 |
| ADR-99-02 | BMB 符合性台账落在哪 | **扩展** `SecurityBaselineService` 现有 6 项检查，加 BMB 条款号 + 测评轮次，不新建模块 | 已有 `/api/security/baseline/checks` + `SecurityBaselineRemediation` 实体（账本#7） | 检查项从 6 扩到 BMB 条款级；`verifyMode` 由 MANUAL 升为 AUTO 的优先做 |
| ADR-99-03 | 敏感数据识别的密级关系 | 识别引擎**只产出候选**，密级判定权仍归 `SecurityLevelCatalog` / `CatalogClassificationPropagationService`，识别结果不得直接改密级 | domain-dts D1：密级是独立合规控制面；只升不降由传播服务保证（账本#4） | 识别结果 → 建议脱敏规则 + 建议密级，需人工确认后才写入 |
| ADR-99-04 | 识别引擎的扫描锚点 | 复用 `CatalogColumnSchema` + `(asset_type, asset_key)`，不为敏感数据另建资产表 | domain-dts A3 | 新表 `catalog_sensitive_rule` / `catalog_sensitive_finding` 均以 asset 锚点关联 |
| ADR-99-05 | 口令策略落点 | Keycloak realm `passwordPolicy` 为唯一事实源，应用侧只做启动期校验与前端提示，不自建口令规则 | 认证唯一 owner 是 Keycloak；自建会与 PKI 登录路径冲突 | 现场仅 PKI 登录的部署，策略仍需配置但不阻断启动（WARN） |
| ADR-99-06 | 告警栈选型 | Prometheus + Alertmanager，复用**已暴露**的 actuator `/management/prometheus`（账本#8），不引入 Grafana 之外的第二套采集 | 端点已就绪，增量最小 | compose 新增 2 个服务 + 1 份 rules 文件 |
| ADR-99-07 | 告警仪表盘 | Grafana 只读看板，随离线包交付；**不**在 dts-platform-webapp 里重画一套监控页 | domain-dts B：默认不新增菜单/页面 | 平台内只保留一个跳转入口 |

## 端到端契约链 (Vertical Slice)

### 主竖线：测评人员打开符合性报告 → 看到自动判定结果 → 导出证据包

| 层 | 契约/落点 | 签名要点 |
|----|-----------|----------|
| UI 入口 | 数据安全 → 安全基线页（既有 `pages/security/data-security.tsx`），新增「BMB 符合性」页签 | 表格：条款号 / 控制项 / 判定 / 判定方式 / 最近校验时间 / 整改状态；右上「导出证据包」 |
| API | `GET /api/security/baseline/checks?standard=BMB17.1`<br>`POST /api/security/baseline/checks/{checkKey}/verify`<br>`GET /api/security/baseline/report?format=zip` | resp: `{checkKey, clauseNo, title, category, severity, verifyMode, status(PASS/FAIL/NA/UNKNOWN), evidence[], lastVerifiedAt, remediation{owner,dueDate,state}}` |
| Service | `SecurityBaselineService`（扩展）+ 新增 `BaselineAutoVerifier` 策略集 | 每个 AUTO 项一个 verifier：读 realm 配置 / 读审计开关 / 读备份日志 / 读告警规则 → 产出 `status + evidence` |
| 数据 | `security_baseline_remediation`（既有，加列 `clause_no`、`standard_code`、`assessment_round`）+ 新表 `security_baseline_verification`（每次自动校验一条留痕） | uk(`standard_code`,`clause_no`,`assessment_round`)；idx(`verified_at`) |
| 迁移 | `dts-platform` changelog 新增 `sprint99-baseline-bmb.xml` | ALTER + CREATE，Expand-only，不删旧列 |

### 副竖线：口令策略生效 → 用户改密被拒 → 平台自检显示 PASS

| 层 | 契约/落点 |
|----|-----------|
| 配置 | `services/dts-keycloak/realm-dts.json`：`passwordPolicy` + `bruteForceProtected:true` |
| 校验 | `dts-admin` 启动期 `KeycloakSecurityPolicyValidator` → 不符合则 WARN + 写审计 |
| 判定 | `BaselineAutoVerifier(SEC_BASELINE_ACCOUNT_PASSWORD)` 读 realm 实况产出 PASS/FAIL |
| UI | 登录/改密页展示策略要求文案（从 `GET /api/admin/security/password-policy` 取） |

### 副竖线：敏感识别扫描 → 候选清单 → 人工确认 → 落脱敏规则

| 层 | 契约/落点 |
|----|-----------|
| UI | 数据安全 → 「敏感数据识别」页签：规则库 / 发起扫描 / 候选结果 / 批量确认 |
| API | `POST /api/security/sensitive/scans`、`GET /api/security/sensitive/findings`、`POST /api/security/sensitive/findings/{id}/confirm` |
| Service | `SensitiveScanService`（正则+字典+列名启发）→ `SensitiveFinding` |
| 数据 | `catalog_sensitive_rule`、`catalog_sensitive_finding`（锚点 `asset_type + asset_key + column_name`） |
| 下游 | 确认后写 `CatalogMaskingRule`（既有）+ **建议**密级提交给 `CatalogClassificationService`（人工确认，ADR-99-03） |

## 现状勘察账本 (Context Ledger)

> 一次勘察的全部事实，下游 task 引用条目号，**禁止重复扫描**。

| # | 事实 | 证据(文件:行) |
|---|------|---------------|
| 1 | realm 无 `passwordPolicy` 字段；`bruteForceProtected:false`、`permanentLockout:false`、`failureFactor:30`、`maxFailureWaitSeconds:900` | `services/dts-keycloak/realm-dts.json` |
| 2 | 统一密级目录：人员 GENERAL/IMPORTANT/CORE，数据 PUBLIC/INTERNAL/SECRET/CONFIDENTIAL，默认数据密级 INTERNAL，含数字/中文/legacy 别名归一 | `dts-common/.../security/SecurityLevelCatalog.java:34-46` |
| 3 | 入湖密级封存：`requireProductionSeal` 校验 sealId/subjectType/subjectKey/effectiveLevel/snapshotVersion/checksum | `dts-ingestion/.../IngestionClassificationSealGuard.java:19-44` |
| 4 | 密级沿血缘取最高值传播（只升不降）；血缘成环时保留最后已知最高密级 | `CatalogClassificationPropagationService.java:111`（`SecurityLevelCatalog.maxDataCode`）、`:65` |
| 5 | 密级准入：`CatalogClassificationAdmissionService.requireValid(SealReference)` | `CatalogClassificationAdmissionService.java:16` |
| 6 | 操作权限矩阵已闭合：`AssetAction` 八动作（CREATE/DELETE/UPDATE/COPY/IMPORT/EXPORT/ARCHIVE/DESTROY）+ `/api/iam/action-policies/matrix`、`/requests`、`/requests/{id}/decision` + 前端 `AssetActionMatrixPanel.tsx` | `security/policy/AssetAction.java:9-17`、`AssetActionPolicyResource.java:39,57,72,96` |
| 7 | 安全基线既有 6 项检查（账号口令/会话超时/接口鉴权/审计留痕/备份恢复/TLS），字段 `checkKey,title,category,severity,verifyMode(MANUAL\|AUTO),description,expectation`；端点 `/api/security/baseline/checks`、`PUT /checks/{checkKey}`、`GET /report`；整改实体 `SecurityBaselineRemediation` | `service/security/baseline/SecurityBaselineService.java:26-75`、`web/rest/SecurityBaselineResource.java:22,37,48,64` |
| 8 | actuator 已暴露 prometheus 端点（`management.endpoints.web.exposure.include` 含 prometheus，`metrics.export.prometheus` 已配） | `dts-platform/.../application.yml:73,98`、`application-prod.yml:30` |
| 9 | 全仓无 `prometheus.yml` / `rule_files` / `alerting:`；`services/` 下无 alertmanager、无 grafana | 实测 grep 零命中 |
| 10 | 列元数据锚点 `CatalogColumnSchema`；脱敏规则实体 `CatalogMaskingRule`，端点 `/api/catalog/masking-rules`（GET/POST/PUT/DELETE/preview）+ `/classification-mapping` | `domain/catalog/CatalogColumnSchema.java`、`web/rest/catalog/CatalogMaskingResource.java:26,52-107` |
| 11 | 无任何敏感数据识别实现（`SensitiveRule|SensitiveScan` 全仓零命中）；脱敏规则全人工标注 | 实测 grep 零命中 |
| 12 | 平台内唯一审批 seam：`CatalogLifecycleRequestService`（request/decision，类型仅 ARCHIVE/DISPOSE/EXTEND，`:409`）+ `CatalogDatasetAccessApprovalResource`；`DataStandardAttachmentService` 无任何 approval 调用 | `service/catalog/CatalogLifecycleRequestService.java:336,409` |
| 13 | 会话安全已闭合：登录只存 `{authenticated, tokenExpiresAt}`；生产构建 `drop:["console","debugger"]`；TEST_SESSION 需 `!PROD && localhost && 显式开关`；devFallback 需 `import.meta.env.DEV` + 白名单 host + flag | `login-form.tsx:381`、`vite.config.ts:363`、`apiClient.ts:33`、`userStore.ts:281,285` |
| 14 | 上传明文落盘已闭合：`FileUploadService` 密文存储 + compose 将 multipart location 与 `java.io.tmpdir` 重定向至 tmpfs(mode=1700) | `dts-ingestion/.../etl/FileUploadService.java`、`docker-compose-app.yml:812,1380` |
| 15 | 全部 22 个 compose 服务**零 `deploy.replicas`**：单 PG、单 dts-proxy、单 Kafka、Airflow 单 scheduler | `docker-compose-app.yml` |
| 16 | 审计动作须在 dts-admin 审计资源字典登记，否则落「未分类」 | domain-dts D2 |

**开放问题**（实施期须确认，不得自行假设）：
- 甲方对口令策略的具体数值要求（长度/复杂度/有效期/历史条数/锁定阈值）——需现场确认后写入 realm，本 Sprint 先按 BMB 机密级常见基线取值并标注「待甲方确认」。
- 现场是否为「仅 PKI 登录、不可本地建用户」的部署形态（sprint-78 ADR-78-09 已在部分现场确认）——影响 F1/T03 是阻断还是告警。
- 测评机构的证据包格式要求（目录结构/命名/是否需签章）——影响 F2/T04 导出结构。

## Gate Registry

| Gate | 项目 | 状态 | 证据 | 未过则关联 Task |
|------|------|------|------|-----------------|
| G0 | 交付基线（可运行实例 + 登录 + Chrome95） | **PENDING** | `it/baseline.md` | F0/T01 |
| G0 | 领域与数据画像 | PASS | 本文档 §Context Ledger（密级控制面已实测确认） | - |
| G0 | 领域不变量自检（domain-dts） | PASS | ADR-99-01/03/04/07 分别对应 A4 / D1 / A3 / B | - |
| G1 | 契约链贯通 | PASS | 本文档 §端到端契约链 | - |
| G1 | 非功能预算 | GAP | 缺 `assets/nfr-budget.md`：告警规则的阈值与告警风暴抑制未定量 | F4/T02 |
| G3 | 发布安全 | PENDING | `assets/release-plan.md` 待写（realm 变更需回滚预案） | F1/T01 |
| G4 | 可运维性 | PENDING | `assets/runbook.md` 待写（告警值班与静默流程） | F4/T03 |
| G4 | DoD 验收 | PENDING | `it/` | - |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 | 闭合缺口 |
|----|---------|---------|--------|------|----------|
| F0 | 交付基线与缺口冻结 | 1 | P0 | READY | — |
| F1 | 口令策略与登录失败锁定 | 3 | P0 | READY | P0-1、P0-2 |
| F2 | BMB 符合性映射与测评台账 | 4 | P0 | DRAFT（依赖 F0） | P0-4、P0-5 |
| F3 | 敏感数据识别与密级脱敏联动 | 4 | P0 | DRAFT（依赖 F0） | M05 P1（测评必备） |
| F4 | 系统监控告警与业务仪表盘 | 3 | P0 | DRAFT（依赖 F0） | P0-11、M09 P1 |

**依赖顺序**: F0 → F1（可与 F0 并行，纯配置）→ F2 → F3 → F4；F3 与 F4 无相互依赖，可并行。

## 追溯矩阵 (Traceability)

| 需求点 | 协议条款 | Feature | 关键 Task | 验收证据 |
|--------|---------|---------|-----------|----------|
| 强密码策略 | 2.3.2.10 / BMB17.1 | F1 | F1/T01、T03 | `it/` IT-01 |
| 登录失败锁定 | 2.3.2.10 / BMB17.1 | F1 | F1/T02 | `it/` IT-02 |
| BMB17.x 符合性映射 | 2.3.2.10 | F2 | F2/T01、T02 | `it/` IT-03 |
| 第三方测评整改台账 | 2.3.2.10 | F2 | F2/T03、T04 | `it/` IT-04 |
| 敏感数据自动识别 | 2.3.2.5 | F3 | F3/T01～T03 | `it/` IT-05 |
| 脱敏规则联动 | 2.3.2.5 | F3 | F3/T04 | `it/` IT-06 |
| 自定义告警规则 | 2.3.2.9 | F4 | F4/T01、T02 | `it/` IT-07 |
| 业务监控仪表盘 | 2.3.2.9 | F4 | F4/T03 | `it/` IT-08 |

## 完成标准

- [ ] realm 口令策略与失败锁定生效，且**用弱口令改密被真实拒绝**、连续失败被真实锁定（非配置截图）
- [ ] 安全基线页出现 BMB 条款级检查项，其中 ≥4 项为 AUTO 自动判定，判定结果可复现
- [ ] 证据包可下载，解压后目录结构完整、每条 AUTO 项含机器产生的证据文件
- [ ] 敏感识别扫描能在真实 catalog 上跑出候选，确认后落 `CatalogMaskingRule` 并在查询期生效
- [ ] 告警规则触发后 Alertmanager 真实收到并可查询；Grafana 看板显示平台业务指标
- [ ] 四态（空/加载/错误/成功）UI 证据入 `it/evidence/`，无占位

## 非目标

- 一切审批流接线（见 §范围边界）
- 高可用集群与全平台压测（P0-12、P0-13）→ Sprint-101
- 数据生命周期 6 阶段与销毁双态（P0-7、P0-8 的非审批部分）→ Sprint-100
- DOC 旧格式与在线预览（P0-9）→ Sprint-100
- M02/M06/M07/M08/M10/M11 的 P1 增强 → 见 `assets/protocol-gap-register.md`
- 不新增顶级菜单，不重画监控页（ADR-99-07）
