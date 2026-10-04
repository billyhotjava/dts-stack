# 技术协议（dts.pdf）对账复核 — v5（增量）

> 复核日期：2026-08-29
> 协议基准：`docs/req/dts.pdf`「数据管理平台 I 期」2.3.2（10 功能模块）+ 2.3.3（非功能）
> 上一版：`protocol-gap-review-20260821.md`（v4，基线 `ec8da3022`）
> 本次代码基线：branch `v2.2.3` @ `0b51fdce1`（工作树实测）
> 方法：对 v4 的 11 项未闭合 P0 逐条回源码复验；P1 按可判定项抽验；再核 sprint-99/102/103 的实际落地面

---

## 0. 净变化摘要

| | v4（08-21） | v5（08-29） | 变化 |
|---|---|---|---|
| P0 未闭合 | 11 | **11** | **0** |
| P1 未闭合 | 25 | **25** | **0**（抽验 13 项，全部无变化） |

**8 天 26 次提交，无一条落在协议缺口上。** 提交全部集中在建模关系图、dbt 模型包兼容、大屏下钻/创建人、任务编排落地页——属 M08/M04 的增强，不抵消任何 P0。

v4 提出的「P0-1/P0-2 成本以小时计」建议未被采纳：Sprint-99 已按该建议建册（4 Feature / 15 Task），但**状态仍为 `DRAFT`，`it/evidence/` 为空目录，零行代码**。这两项配置级缺口至此已滞留约三个月。

---

## 1. P0 逐项复验（11 项，全部维持未闭合）

| # | 模块 | 缺口 | 状态 | 08-29 实测证据 |
|---|---|---|---|---|
| P0-1 | M10 | 强密码策略 | 🔴 未闭合 | `services/dts-keycloak/realm-dts.json` 仍无 `passwordPolicy` 字段 |
| P0-2 | M10 | 登录失败锁定 | 🔴 未闭合 | 同 realm `:40-49`：`bruteForceProtected: false`、`permanentLockout: false`、`failureFactor: 30` |
| P0-4 | M10 | BMB17.1/17.2 符合性映射 | 🔴 未闭合 | 全 `source/` grep `BMB17` **0 命中** |
| P0-5 | M10 | 第三方测评 + 整改台账 | 🔴 未闭合 | 同上，无实体/服务/前端 |
| P0-7 | M04 | 生命周期六阶段 + 专项监控 | 🔴 未闭合 | `CatalogLifecycleRequestService.java:51-53` 仍只有 `ARCHIVE / DISPOSE / EXTEND_RETENTION` |
| P0-8 | M04 | 销毁临时/永久双态 + 一键还原 | 🔴 未闭合 | `:336` `TYPE_DISPOSE → AssetAction.DELETE` 软禁用；`TEMP_DISPOSE\|PERMANENT_DISPOSE\|RecycleBin` **0 命中** |
| P0-9 | M01 | DOC 旧格式 | 🔴 未闭合 | `application.yml:480` `allowed-extensions: docx,wps,pdf,xlsx,xls,md,txt`（无 `doc`）|
| P0-10 | M01 | 文件维护审批流接线 | 🔴 未闭合 | `DataStandardAttachmentService` 零 `approval` 调用 |
| P0-11 | M09 | 自定义告警规则 | 🔴 未闭合 | 三处 `prometheus.yml` 均为 JHipster 脚手架，**无 `rule_files`、无 `alerting:`**；全仓无告警规则文件；`services/` 下无 alertmanager / grafana |
| P0-12 | M11 | 高可用集群 + 负载均衡 | 🔴 未闭合 | `docker-compose-app.yml` 22 个服务 **`replicas` 出现次数 = 0**；Airflow 四处仍 `LocalExecutor`；单 PG / 单 Traefik / 单 Kafka |
| P0-13 | M11 | 协议性能指标压测报告 | 🔴 未闭合 | 仅 sprint-11/20 局部 perf-report；无覆盖「1000 在线 / 200 并发 / 单表<3s / 复杂<6s / 500 QPS」的全平台报告 |

> P0-3（会话安全）、P0-6（操作权限矩阵）维持 v4 的已闭合结论，本次未复验。

---

## 2. P1 抽验（13 项，全部维持未闭合）

