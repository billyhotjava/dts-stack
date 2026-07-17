# 通用建模内核与行业模板边界设计

**日期**: 2026-07-17
**范围**: `dts-platform`、`dts-platform-webapp` 建模规划入口
**目标**: 保证 DTS 默认安装是行业中立产品，PJM 仅作为可选模板与测试夹具存在。

## 1. 能力声明

新建租户或空白主题域在未安装行业模板时，不自动拥有任何业务过程、一致性维度或客户业务字段。管理员可以显式安装一个版本化行业模板，模板内容复制为主题域自己的草稿事实，并保留来源；客户后续可以独立修改，不受模板静态常量控制。

## 2. 架构不变量

- 通用契约和校验器不得包含客户业务过程 ID、模型名或业务字段名。
- `src/main` 中允许存在行业模板描述文件，但只能通过通用模板目录加载，不能作为默认数据返回。
- 主题域业务过程和一致性维度必须持久化，不能由前端 seed 或后端静态列表伪装为租户数据。
- 模板安装必须显式、鉴权、可审计、幂等。
- 模板扩展规则不得按模型名在全局运行时自动生效；调用方必须显式指定模板，未来还需携带业务域上下文。
- 模板生成对象必须携带 `sourceType/sourceId/sourceVersion/confirmed`。
- 空白主题域返回空目录是正常状态，不得回退到 PJM。
- 已经存在的客户数据不删除；本次只停止继续注入默认示例。

## 3. 分层

```text
Generic Modeling Core
├─ warehouse layer and grain validation
├─ business process and conformed dimension persistence
└─ generic template loader and installer
        ↑ explicit install
Industry Template Catalog
├─ pjm-v1.json
└─ future manufacturing / energy / healthcare packs
        ↓ copies with provenance
Tenant / Domain-owned Modeling Facts
```

模板目录是内容包，不是新的事实源。安装完成后，业务过程和维度的权威数据仍在主题域持久化表中。
模板内的诊断建议同样只是可选内容；通用 dbt 诊断默认只依据 manifest、relation、上游行数和运行日志，不会因模型名碰巧相同而加载某个行业规则。

## 4. 数据模型

### 4.1 业务过程来源

在 `sprint64_business_process` 增加以下可向后兼容字段：

- `source_type`: `MANUAL | TEMPLATE | IMPORTED | SCANNED`，默认 `MANUAL`。
- `source_id`: 模板或导入任务标识，可空。
- `source_version`: 来源版本，可空。
- `confirmed`: 是否已被客户确认；手工创建默认 `true`，模板安装默认 `false`。

### 4.2 一致性维度

新增 `sprint64_conformed_dimension`：

- 主键：`domain_id + dimension_id`。
- 字段：`name/source_model/source_type/source_id/source_version/confirmed/created_date/last_modified_date`。
- 空白主题域没有默认行。

### 4.3 模板契约

```java
record IndustryModelingTemplate(
    String templateId,
    String industryCode,
    String name,
    String version,
    int compatibleContractVersion,
    List<ProcessTemplate> processes,
    List<DimensionTemplate> dimensions
) {}
```

首个 `pjm` 模板用于证明模板边界和回归能力，不会自动安装。

## 5. API

- `GET /api/governance/modeling-templates`: 查看可安装模板摘要。
- `GET /api/governance/modeling-templates/{templateId}`: 查看模板内容。
- `POST /api/governance/modeling-templates/{templateId}/install?domainId={uuid}`: 显式安装。
- 既有 `/api/governance/sprint64/domains/{domainId}/processes` 和 `conformed-dimensions` 返回持久化事实。

安装接口只新增缺失的过程和维度；重复调用返回 `ALREADY_INSTALLED` 或零新增，不覆盖客户修改。

## 6. UI 最小纠偏

- 删除 `BUSINESS_PROCESS_SEEDS` 和“从 PJM 示例开始”。
- 删除前端 `CONFORMED_DIMENSION_SEEDS`。
- 空态只保留“新增业务过程”；行业模板选择器属于下一阶段 UI 优化。
- 维度推荐从后端目录读取，目录为空时明确显示“尚未登记公共维度”。

## 7. 迁移与兼容

- 使用新的 forward-only Liquibase changeSet，不修改已经发布的 Sprint-64 migration。
- 新增列使用默认值，既有过程视为 `MANUAL + confirmed=true`。
- 不自动删除历史上已经写入数据库的 PJM 过程；无法可靠判断它们是客户数据还是旧示例。
- 新维度表不回填八个旧静态维度，避免再次把示例提升为全局事实。

## 8. 验收

- 通用契约测试证明 `ModelingVNextContract` 不再暴露 PJM fixture API。
- 空白主题域维度列表为空。
- PJM 模板仅能通过模板目录读取。
- 安装后过程、维度带模板来源，重复安装不覆盖已有记录。
- 前端生产源码不再包含 `BUSINESS_PROCESS_SEEDS`、`CONFORMED_DIMENSION_SEEDS` 或“从 PJM 示例开始”。
- 定向 Maven、Node source-contract、前端构建、Liquibase XML 解析和 GitNexus change detection 通过。

## 9. 非目标

- 本阶段不实现过程形态、事实模式、时间语义和存量资产扫描。
- 本阶段不新建模板管理页面。
- 本阶段不清理客户数据库中已经存在的业务过程。
- 本阶段不重构 `/api/semantic` 与 `/api/modeling/vnext` 的长期收敛关系。
