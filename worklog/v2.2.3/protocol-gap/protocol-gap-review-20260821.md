# 技术协议（dts.pdf）对账复核 — v4（增量）

> 复核日期：2026-08-21
> 协议基准：`docs/req/dts.pdf`「数据管理平台 I 期」2.3.2（10 功能模块）+ 2.3.3（非功能）
> 对账底稿：`worklog/v2.2.3/sprint-36-202606/assets/protocol-gap-analysis-v3.md` + `assets/gap-evidence/M01..M11`
> 代码基线：branch `v2.2.3` @ `ec8da3022`（工作树实测，非文档声明）
> 方法：对 v3 的 13 P0 + 29 P1 逐项回源码复验；再核 sprint-36~98 的实际落地面

---

## 0. 净变化摘要

| | v3（2026-06-08） | v4（2026-08-21） | 变化 |
|---|---|---|---|
| P0 | 13 | **11** | 闭合 2（P0-3、P0-6） |
| P1 | 29 | **25** | 闭合 4（数据标签、API 入湖、上传明文落盘*、平台级备份恢复） |

\* 上传明文落盘是 v3 之后现场新识别的 P0 级事实（Sprint-37），已闭合，故不计入原 13 项。

**核心判断：两个半月（sprint-36 → 98，共 63 个 sprint 编号）的产能几乎全部投入「建模 / BI / 资产目录」三个域，协议 roadmap 中排定的安全合规（Sprint-36）、可观测性与高可用（Sprint-39 建议）两条主线从未执行。**

- `sprint-36-202606/README.md` 状态仍为 `PLANNING`，6 个 Feature 无一落地（仅 F3 操作权限矩阵后来被单独实现）。
- 机密级测评（BMB17.1/17.2-2024）相关的 5 项 P0 **原封不动**——这是整个项目的验收硬门槛。
- sprint-94~98 交付的治理型 BI、数据门户、资产目录，属协议 M08/M07 的**增强**，不抵消上述 P0。

---

## 1. P0 逐项状态（11 项未闭合 / 2 项闭合）

| # | 模块 | 缺口 | 状态 | 现场证据（2026-08-21 实测） |
|---|------|------|------|------|
| P0-1 | M10 | 强密码策略 | 🔴 未闭合 | `services/dts-keycloak/realm-dts.json` 仍无 `passwordPolicy` 字段 |
| P0-2 | M10 | 登录失败锁定 | 🔴 未闭合 | 同 realm：`"bruteForceProtected": false`、`"permanentLockout": false` |
| P0-3 | M10 | 会话安全（sprint-22 遗留） | 🟢 **已闭合** | 登录只存 `{authenticated, tokenExpiresAt}`（`login-form.tsx:381`），token 不再落 localStorage；`vite.config.ts:363` 生产 `drop: ["console","debugger"]`；`apiClient.ts:33` TEST_SESSION 收紧为 `!PROD && localhost && 显式开关`；`userStore.ts:281` devFallback 需 `import.meta.env.DEV` + 白名单 host + 显式 flag |
| P0-4 | M10 | BMB17.1/17.2 符合性映射 | 🔴 未闭合 | 全源码 grep `BMB17` **零命中**，仅存在于 sprint-36 计划文档 |
| P0-5 | M10 | 第三方测评 + 整改闭环台账 | 🔴 未闭合 | 同上，无实体、无服务、无前端 |
| P0-6 | M05 | 操作权限矩阵 | 🟢 **已闭合** | `security/policy/AssetAction.java` 8 动作（新增/删除/修改/复制/导入/导出/归档/销毁）；`AssetActionPolicyService` + `/api/iam/action-policies/matrix` + 申请/审批端点；前端 `pages/security/AssetActionMatrixPanel.tsx`；`OperationPermissionMatrixIT` 有 IT |
| P0-7 | M04 | 生命周期 6 阶段闭环 + 专项监控 | 🔴 未闭合 | `CatalogLifecycleRequestService.java:409` 仍只允许 `ARCHIVE / DISPOSE / EXTEND` 三类；创建/存储/使用/共享无统一审批入口；无在用/共享/归档/销毁数据量统计 |
| P0-8 | M04 | 销毁临时/永久双态 + 一键还原 | 🔴 未闭合 | `TYPE_DISPOSE → AssetAction.DELETE` 软禁用（:336），无 temp-trash / hard-delete 双态、无回收站 restore |
| P0-9 | M01 | DOC 旧格式 | 🔴 未闭合 | `application.yml:480` `allowed-extensions: docx,wps,pdf,xlsx,xls,md,txt`（无 `doc`）；`AttachmentSignatureValidator` 同 |
| P0-10 | M01 | 文件维护审批流接线 | 🔴 未闭合 | 平台内唯一审批消费方仍是 `CatalogDatasetAccessApprovalResource`；`DataStandardAttachmentService` 无任何 approval 调用 |
| P0-11 | M09 | 自定义告警规则 | 🔴 未闭合 | 全仓无 `prometheus.yml` / `rule_files` / `alerting:`；`services/` 下无 alertmanager、无 grafana |
| P0-12 | M11 | 高可用集群 + 负载均衡 | 🔴 未闭合 | `docker-compose-app.yml` 22 个服务**零 `deploy.replicas`**：单 PG、单 Traefik(dts-proxy)、单 Kafka、Airflow 单 scheduler |
| P0-13 | M11 | 协议性能指标压测报告 | 🔴 未闭合 | 仅 sprint-11/20 的局部 perf-report；无覆盖「1000 在线 / 200 并发 / 单表<3s / 复杂<6s / 500 QPS」的全平台达标报告 |

