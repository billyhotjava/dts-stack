# F1/T01 统一语言、Canonical Owner 与写权限决策包

**评审状态**：ACCEPTED（IT-01、IT-02；IT-07 权限子评审）

**形成日期**：2026-08-09

**适用评审**：IT-01、IT-02、IT-07（权限前置部分）

**评审人**：xiezm（兼任产品决策、数据架构、安全/权限及相关 canonical owner）

本文件把 F1/T01 所需事实、候选选择、反例和验收方式收敛为一次决策输入，并在 §5 保留正式评审结论。真实参与者、异议和批准结果写入对应 IT 记录；ADR 的权威状态只在 [`decision-register.md`](decision-register.md) 修改。

## 1. 已证实的当前实现

| 能力 | 当前写入口/写 owner | 现有预防控制 | 审计/已知缺口 |
|---|---|---|---|
| 业务分类/数据域 | `CatalogDomainResource` 直接写 `CatalogDomainRepository` | `CATALOG_MAINTAINERS`；受限域再校验 `EDIT/MANAGE` | 写动作有审计；Controller 直写 Repository，尚无统一 application command boundary |
| 业务过程 | `ModelingBusinessProcessResource` 委托 `Sprint64GovernanceService` | `DATA_MAINTAINER_ROLES` | 新入口复用既有 ledger；遗留 sprint 编号入口仍需作为兼容面盘点 |
| 数仓分层 | `WarehouseLayerResource` 委托 `WarehouseLayerApplicationService` | `CATALOG_MAINTAINERS` | Service 内有严格审计、引用保护和软删除；是可复用的 canonical service 模式 |
| 数据集市 | `DataMartResource` 与计划基线入口共同委托 `DataMartApplicationService` | `CATALOG_MAINTAINERS`、actor、幂等键/ETag | Service 统一写与审计；计划基线入口仍属于“建设范围”，不是第二个字典 owner |
| 主题域 | `SubjectDomainResource` 委托 `SubjectDomainApplicationService` | `CATALOG_MAINTAINERS`、actor、ETag | Service 统一写与审计 |
| 数仓计划 | `WarehousePlanResource` 委托 `WarehousePlanApplicationService` | `CATALOG_MAINTAINERS` + `WarehousePlanAuthorizationGuard` 的机构/同部门边界 | 属于建模建设范围，不上收为架构字典 |

源码证据：

- `AuthoritiesConstants.java:8-67`：现有 JWT authority 与 `CATALOG_MAINTAINERS/DATA_MAINTAINER_ROLES` 聚合角色；
- `CatalogDomainResource.java:87-143,229-264`：域写入口、角色校验、对象级维护校验和审计；
- `WarehousePlanResource.java:68-75,107-149,235-415` 与 `WarehousePlanAuthorizationGuard.java:16-91`：计划写权限和部门边界；
- `WarehouseLayerApplicationService.java:62-67,144-195`：application service、严格审计、引用保护；
- `DataMartApplicationService.java:35-46,54-95,403-408`、`SubjectDomainApplicationService.java:30-32,295`：字典写与审计归入应用服务。

### 权限事实的两层口径

`read/write/export` 是当前产品/前端授权能力的粗粒度口径；后端同时已有组织角色集合、域访问策略和计划部门 guard。两者并不矛盾，但后者仍未形成“数据架构管理员”这一实体类型专属授权。因此：

1. 不能再写成“运行时完全不可强制”；现有角色与对象 guard 已能拒绝部分写入。
2. 也不能宣称已有数据架构职责分离；`CATALOG_MAINTAINERS` 横跨多种维护职责，且域 Controller 仍直接写 Repository。
3. “单一写 owner”首先是代码与数据所有权约束，应由唯一 application command boundary 预防；“哪些人可写”是 actor 授权约束，两者必须分别验收。
4. 审计只能证明和追查已发生的写入，不替代上述两类预防控制。

## 2. 已冻结的统一语言与边界

