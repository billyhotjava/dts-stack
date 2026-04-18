# Sprint-2: 主数据与填报体系建设

**时间**: 2026-05
**状态**: READY
**目标**: 建立「审批引擎 + 主数据 + 填报」三位一体的业务基础设施，取代当前"Excel 直灌 ODS"的脆弱链路，为所有业务系统（platform / 财务 / PLM / ERP）提供主数据对接与数据质量保障。

## 背景

当前 S10 平台存在以下根本性缺陷：

1. **缺乏主数据管理**: 业务主数据（project / dept / subsystem / supplier / pbs）在 9 张 ODS 表里以 `varchar` 重复出现，没有权威源，无法跨系统复用，无法做主数据治理；人员信息在各系统分散查询，缺统一对外入口
2. **Excel 直灌 ODS 质量失控**: 字段漂移、中英文别名、枚举硬编码、主键靠 MD5 拼串 → DWD 主键抖动、指标失真
3. **缺乏填报规范化通道**: 低质量数据只能靠 Excel 反复返工，没有"带下拉/校验的结构化填报"路径
4. **业务审批能力空白**: admin 只做三员账号审批，不做业务审批；未来资产访问审批、主数据变更审批、填报数据入库审批都缺基础设施
5. **密级属性跨系统不一致**: 数据密级 4 级（PUBLIC/INTERNAL/SECRET/CONFIDENTIAL）与人员密级 3 级（GENERAL/IMPORTANT/CORE）在前端 / admin / platform 有多份硬编码；dts-common 里已有 `SecurityLevelCatalog` 但未在全链路使用

**架构决策**（2026-04-18 确认）：

- **dts-approval** 抽取为独立服务，承担所有业务审批（不做账号审批——那仍在 admin）；本 Sprint 先做 **lite 版**（契约+Stub），full 版延后
- **dts-mdm** 独立服务，与 admin/platform 平级，作为所有业务主数据的权威源，对外提供 REST API 供其他系统对接；**person 作为统一查询代理入口**（内部走 admin/Keycloak，不维护数据本身）
- **dts-intake** 独立的轻量填报服务，作为 Excel 的结构化替代路径；下拉引用 dts-mdm，提交入库走 dts-approval
- **dbt 层范式化**：ODS 重构时引用 dts-mdm 维表，清掉之前 12 项 dbt 遗留债务
- **密级全链路统一**：数据密级 4 级、人员密级 3 级，规则法定常量写死在 `dts-common.SecurityLevelCatalog`；所有服务（含前端）都通过 shared lib 或 MDM API 获取，不再硬编码

**约束**:
- Sprint-1（IAM 重构）完成后才开始本 Sprint，因为 F1 审批引擎需要 Keycloak 组织树作为真源
- admin 保持职责边界：**只管人，不管业务**（参考 `feedback_mdm_is_independent_service.md`）
- Excel 路径不强制下线，与填报并行；按数据质量灰度迁移
- 每个 Feature 对应一份独立设计 spec，细节另行 brainstorm

## Feature 列表

| ID | Feature | Task 数 | 状态 | 设计阶段 | 优先级 | 依赖 |
|----|---------|---------|------|---------|--------|------|
| F0 | [消息事件基础设施](features/F0-消息事件基础设施/README.md) | 5 | READY | spec 就绪 | P0 | Sprint-1 |
| F1 | [审批契约与 Stub（lite）](features/F1-审批契约与Stub/README.md) | 4 | READY | 骨架 | P0 | F0 |
| F2 | [主数据管理 MDM](features/F2-主数据管理MDM/README.md) | 7 | READY | 骨架 | P0 | F1-lite |
| F3 | [轻量填报](features/F3-轻量填报/README.md) | 6 | READY | 骨架 | P1 | F2 |
| F4 | [ODS 范式化](features/F4-ODS范式化/README.md) | 7 | READY | 骨架 | P1 | F2 |

**依赖链**: `Sprint-1 → F0 → F1-lite → {F2, F3, F4 并行}`

**F1 拆分说明**：F1 在本 Sprint 只做 **lite 版**（契约+Stub+SDK），让 F2/F3 日一可集成；**F1-full 完整审批引擎**（审批链、规则、前端、组织树、通知）延到后续 Sprint，待客户内部审批制度讨论输入。F1-lite 契约稳定后，F1-full 上线**业务方零改动**。

## 设计阶段定义（增量式 brainstorm 流程）

为控制单次会话的上下文长度，Feature 按依赖顺序**逐个**进入详细设计，状态含义：