### 新闭合的现场 P0（v3 之后识别）

| 项 | 状态 | 证据 |
|---|---|---|
| 入湖上传文件明文落宿主机磁盘 | 🟢 已闭合 | `dts-ingestion/service/etl/FileUploadService.java` 密文存储；`docker-compose-app.yml:812,1380` multipart location + `java.io.tmpdir` 重定向至 `tmpfs`（mode=1700）——Sprint-37 H1/H2 落地，注意 README 仍写 PLANNING |

---

## 2. P1 状态（25 项未闭合 / 4 项闭合）

### 已闭合

| 模块 | 项 | 证据 |
|---|---|---|
| M04 | 数据标签管理 | `CatalogTag` / `CatalogTagCategory` / `CatalogAssetTag` 全套（目录+预置库+打标+按标签检索+迁移+审计守卫），sprint-71/97 落地 |
| M02 | API 数据源入湖 | `IngestionTaskService:1361` API 连接器、`sourceKind=API`、`ApiAuthProviderRegistry`（sprint-38） |
| M05 | 备份实际执行 | `bin/dts-backup` + `logs/backup/dts-backup-restore-check.log`（已验证恢复，sprint-78 F4）——**注意**：这是平台级 PG 备份，`SecurityBackupPlan/Run` 业务台账仍未接执行引擎 |
| M03 | （v3 已 covered，无退化） | — |

### 仍未闭合（按模块）

