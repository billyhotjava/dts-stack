# WarehousePlan 领域与 API 契约

## 1. Canonical 聚合

```text
WarehousePlan
  id, tenantId, code, name, objective, scope
  ownerId, ownerDepartmentId
  onboardingMode
  lifecycleStatus
  optimisticVersion
  legacySource, legacyRef
  domainBindings[]
  processBindings[]
  sourceBindings[]
  sourceBusinessMappings[]
  metricRequirements[]
  planningPolicy
  modelRefs[]
  stageEvidence[]
```

禁止把完整连接配置、标准正文、资产正文、指标公式或 dbt 文件保存在计划表中。

## 2. 状态机

```text
DRAFT
  -> BASELINE_READY
  -> DESIGNING
  -> VALIDATING
  -> READY_TO_PUBLISH
  -> PUBLISHED
  -> ARCHIVED

任意未发布状态 -> ARCHIVED
PUBLISHED 修改 -> 新版本/变更评审，不原地回到 DRAFT
```

状态只能由命令服务根据领域事实转换。`stageProjection` 是查询模型，不是第二套状态机。

## 3. 主要请求 DTO

### 3.1 CreateWarehousePlanRequest

```json
{
  "code": "warehouse_finance_2026",
  "name": "财务分析数仓规划",
  "objective": "统一预算、合同与支付分析口径",
  "scope": "财务主题域一期",
  "ownerId": "user-id",
  "ownerDepartmentId": "dept-id",
  "onboardingMode": "BUSINESS_FIRST"
}
```

### 3.2 SourceBindingInput

```json
{
  "sourceType": "CATALOG_TABLE",
  "sourceId": "asset-id",
  "sourceVersion": "schema-version",
  "confirmationStatus": "CONFIRMED",
  "exclusionReason": null
}
```

`sourceType` 最低覆盖 `CONNECTION_TABLE`、`CATALOG_TABLE`、`EXCEL_FILE`、`DBT_NODE`。新增类型不得改变基线门禁语义。

### 3.3 SourceBusinessMappingInput

```json
{
  "sourceBindingId": "binding-id",
  "domainId": "domain-id",
  "processId": "process-id",
  "mappingStatus": "CONFIRMED",
  "notes": "该工作表记录付款申请状态"
}
```

### 3.4 PlanningPolicyInput

```json
{
  "layerPolicyCode": "CLASSIC_ODS_DWD_DWS_ADS",
  "namingPolicyRef": "policy-id",
  "historyPolicy": "PRESERVE_BUSINESS_HISTORY",
  "defaultTimeZone": "Asia/Shanghai"
}
```

## 4. API 清单

| Method | Path | 用途 | 主要门禁 |
|---|---|---|---|
| GET | `/api/modeling/warehouse-plans` | 计划列表 | 租户、权限、筛选 |
| POST | `/api/modeling/warehouse-plans` | 创建统一计划 | code 唯一、负责人有效 |
| GET | `/api/modeling/warehouse-plans/{id}` | 计划详情 | 租户、可见性 |
| PATCH | `/api/modeling/warehouse-plans/{id}` | 更新基本信息 | 乐观锁、生命周期 |
| POST | `/api/modeling/warehouse-plans/{id}/archive` | 归档 | 引用提示、审计 |
| GET | `/api/modeling/warehouse-plans/{id}/baseline` | 查询统一基线 | 聚合业务与来源 |
| PUT | `.../baseline/business-scope` | 保存域、过程、需求 | 候选与确认分离 |
| PUT | `.../baseline/sources` | 保存来源盘点 | 引用有效性 |
| PUT | `.../baseline/source-mappings` | 保存来源业务映射 | 引用归属一致 |
| PUT | `.../baseline/policy` | 保存规划策略 | 策略代码有效 |
| POST | `.../baseline/confirm` | 确认基线 | 五项统一门禁 |
| GET | `.../models` | 查询计划模型 | 复用 ModelSpec |
| GET | `.../stage-projection` | 黄金主线投影 | 不把 UNKNOWN 当完成 |
| GET | `.../evidence` | 查看原始证据摘要 | 权限和脱敏 |
| GET | `.../deliverables` | 发布成果引用 | 资产/指标/服务聚合 |
| POST | `.../versions` | 创建计划版本 | 结构差异、审计 |
| POST | `.../reviews` | 评审/审批 | 状态和权限 |