| 模块 | 项 | 实测 |
|---|---|---|
| M02 | Webhook 接收端点 | `webhook` 在 `source/**/*.java` **0 命中** |
| M02 | MQ（Kafka/RocketMQ）采集源 | `sourceKind` 无 kafka/mq 分支，**0 命中** |
| M02 | 数据填报子系统 | `DataFilling\|数据填报` **0 命中**（整模块缺失）|
| M02 | 实时流采集（CDC）| 见 §3-N2，**实质仍不可用** |
| M04 | 元数据审核-发布工作流 | `MetadataReview` **0 命中** |
| M05 | 敏感数据自动识别 | `SensitiveRule\|SensitiveScan` **0 命中** |
| M05 | 脱敏任务编排 | `MaskingJob\|MaskingTask` **0 命中** |
| M06 | 主动预警推送通道 | `JavaMailSender` **0 命中**；见 §3-N3 |
| M06 | 标准→质量规则事件式同步 | `StandardQualitySync\|standardToRule` **0 命中** |
| M07 | 对外接口聚合层 | `OpenApiResource` 仍只有 `POST /{code}`、`/{code}/batch`、`GET /{code}/health` 三个查询端点；元数据/更新/字典三类未建 |
| M08 | 报表 Word/docx 导出 | `XWPFDocument\|docx4j` **0 命中** |
| M10 | SM4 存储加密 | `SM4` **0 命中**（现仅 PKI 侧 SM2 验签）|
| M11 | 多架构镜像构建 | `builds/dts-build.sh:229-263` 只是**运行时探测** `--platform` 用于 pull maven 基础镜像，**不是 amd64/arm64 交叉构建流水线**；全仓无 `buildx` |

---

## 3. 本次新发现

### N1 —（需客户决策）Sprint-103 的产品裁决与协议 M02「图形化细粒度算子」方向相反

协议 M02 要求图形化的细粒度算子编排（去重 / 合并 / 拆分 / 连接 / 统计 / 排序 / 批量加载）。

Sprint-103 的裁决是反向的：
- `README.md:32` — 编辑事实源改为类型化任务配置，**`graphDsl` 降为历史兼容数据、不驱动执行**；
- 范围表 — 「任意 SQL/Python/Spark、循环、事件、业务回滚」列为 `OUT_OF_SCOPE`；
- 「多任务依赖：分支、汇聚、跨作业前后置」列为 `CONDITIONAL`，只盘点不建设。

这个裁决在工程上是对的（避免建第二套工作流引擎），但它不是"暂缓"，而是**把协议里的一个 P1 条款从路线图上取消了**。这不能由研发单方面决定，需要和客户明确二选一：

1. 协议条款裁剪 —— 书面确认「数据集成流程」形态即为交付形态；或
2. 后续补一个**受控算子层** —— 算子编译到既有 dbt/SQL 执行链，不恢复自由画布（对应登记册 F11 的原意）。

**在客户书面确认前，验收时这仍是一个可被指出的未满足条款。**

### N2 — CDC「已启用」是假象

`ConnectorCapabilityService.java` 三个连接器块中，唯一 `cdc.enabled = true` 的是 **`airbyte`**，而它的版本字段是字面量 `"future"`，且 `docker-compose-app.yml` 的 22 个服务里**没有 airbyte**。真正在跑的 `addax` 连接器 `cdc.enabled = false`。

所以「实时流采集」在能力声明上看似部分开放，实际不可用。**若前端按 capability 渲染，这里存在向用户暴露不可用选项的风险**，建议在补齐前把 airbyte 块的 `cdc` 显式关闭，或从 `ensureDefaults()` 中摘除。

### N3 — 通知能力是空壳，不是"部分实现"

全仓唯一的通知类 `DtsCommonNotifyClient.java` 是一个**出站 HTTP 客户端**：
- `enabled` 默认 `false`，`application.yml:445-448` 未配置 `base-url`；
- 它 POST 到 `{base-url}/api/notify`，而**该端点在本仓库没有任何服务端实现**；
- 方法名 `trySend` —— 失败静默。

M06 的「主动预警推送」「问题工单主动通知」应按**完全缺失**计，不应因为存在这个类而记为部分完成。

### N4 — Sprint-99 建册但未启动

`README.md:4` 状态 `DRAFT`（G0 交付基线未过），`it/evidence/` 空目录，F1～F4 无对应代码。v4 标注为「成本 1 人日」的 P0-1/P0-2 至今未执行。

---

## 4. 结论与建议

1. **本周期协议缺口净进展为零。** 这是连续第二个复核周期出现"产能全部投入 M08/M04 增强、P0 零动作"的模式，与 v4 §3.1 的判断一致。
2. **N1 需要尽快向客户提出**，它是本次 review 唯一的**新增验收风险**，且拖得越久改动成本越高（sprint-103 已按新形态部署）。
3. **N2 建议本周内改掉**（一处 `Map.of` 的布尔值），成本以分钟计，避免现场演示时点开不可用功能。
4. P0-1/P0-2 的建议维持不变：改 realm JSON + 补启动期校验，1 人日。它们是机密级测评的硬门槛，不做则其余功能无法验收。
5. 其余排期建议沿用 `sprint-99-.../assets/protocol-gap-register.md` 的四波次划分，无需重排。

> 全部结论为工作树实测（文件路径 + 行号），可逐条回验。