- **M01 数据规划**：流程模板 seed 缺失；DOC/DOCX/PDF 在线预览缺失；文件分组/文件夹缺失；主题四级语义未明确；制度规范生命周期无审批闭环。
- **M02 采集清洗**：MQ（Kafka/RocketMQ）采集源；Webhook 接收端点；JSON/日志文件离线采集；**实时流采集仍全禁用**（`ConnectorCapabilityService.java:83` `CDC → enabled:false`）；**数据填报子系统整模块缺失**（grep 零命中）；图形化细粒度算子（去重/合并/拆分/连接/统计/排序）。
- **M04 数据管理**：元数据审核-发布工作流（无 `MetadataReview` 实体，REVIEW 仍是展示标签）。
- **M05 数据安全**：**敏感数据自动识别引擎完全缺失**（无扫描规则/引擎/结果表，`SensitiveRule|SensitiveScan` 零命中）；脱敏任务编排闭环（`CatalogMaskingResource` 仍只做查询期即时脱敏）；业务级备份执行引擎。
- **M06 数据质量**：主动预警推送通道缺失（无 `JavaMailSender`/webhook notifier/站内信，全仓零命中）；问题工单主动通知；标准→质量规则事件式同步。
- **M07 数据服务**：`/openapi` 只有**查询**一类（`OpenApiResource` 仅 `POST /{code}`、`/batch`、`/health`），协议要求的元数据/更新/字典三类对外接口未建；数据更新对外面缺失。
- **M08 分析可视化**：节点编程三件套（触发器/转换器/定时器）缺失；报表 **Word/docx 导出零实现**（全仓无 `XWPFDocument`/`docx4j`）。下钻交互已由 sprint-66 通用化，属部分改善。
- **M09 监控告警**：业务级监控仪表盘（无 Grafana/SigNoz，仅 JHipster JVM 脚手架）；审计「异常原因」结构化错误码列。
- **M10 安全保密**：密钥分级+轮换；国密 SM2/SM3/SM4 与 GM TLS——现仅 `PkiVerificationService` 用到 SM2 **验签**，无 SM4 存储加密、无国密 TLS。
- **M11 非功能**：多架构（amd64/arm64）镜像构建证据（无 `buildx`/`--platform`）；信创真机兼容测试报告；交付级《测试计划》《测试报告》主文档 + BUG 回归台账。

---

## 3. 结构性风险

1. **验收硬门槛零进展**。M10 的 4 项 P0（强密码、失败锁定、BMB 映射、测评台账）全部未动，机密级测评不通过则其余功能无法验收。其中 P0-1/P0-2 是**改 realm JSON 的配置项**，成本以小时计，滞留两个半月属计划失焦而非技术困难。
2. **文档状态与代码状态背离**。Sprint-36/37 README 均标 `PLANNING`，但 Sprint-37 的加密与 tmpfs 实际已落地、Sprint-36 的 F3 权限矩阵也已落地。反向亦有：sprint-72（生命周期/密级传播）标 IN_PROGRESS，但 v2.2.3 工作树里生命周期仍是三类型旧实现。**以 README 判断闭合会得出错误结论，须回源码。**
3. **可观测性与高可用是「无人认领的 P0」**。P0-11/12/13 需环境与架构投入，历次 sprint 都排在别处，至今无 owner。这三项同时是协议 2.3.3 的硬指标。

---

## 4. 建议排期（不改变现有 BI 主线的前提下）

| 优先级 | 内容 | 成本估计 |
|---|---|---|
| **立即（本周）** | P0-1 + P0-2：realm 加 `passwordPolicy`（长度/复杂度/历史/有效期）+ `bruteForceProtected:true`、`permanentLockout`、`failureFactor` 下调；补启动期校验 | 1 人日 |
| **P0 专项 A（1 sprint）** | P0-4 + P0-5 BMB17.x 符合性映射与测评整改台账（扩展 `SecurityBaselineService`）+ M05 敏感数据自动识别引擎 | 1 sprint |
| **P0 专项 B（1 sprint）** | P0-7 + P0-8 生命周期 6 阶段与销毁双态 + P0-9/P0-10 文件合规与审批接线 | 1 sprint |
| **P0 专项 C（1~2 sprint，需环境）** | P0-11 告警规则 + Alertmanager + 业务仪表盘；P0-12 PG 主从/PgBouncer、多副本 + LB、Airflow Celery、Kafka 多 broker；P0-13 全平台压测达标报告 | 1~2 sprint + 环境预算 |
| **Backlog** | M02 数据填报子系统与 MQ/Webhook 采集、M06 通知推送统一能力、M07 接口聚合层、M08 Word 导出与节点编程三件套、M10 国密与密钥分级、M11 多架构镜像与测试主文档 | 按业务优先级穿插 |

> 复核依据均为工作树实测（文件路径 + 行号），可逐条回验。旧底稿见 `sprint-36-202606/assets/gap-evidence/M01..M11`。
