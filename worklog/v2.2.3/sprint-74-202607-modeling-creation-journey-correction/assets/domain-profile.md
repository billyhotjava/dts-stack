# 领域画像（Gate G0）

**勘察日期**：2026-07-26  
**数据来源**：当前 `v223-dts-pg-1 / dts_platform` 实例，只读查询  
**结论**：可据此设计创建与纠错主线；样本规模和类型覆盖不足，真实 E2E 数据基线仍有缺口

## 1. 统一语言

| 术语 | 定义 | 同义/禁用词 | 出处 |
|---|---|---|---|
| 业务目的 | 用户要描述的稳定分析对象、业务事件、聚合结果或消费输出 | 不用“先选 FACT 再说” | DataWorks/Kimball 参考 + 用户反馈 |
| 模型类型 | `DIMENSION / FACT / SUMMARY / APPLICATION` 的业务语义类别 | 不等于数仓层 | Sprint-67 canonical ModelSpec |
| 逻辑设计 | 不依赖具体引擎的业务粒度、字段、键、时间和维度关系 | 不包含物理名、装载、dbt | Sprint-67 四层设计 |
| 数据实现 | 把锁定的逻辑 revision 变成可构建目标的输入、映射、转换和物化配置 | 不等于逻辑模型，不等于物理资产 | `ModelImplementationRevision` |
| 高级 dbt | `DBT_MANAGED` 实现方式 | 不是“物理资产高级版” | Sprint-67/70 |
| 发布结果 | 已产生的真实 table/view、DDL、构建/测试/发布/登记/血缘证据 | 原 UI “物理资产”阶段 | 用户问题 + Sprint-69 |
| 物理资产 | 数据库或平台中真实存在、可定位和版本化的表、视图等对象 | 不包括 DRAFT ModelSpec 和 ephemeral STG | Sprint-67 |
| 必须项 | 当前要跨越的门禁 blocker | 不包括未来阶段要求和改进建议 | 本 Sprint ADR-74-08 |
| 可选项 | 不阻断当前门禁的增强信息 | 不得进入 blocker 计数 | 本 Sprint ADR-74-08 |

## 2. 业务不变量

| # | 不变量 | 强制级别 | 违反后果 | 出处 |
|---|---|---|---|---|
| D01 | 先选择业务过程/目的并声明粒度，再确定维度和事实 | 业务规则 | 模型类型与字段角色错误 | Kimball 四步法参考 |
| D02 | 模型类型和目标数仓层是两个概念；当前版本经典映射不因此改变 | 架构规则 | DIMENSION 被错误等同为物理层，或为纠正文案而扩大迁移范围 | Sprint-73 决策 + 本 Sprint ADR-74-06 |
| D03 | 逻辑模型不因没有来源或 dbt 实现而失效 | 业务规则 | 无法进行概念/逻辑评审 | DataWorks 建模与物化分离 |
| D04 | ModelSpec、ModelImplementation、发布控制面各只有一套 owner | 架构红线 | 产生平行台账和证据漂移 | domain-dts A4 |
| D05 | 历史 revision 不原地重写 | 数据规则 | checksum、审计和发布证据失真 | Sprint-67 |
| D06 | 密级传播不能降级，但可通过继承证据满足，不强迫逐字段重复填写 | 合规硬约束 | 泄露风险或无意义重复劳动 | Sprint-72 |
| D07 | dbt 是实现方式，物理资产是实现/发布结果 | 架构规则 | 页面边界和用户心智混乱 | Sprint-67 |

## 3. 真实数据画像

执行查询：

```sql
select model_type, layer, status, count(*)
from modeling_model_spec
where contract_version = 2
group by model_type, layer, status;

select count(*) as specs, count(i.id) as with_implementation
from modeling_model_spec s
left join modeling_model_implementation i
  on i.tenant_id = s.tenant_id and i.model_spec_id = s.id
where s.contract_version = 2;
```

