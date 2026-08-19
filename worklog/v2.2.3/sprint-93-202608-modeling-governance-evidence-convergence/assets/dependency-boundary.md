# Sprint-93 依赖与范围边界

## 1. 编号说明

Sprint-91 曾在 `assets/sprint-92-back-conversion-handoff.md` 把 Sprint-92 预留给安全回切；2026-08-19 该范围已升级为来源无关的统一模型创作。本项仍使用 Sprint-93，并且只消费 Sprint-92 commit 产生的稳定 pins，不进入其草稿、投影和兼容迁移范围。

## 2. 既有 Owner 与本 Sprint 接缝

| 领域 | 唯一 Owner / 既有 seam | Sprint-93 允许动作 | 禁止动作 |
|---|---|---|---|
| 资产身份 | `CatalogAssetType` + `CatalogAssetKey` + `catalog_dataset` | 让所有生产观察进入同一登记边界 | 新建模型专属资产表、用表名作第二 ID |
| 五轴与消费资格 | `CatalogAssetRegistrationService` + semantic store | 接入生产 producer、对账存量、兼容扩展读 DTO | 再建一套状态枚举/统计表 |
| 模型台账/发布 | ModelSpec、implementation、candidate、physical observation、serving projection | 消费现有 outbox、回写同步结果、展示深链 | 新建发布状态机或模型台账 |
| 元数据来源 | Sprint-89 的 CatalogDataset/Table/Column 与 WarehousePlan source inventory | 读取稳定 locator、schema fingerprint、失效状态 | 重做采集、来源盘点或 drift UI |
| 血缘 | Sprint-90 的既有表、verification guard、查询/UI | 从物化/manifest 适配到既有写 seam | 新建血缘表、第二 graph 或第二 parser |
| 数据质量 | GovRule/Version/Binding/QualityRun | 建只读 Evidence Port，候选只存引用/checksum | 复制 run 结果、以模板数量或 dbt build 冒充治理质量 |
| 资产 UI | Sprint-88 概览、现有目录/详情/元数据/血缘/质量页面 | 增补状态、深链、统一用词与四态 | 新菜单、新工作台、复制详情页 |
| 权限 | 现有 `read/write/export`、全局写 guard、部门范围 guard | 补负向测试和审计 | 发明尚未确认的细粒度权限 |

## 3. 跨 Sprint 前置条件

| 前置 | 消费方 | 未完成时处理 |
|---|---|---|
| Sprint-89 F1/F2 稳定来源身份与 soft invalidation | F1、F4 | 对应 producer 保持 DRAFT，不复制修复 |
| Sprint-90 F1 字段血缘有效期/匹配修复 | F4 | 仅做表级观察，字段级验收 BLOCKED |
| Sprint-91 统一发布与物化主链 | F2、F3、F6 | 不绕过主链直接写 candidate 状态 |
| Sprint-92 统一 authoring commit | F2～F6 的模型结果消费 | 只消费 model/implementation/dependency pins；Sprint-92 未完成时沿 Sprint-91 既有 commit 输入验收，不复制创作实现 |

## 4. 文件冲突守卫

实施前为每个待修改符号执行 GitNexus impact。若 Sprint-89/90/91 正在修改同一文件，先确认其提交基线，再通过依赖接口适配；禁止并行改写相同页面或 owner。最终只允许一次主审，新增 security/database review 仅在主审发现独立实质风险时追加。
