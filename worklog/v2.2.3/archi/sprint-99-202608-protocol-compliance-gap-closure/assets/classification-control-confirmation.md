# 密级控制现状确认（2026-08-21 实测）

> 缘起：Sprint-99 立项时需确认「密级控制是否已具备、能否作为本轮依赖」。结论：**具备，且成体系，可直接依赖。**
> 复核方式：`v2.2.3` 工作树 @ `ec8da3022` 逐类核验，非文档声明。

## 1. 已具备的密级控制面

| 能力 | 落点 | 关键实现 |
|------|------|----------|
| 统一密级目录 | `dts-common/.../security/SecurityLevelCatalog.java` | 人员 `GENERAL(0)/IMPORTANT(1)/CORE(2)`；数据 `PUBLIC(0)/INTERNAL(1)/SECRET(2)/CONFIDENTIAL(3)`；默认数据密级 `INTERNAL`；含数字、中文、legacy（NON_SECRET/TOP_SECRET）别名归一 |
| 入湖封存 | `dts-ingestion/.../IngestionClassificationSealGuard.java:19` | `requireProductionSeal` 强制校验 `sealId / subjectType / subjectKey / effectiveLevel / snapshotVersion / checksum`，缺一即拒 |
| 准入校验 | `CatalogClassificationAdmissionService.java:16` | `requireValid(SealReference)` |
| 沿血缘传播 | `CatalogClassificationPropagationService.java:111` | `SecurityLevelCatalog.maxDataCode(levels)` —— **取最高密级，即只升不降** |
| 环路保护 | 同上 `:65` | 血缘成环时保留最后已知最高密级，并阻止发布 |
| 持久化四件套 | `domain/catalog/` | `CatalogClassificationSnapshot / Event / Mapping / PropagationJob` |
| 影响解释 | 同服务 `:209` | `explainImpact(datasetId)` 可解释某资产密级的来源 |
| 消费侧密级 | `CatalogConsumerClassificationService`、`AnalyticsConsumerClassificationService` | 大屏侧有降密拦截测试 `ScreenResourceClassificationDowngradeTest` |
| 人员密级判定 | `security/policy/PersonnelLevel.java`、`FileClassificationGuard.java` | 人员密级与数据密级的比对闸门 |
| 操作权限矩阵 | `security/policy/AssetAction.java` + `/api/iam/action-policies/*` | 八动作：新增/删除/修改/复制/导入/导出/归档/销毁 |
| 前端 | `pages/catalog/Sprint72ClassificationLifecycle.source-contract.test.ts`、`pages/security/F5DataSecurityLinkage.source-contract.test.ts` | 密级生命周期与数据安全联动有契约测试 |

## 2. Sprint-72 的两半：一半落地，一半没有

Sprint-72 标题是「数据密级全生命周期与不可降级传播闭环」，实测结论：

- ✅ **密级传播那一半落地了** —— 封存、准入、只升不降、快照/事件/传播作业、消费侧拦截，全部在 `v2.2.3` 工作树里跑得通。
- ❌ **生命周期那一半没有** —— `CatalogLifecycleRequestService.java:409` 仍只允许 `ARCHIVE / DISPOSE / EXTEND` 三种类型；`:336` 的 `DISPOSE` 映射为 `AssetAction.DELETE` 的软禁用，**没有临时销毁/永久销毁双态、没有回收站还原、没有创建/存储/使用/共享四阶段的统一入口、没有在用/共享/归档/销毁的数据量统计**。

这与「审批模型未定所以生命周期停在半路」的判断一致：生命周期的每个阶段迁移都需要"谁批准"，模型未定则契约无法钉死。

## 3. 对 Sprint-99 的影响

| 判断 | 依据 |
|------|------|
| **密级可以作为依赖直接用** | 第 1 节全部能力实测存在 |
| **F3 敏感识别不得自己判密级** | 密级判定权归 `SecurityLevelCatalog` + 传播服务；识别引擎只产出建议（ADR-99-03） |
| **F3 的降级建议必然被拒** | `maxDataCode` 语义决定建议密级低于当前密级时不可能生效，前端应直接禁用而非提交后报错 |
| **生命周期缺口不在本 Sprint** | 其非审批部分排 Sprint-100，审批部分 `BLOCKED-审批模型未定` |

## 4. 仍存在的密级相关缺口（登记，不在本 Sprint）

| 缺口 | 归属 |
|------|------|
| 生命周期 6 阶段统一闭环 + 在用/共享/归档/销毁数据量统计 | Sprint-100（P0-7 非审批部分） |
| 数据销毁临时/永久双态 + 一键还原沙箱 | Sprint-100（P0-8） |
| 生命周期各阶段的审批入口 | `BLOCKED-审批模型未定` |
| 密钥分级与轮换、国密 SM2/SM3/SM4 与 GM TLS | 见 `protocol-gap-register.md` F16（现仅 PKI 验签用到 SM2） |
