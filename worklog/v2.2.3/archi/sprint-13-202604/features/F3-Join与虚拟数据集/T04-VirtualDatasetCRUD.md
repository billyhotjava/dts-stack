# T04: VirtualDataset CRUD + 持久化

**优先级**: P0
**状态**: READY
**依赖**: F1/T04

## 目标

实现虚拟数据集的持久化和 CRUD API，遵循 F1-T04 定义的 JSON schema。用户在画布上搭的"base + joins + derived_metrics + default_filters + canvas_layout"能保存、读取、共享、用在 Card 里。

## 技术设计

### 1. DDL（包含在 F2/T01 的 changelog 9001）

```sql
CREATE TABLE gov_virtual_dataset (
    id VARCHAR(32) PRIMARY KEY,
    spec_version VARCHAR(8) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    owner_user_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64),
    state VARCHAR(32) NOT NULL DEFAULT 'draft',
    security_level VARCHAR(32) NOT NULL,
    base_model VARCHAR(128) NOT NULL,
    definition JSONB NOT NULL,
    usage_stats JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    promoted_pr_url VARCHAR(500)
);
CREATE INDEX ix_vds_owner ON gov_virtual_dataset(owner_user_id);
CREATE INDEX ix_vds_workspace ON gov_virtual_dataset(workspace_id);
CREATE INDEX ix_vds_base ON gov_virtual_dataset(base_model);
```

### 2. 实体 & DTO

```java
@Entity
@Table(name = "gov_virtual_dataset")
public class GovVirtualDataset {
    @Id private String id;
    @Column(nullable = false) private String specVersion;
    @Column(nullable = false) private String name;
    private String description;
    @Column(nullable = false) private String ownerUserId;
    private String workspaceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VdsState state;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SecurityLevel securityLevel;
    @Column(nullable = false) private String baseModel;
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private VirtualDatasetDefinition definition;
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private VdsUsageStats usageStats;
    private Instant createdAt;
    private Instant updatedAt;
    private String promotedPrUrl;
}

public enum VdsState { DRAFT, SHARED, PROMOTED_PENDING, PROMOTED }
```

### 3. REST 端点

```
GET    /api/semantic/virtual-datasets              列表（支持 owner=me、workspace=、state=）
GET    /api/semantic/virtual-datasets/:id          详情
POST   /api/semantic/virtual-datasets              创建
PUT    /api/semantic/virtual-datasets/:id          更新（乐观锁）
DELETE /api/semantic/virtual-datasets/:id          删除（仅 draft 状态）
POST   /api/semantic/virtual-datasets/:id/share    draft → shared
POST   /api/semantic/virtual-datasets/:id/query    执行查询（在 VDS 上下文跑）
POST   /api/semantic/virtual-datasets/:id/promote  F6/T04 实现
```

### 4. 创建/更新校验（核心）

```java
public class VirtualDatasetValidator {
    void validate(VirtualDatasetDefinition def) {
        // 1. JSON schema 校验（F1-T04）
        // 2. base model 存在且 exposed
        // 3. 每个 join 是否在 JoinGraphRegistry 白名单
        // 4. 每个 derived_metric.expression 过 ExpressionParser（F4/T01）
        // 5. derived_metric 引用的指标存在
        // 6. security_level 自动计算 = max(所有参与 model.security_level)
        // 7. 用户自身密级必须 ≥ 计算出的 security_level
    }
}
```

**校验失败**：返回 400 + 字段级错误细节。

### 5. 状态机

```
draft ──→ shared ──→ promoted_pending ──→ promoted
  │          │
  └──────────┘ (可以 revert 回 draft)
  │
  └→ delete (仅 draft)
```

转换规则：
- 创建后默认 `draft`
- owner 可以 `share`：变 `shared`，同 workspace 可见
- 只有 state=shared 且使用度高（F6 usage_stats）才能 promote
- promoted 状态只读；后续 dbt model 上线后会自动绑定 dbt_unique_id 并建议下线 VDS

### 6. 乐观锁

所有更新必须带 `If-Unmodified-Since: <updatedAt>` 或 body 里 `expected_updated_at`，否则 412 Precondition Failed。

### 7. 基于 VDS 的查询

`POST /api/semantic/virtual-datasets/:id/query`：
- 读 VDS definition
- 把 VDS 的 joins + default_filters + derived_metrics 和请求的 measures/dimensions/filters 合并成一个大 QueryRequest
- 走 F3/T02 MultiModelCompiler（如需 fanout 走 T03）
- 返回结果

这让 Card 可以简写为"use VDS X + measures=[...] + dims=[...]"，不必每次重复 join 定义。

### 8. 列表性能

- 分页（page_size 默认 20，最大 100）
- `owner_user_id` + `workspace_id` 有 index
- `state` 过滤走列扫描（VDS 表预期不大）
- 返回 summary 视图（不返回 full definition JSON，减小响应）

### 9. 权限

- 列表：只返 owner=self 或 state=shared 且 workspace 可见
- 详情：owner 或 shared 可见
- 创建：任何登录用户
- 更新/删除：仅 owner（或 OP_ADMIN）
- 按 security_level 过滤：user level < vds level → 403

### 10. 审计

所有变更（创建、更新、状态转换、删除）写审计日志，走现有 `AuditLog` 服务。

## 影响范围

| 类型 | 文件 |
|---|---|
| 新建 | `domain/governance/GovVirtualDataset.java` |
| 新建 | `repository/governance/GovVirtualDatasetRepository.java` |
| 新建 | `service/semantic/vds/VirtualDatasetService.java` |
| 新建 | `service/semantic/vds/VirtualDatasetValidator.java` |
| 新建 | `web/rest/semantic/VirtualDatasetResource.java` |
| 新建 | DTOs + state machine 校验 |
| 新建 | liquibase 9001 追加 VDS 表 |
| 测试 | `VirtualDatasetServiceTest`、`VirtualDatasetResourceIT`、状态机 IT |

## 验证

- [ ] 创建 / 读取 / 更新 / 删除全流程走通
- [ ] JSON schema 校验失败时返回清晰字段级错误
- [ ] 乐观锁冲突返回 412
- [ ] 状态机非法转换返回 409
- [ ] 权限越界返回 403
- [ ] 基于 VDS 的查询结果与 inline joins 查询结果一致
- [ ] 审计日志完整

## 完成标准

- [ ] VDS CRUD 端到端通过
- [ ] 前端 F5 可以 save/load VDS
- [ ] 单元 + 集成测试覆盖率 ≥ 80%
- [ ] 文档 `assets/specs/04-virtual-dataset-and-arrow.md` 与实现一致