| 设计阶段 | 含义 |
|---------|------|
| `骨架` | Feature README + Task 占位已就绪；详细设计未开始 |
| `设计中` | 正在 brainstorming（澄清 / 方案 / 设计） |
| `spec 就绪` | `F{n}-design.md` 已写入 Feature 目录并通过 review |
| `plan 就绪` | 实施计划已通过 `writing-plans` 产出；可开工 |
| `编码中` | 开发中（Feature 状态同步改为 IN_PROGRESS） |
| `完成` | 验收通过（Feature 状态 DONE） |

**当前下一步**：F0 进入"设计中"。F1/F2/F3/F4 保持"骨架"，按依赖链逐个推进。

## 模块边界

```
┌───────────────────────────────────────────────────────────────┐
│ Keycloak (身份+组织真源，Sprint-1 成果)                          │
│  user attributes: person_security_level (GENERAL/IMPORTANT/CORE) │
└───────────────────────────────────────────────────────────────┘
           ▲                                    ▲
           │ 认证/组织/密级查询                 │
           │                                    │
┌──────────┴──────────┐     ┌──────────────────┴─────┐
│ dts-admin            │     │ Kafka (F0 业务事件总线)   │
│ (仅账号/三员/部门)    │     │ +Outbox 保强一致          │
│                      │     │ 信封带 dataClassification │
└──────────────────────┘     └────────────────────────┘
                                 ▲     │
                          发事件 │     │ 订阅事件
                                 │     ▼
┌────────────────────────┐   ┌────┴──────────────┐
│ dts-approval (F1-lite) │◀─▶│ dts-mdm (F2)       │──┐
│ 契约+Stub+SDK           │   │ 主数据权威源 (5类)  │  │ 对外提供主数据
│ (full 版后续 Sprint)    │   │ + person 查询代理   │  │ + 密级字典只读
└────────────────────────┘   └────────────────────┘  │
       ▲                         ▲   ▲                │
  提单 │                    查询 │   │ 变更事件       │
       │                         │   │                ▼
┌──────┴──────────┐   ┌──────────┴───┴──┐   ┌────────────────┐
│ dts-intake (F3) │   │ dts-platform     │   │ 财务/PLM/ERP   │
│ 轻量填报         │   │ (资产/大屏/ABAC) │   │ (将来对接)      │
│ form_classif.   │   └──────────────────┘   └────────────────┘
└─────────────────┘
       │
       │ 填报数据结构化入 ODS (带 classification)
       ▼
┌──────────────────────────────────┐       ┌──────────────────────────┐
│ dts-dbt (F4)                     │ uses  │ dts-common (shared)      │
│ ODS/STG/DWD 均带 classification   │──────▶│ SecurityLevelCatalog      │
│ yaml meta.classification         │       │ (法定常量+规则函数)       │
└──────────────────────────────────┘       └──────────────────────────┘
```

**密级一致性**：`dts-common.SecurityLevelCatalog` 是唯一代码真源；MDM 把密级以"只读字典"对外暴露；前端原有硬编码映射改调 MDM API。

## 完成标准

- [ ] Kafka（单节点 KRaft）接入 docker-compose 并作为 F1/F2/F3/F4 的统一事件总线；outbox 样例可复用；信封含 `dataClassification`
- [ ] dts-approval **lite 版**上线：REST + Kafka 契约稳定、Stub 自动 approve、`ApprovalPort` SDK 发布；至少被 MDM / intake 使用
- [ ] dts-mdm 独立服务上线：5 类实存主数据 + person 代理查询 + 业务字典 + 密级只读字典；变更走 F1-lite 审批；对外 REST API 稳定
- [ ] dts-intake 独立服务上线，至少替代 1 张低质量 Excel 表；表单带 `form_classification`，提交继承
- [ ] dbt ODS 层范式化完成：9 张 v2 表完成主从拆分、引用 MDM 维表、稳定代理键、每表带 classification；12 项 dbt 债务清零
- [ ] 前端 `value-localization.ts` 硬编码改为调 MDM 密级字典 API；admin/platform/其他前端消除密级本地映射
- [ ] 所有模块的 IT（集成测试）在 `it/` 下有可重现的验证脚本与证据
- [ ] admin 的三员审批代码**不被 dts-approval 吸收**，保持原地（明确职责边界）
- [ ] **Sprint-1 F3 追加要求**：Keycloak `person_security_level` attribute 取值统一为 `GENERAL/IMPORTANT/CORE`（或至少存量数字码可被 `SecurityLevelCatalog.parse` 正确归一化）

## 后续动作

- 本 README + 各 Feature README 是 Sprint-2 的**骨架 spec**
- 每个 Feature 开工前，单独走一轮 brainstorming → 产出 `F{n}-design.md`（保存在对应 Feature 目录下）→ 然后 writing-plans
- 本 Sprint 所有 Task 的细节（数据模型、API、UI、验证）在各 Feature 的 brainstorming 阶段补齐，当前 Task 文件仅占位