| 术语 | 唯一定义 | Canonical owner | 明确排除 |
|---|---|---|---|
| 业务分类 | 业务管理视角的一级归类；映射 `catalog_domain` 根节点 | 平台数据架构 | 指标类型、密级、自由标签 |
| 数据域 | 业务分类下的单父级子域；映射 `catalog_domain` 子节点 | 平台数据架构 | 数仓分层、来源系统、主题域 |
| 业务过程 | 数据域内可稳定引用的业务事件/活动定义 | 平台数据架构 | ModelSpec 自由文本活动描述 |
| 数仓分层 | ODS/STG/DWD/DWS/ADS 等技术加工层级，与业务树正交 | 平台数据架构 | SOURCE_SYSTEM、DIMENSION 模型类型 |
| 数据集市 | 面向一类业务消费场景的数据集合 | 平台数据架构 | 数仓计划、任意项目工作区 |
| 主题域 | 数据集市下的主题组织单元 | 平台数据架构 | 业务分类/数据域的别名 |
| 数仓计划 | 在既定架构字典上选择建设范围、策略与 baseline | 数据建模规划 | 平台级架构字典本体 |
| 业务主数据 | 人员、组织、项目、物料等业务实体的金记录、来源映射与版本 | 独立 MDM | 架构字典、物理资产、ModelSpec |

MDM 的端到端边界固定为：`MDM 金记录/稳定引用 → 维度定义/模型 revision → 物化观察 → 物理资产投影`。MDM 不直接写架构字典，也不因被维度模型引用就变成数仓分层或数据域。

## 3. ADR-86-01/02/03/08/11/12/17 冻结选择

| ADR | 冻结选择 | 理由与影响 | 兼容路径 | 反例/拒绝条件 | 验收方式 |
|---|---|---|---|---|---|
| 01 | 保持 `ACCEPTED`：平台全局 | 用户已明确当前无完整租户能力 | 遗留 `tenant_id` 保留服务端默认 scope，不展示租户选择器 | 任一读取因客户端伪造 tenant 产生第二份字典 | `ArchitectureScopeContractIT` 验证跨现有角色读取同一投影、非法 scope 不分叉数据 |
| 02 | 保持 `ACCEPTED`：业务分类 1:n 数据域、域单父级 | 复用 `catalog_domain.parent_id`，避免第二棵树 | 旧名称仅作展示兼容，稳定 ID 不变 | 同一数据域拥有多个父分类 | 树契约测试 + 数据唯一性/循环校验 |
| 03 | 保持 `ACCEPTED`：数仓分层与业务树正交 | 技术加工阶段不能替代业务归属 | 现有字符串 layer 双读并映射 canonical code | 用 DWD/ADS 作为数据域，或用业务域决定技术层 | 模型/资产同时保存业务归属与分层的契约测试 |
| 08 | `ACCEPTED`：建立逻辑上的平台数据架构控制面；是否一级菜单留给 ADR-86-09 | 收敛 owner，不等于新建系统或先改导航 | 复用现表、现 API 和 application service；旧入口只作为兼容 adapter，逐步只读/跳转 | 新建第二套 domain/layer/mart/subject 表或 CRUD；把计划 baseline 当字典本体 | 静态依赖规则证明消费者无 Repository 写入；兼容路由写入落到同一 command service |
| 11 | 保持 `ACCEPTED`：密级/权限与业务语义分离 | 分类是安全控制，不是业务归属 | 沿用现有 classification 控制面 | 用“研发域”替代密级，或按业务分类自动授权 | 数据契约中字段分轴；授权测试不读取业务标签作安全判定 |
| 12 | `ACCEPTED`：只冻结 MDM 边界，通用 MDM 实现转后续独立 Sprint | 简单 MDM 不与 Sprint-86 冲突，但不能把架构字典改名成主数据 | 复用人员/组织网关；后续 Sprint 扩展金记录台账 | MDM 直接维护域/分层，或资产表冒充金记录 | IT-03 走查 E2E-D 边界；后续 MDM Sprint 另有对象/生命周期 ADR |
| 17 | `ACCEPTED` 方案 A：唯一 command boundary + 现有 authority/对象 guard；`ROLE_ADMIN/ROLE_OP_ADMIN/ROLE_INST_DATA_OWNER` 可写全局字典，部门角色只读 | 可预防平行代码 owner，又不虚构不存在的细粒度动作 | 保留旧 URL；逐个改为委托 canonical service；沿用现有角色并收窄全局字典写 allowlist | 只隐藏按钮、只靠审计、多个 Controller 直写 Repository；部门角色写全局字典；新增未接 IAM 的假角色 | `ArchitectureOwnerAuthorizationIT` + ArchUnit/依赖规则 + 审计契约；非授权 actor 403，兼容入口与新入口写同一 ledger |