## 5. StageProjection

```json
{
  "planId": "plan-id",
  "computedAt": "2026-07-18T10:00:00Z",
  "overallStatus": "IN_PROGRESS",
  "currentStage": "WAREHOUSE_PLANNING",
  "primaryBlocker": {
    "code": "SOURCE_BUSINESS_MAPPING_INCOMPLETE",
    "message": "还有 2 个来源未确认业务范围",
    "actionLabel": "继续确认来源映射",
    "actionPath": "/modeling/plans/plan-id/baseline?tab=sources"
  },
  "stages": [
    {
      "code": "DATA_CONNECTION",
      "status": "COMPLETE",
      "evidenceCount": 2,
      "freshness": "CURRENT"
    }
  ]
}
```

阶段状态枚举：`NOT_STARTED`、`IN_PROGRESS`、`BLOCKED`、`COMPLETE`、`UNKNOWN`。证据新鲜度：`CURRENT`、`STALE`、`UNAVAILABLE`。

## 6. 兼容契约

- 现有 `/api/modeling/vnext/plans` 在过渡期作为 adapter，响应中增加 canonical `planId`；
- 新写 API 只写 canonical 聚合；旧写入要么代理到新命令，要么返回带迁移说明的冲突；
- 现有 ModelSpec API 不更换 URL，通过 `planId` 保持兼容；
- 旧计划查询必须标明 `migrationStatus`，不能静默拼接两套不同版本；
- 旧 API 删除前必须先有调用审计和两个版本周期零活跃消费者。

## 7. 错误和并发

所有错误返回稳定 `code`、客户可读 `message`、可选 `fieldErrors`、`correlationId` 和修复链接。计划和基线写入必须携带对应资源的 `version` 或 `If-Match`；冲突时不自动覆盖。

### 7.1 锁粒度（2026-07-18 评审增补）

业务确认（F3-T02）与资产盘点（F3-T03）是设计上鼓励并行的路径，因此锁粒度与 API 编辑单元严格一致，不允许整计划一把锁：

| 锁资源 | API 编辑单元 | version/ETag 所属内容 |
|---|---|---|
| `plan-head` | `PATCH /api/modeling/warehouse-plans/{id}` | name、objective、owner、lifecycle |
| `business-scope` | `PUT .../baseline/business-scope` | domainBindings、processBindings、metricRequirements |
| `sources` | `PUT .../baseline/sources` | sourceBindings |
| `source-mappings` | `PUT .../baseline/source-mappings` | sourceBusinessMappings |
| `policy` | `PUT .../baseline/policy` | planningPolicy |

- 每个编辑单元维护独立 version/ETag；不同编辑单元的并发提交互不冲突；
- 同一编辑单元版本冲突返回 409、当前 ETag 和差异摘要，不自动合并；
- baseline confirm、发布、归档等状态机转换命令校验 `plan-head` 聚合版本，并在事务内复核各子资源当前版本；
- API、Liquibase、领域对象和 IT-12 必须使用同一组锁资源名称，不得另造 `/source-bindings` 等第二套路径。

### 7.2 tenantId 语义（2026-07-18 评审增补）

当前交付形态为私有化单租户部署：`tenantId` 由服务端按部署配置注入（默认 `default`），不接受 UI/请求体传入，也禁止任何代码以 tenantId 分支业务流程。数据库唯一键、仓储查询、关联校验和审计仍必须包含 tenantId 作用域；这既满足当前单租户交付，也保留 IT-11 的隔离回归能力。