| 指标 | 实测值 |
|---|---|
| v2 ModelSpec 总数 | 3 |
| 类型/层/状态 | DIMENSION/DWD/DRAFT=1；FACT/DWD/DRAFT=2 |
| 缺粒度 | 0 |
| 无字段 | 0 |
| FACT 缺有效时间语义 | 1/2 |
| DIMENSION 缺业务维度引用 | 1/1 |
| 已有 ModelImplementation | 0/3 |
| 已有 lifecycle event | 0/3 |
| 建设计划 | 2 |
| 计划分层策略 | 2/2 均为 `CLASSIC_ODS_DWD_DWS_ADS` |

“财务项目模型”实测：

```text
id=e0771977-7a06-4494-b0bf-19fe9efdd95c
plan=财务规划1
type=FACT
layer=DWD
status=DRAFT
revision=4
grain=财务领域中的项目
grain.keys=[field1]
time=EVENT_TIME fields=[TIME]
fields=[field1(KEY), 项目名称(ATTRIBUTE)]
sourceRefs=[]
dependsOn=[]
implementation=NONE
lifecycle=NONE
```

脏数据/不一致：

- `timeSemantics.fields=["TIME"]` 没有命中角色为 TIME 的字段；
- `项目名称` 被存入技术字段 `name`；
- “项目”更像稳定分析对象，但系统默认成 FACT，仍需用户确认业务含义；
- 当前没有实现或发布证据，满足“DRAFT 安全纠错”的候选条件。

**对设计的直接影响**：

- 真实数据量很小，不能据此制定大规模迁移性能结论；
- 至少准备 DIMENSION、FACT、SUMMARY、APPLICATION 各一条可验收 fixture；
- 中文技术字段编码必须兼容读取，不能直接增加强校验使旧草稿打不开；
- 纠错必须以新 revision 留痕，不需要修改物理资产，因为当前样本没有实现。

## 4. 外部边界

| 系统/模块 | 契约 | 可用性 | 失败降级 |
|---|---|---|---|
| 建设计划 | 既有 `layerPolicyCode=CLASSIC_ODS_DWD_DWS_ADS` | 当前可读写；本 Sprint 不扩展 | 策略不可用时禁止推断目标层 |
| 元数据/规划来源 | `WarehousePlanSourceBinding` | 当前样本未绑定 | 不影响 DRAFT/DESIGNED，只阻断实现 |
| dbt | `DBT_MANAGED ModelImplementation` | 容器存在，真实实现未验证 | 普通配置仍可用；不伪造 dbt 成功 |
| 发布工作台 | Sprint-69 ReleaseCandidate | 代码/数据链存在，未做本 Sprint 真实验收 | 发布结果保持空态 |
| 密级传播 | Sprint-72 publish gate | 已有契约，真实运行验收待 G0 | 失败时只阻断 RELEASE_READY |

## 5. 合规要求

| 条款 | 要求 | 是否硬门槛 |
|---|---|---|
| 密级传播 | 发布时有效密级取显式、继承和传播结果中的最高值 | 是，仅 RELEASE_READY |
| 审计 | 改型、策略变更、实现方式切换和发布动作必须留痕 | 是 |
| 权限 | 现阶段只接入既有 read/write/export 粒度 | 是；细粒度权限不在本 Sprint 伪造 |
| 历史留存 | revision、checksum、发布证据不可原地覆盖 | 是 |

## 6. 未决与基线缺口

- 自动化环境无法解析 `dts.local`，真实登录/API/Chrome95 需 F0/T01 修复；
- 当前运行镜像早于 Sprint-73 提交，数据库缺 20260727 前置迁移，需 F0/T01 先对齐部署基线；
- 现网只有 3 条 v2 草稿，缺 SUMMARY/APPLICATION 和已发布资产样本，需 F0/T02 建立隔离 fixture；
- Sprint-73 已提交 `implementationPolicy`；编码前必须更新 GitNexus、确认各环境 snapshot 现状并冻结兼容迁移边界；
- 当前工作树另有 Sprint-74 范围外的 Liquibase 用户修改，必须保持原样并从本 Sprint 提交中排除。