## 4. ADR-86-17 的控制分层与实施缺口

| 层次 | 候选控制 | 性质 | 当前差距 |
|---|---|---|---|
| 代码所有权 | 每类架构字典只有一个 application command service；REST/兼容入口只委托 | 预防性 | `CatalogDomainResource` 仍直接写 Repository；业务过程遗留入口待盘点 |
| Actor 授权 | 入口使用方案 A 的现有 authority allowlist，service guard 再校验对象/组织边界 | 预防性 | 方案已冻结但尚未统一落地；不得扩展部门角色写平台全局字典 |
| 数据一致性 | 稳定 ID、唯一性、乐观锁/幂等、引用保护 | 预防性 | 各字典成熟度不一致，域/业务过程需补齐统一版本契约 |
| 审计 | 每个写动作记录 actor、对象、动作、结果、correlationId | 侦测/追责 | 审计严格性不一致，失败写和兼容入口覆盖率待实施 Sprint 验证 |
| UI | 只有权威维护页显示写动作；消费者仅选择/查看 | 降低误用 | UI 不能作为安全边界；实现验收必须以后端拒绝为准 |

Actor 策略评审结果：

| 选择 | 平台全局架构字典写 allowlist | 取舍 | 建议 |
|---|---|---|---|
| A：最小既有角色集 | `ROLE_ADMIN`、`ROLE_OP_ADMIN`、`ROLE_INST_DATA_OWNER` | 不新增 IAM 角色；机构负责人和部门角色保留读/评审，不直接维护全局字典 | **ACCEPTED** |
| B：兼容机构宽角色 | A + `ROLE_INST_LEADER` | 更贴近现有 `INSTITUTE_PRIVILEGED_ROLES`，但审批人与维护人职责更难分离 | 仅在业务明确要求领导直接维护时选择 |
| C：新增专属角色 | 新 `ROLE_DATA_ARCHITECTURE_ADMIN` | 语义最清晰，但需要 dts-admin、Keycloak、权限配置、迁移和客户部署共同变更 | 转独立安全/IAM Sprint，不作为首版假角色 |

方案 A 已由 xiezm 以安全/权限负责人等全部评审角色批准。`ROLE_DEPT_DATA_OWNER/ROLE_DEPT_LEADER` 等部门角色不得写**平台全局架构字典**，但可继续按现有 guard 维护部门范围的计划/治理对象；IT-07 已记录该权限子评审结论。

## 5. 正式评审结论

1. ADR-86-12：通过；只冻结 MDM 边界，具体能力后续实现。
2. ADR-86-17：通过方案 A；代码 owner、actor guard 与侦测控制分层验收。
3. ADR-86-08：通过逻辑控制面归属；一级菜单与具体页面形态继续留给 ADR-86-09。

xiezm 于 2026-08-09 明确兼任全部评审角色并批准上述结论。F1/T01 可关闭，后续实施仍须由 F5/T01 形成具名 Task，不得把 ADR 通过冒充为代码已落地。

## 6. 实施 Sprint 的最小竖切片输入

本 Sprint 不编码。后续实施 Sprint 至少拆成以下契约测试先行的竖切片：

1. `catalog_domain` 写入收口：兼容 API 不变，新增/复用 canonical application service，禁止 Controller/消费者直写 Repository。
2. 业务过程遗留入口收口：所有写入口委托同一 ledger/service，保留兼容审计标识。
3. 架构字典授权：冻结 allowlist 与对象 guard，负向测试覆盖每个写入口。
4. 消费者只读 port：建模、资产、指标、质量只通过稳定 ID/只读 projection 获取字典。
5. 审计闭环：成功、拒绝、并发冲突和兼容入口均可按对象与 correlationId 追溯。
