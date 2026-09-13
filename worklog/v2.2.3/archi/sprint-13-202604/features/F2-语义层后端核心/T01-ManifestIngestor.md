# T01: ManifestIngestor + GovIndicatorDefinition 扩字段

**优先级**: P0
**状态**: READY
**依赖**: F1 全部

## 目标

实现 dbt manifest.json 的解析器，把 model / column / `meta.dts` 扩展映射到 `GovIndicatorDefinition` 等快照表，作为语义层的只读事实源。

## 技术设计

### 1. 触发方式

三种触发（按优先级）：
1. **手动 POST**：`POST /api/semantic/manifest/ingest`，body 为 manifest.json 文件（dev/test 用）
2. **对象存储轮询**：dbt CI 任务把 manifest.json 上传到指定 S3/MinIO key，服务端定时拉取并版本化
3. **Git webhook**（Phase 2）：dbt repo push 后触发——不在本 Task 范围

本 Task 只实现 1 和 2。

### 2. 入站流程

```
manifest.json (binary)
    ↓
DbtManifestClient.parse()              → 结构化 DbtManifest
    ↓
ManifestIngestor.validate()             → 校验 meta.dts 符合 F1-T01 spec
    ↓
ManifestIngestor.diff()                 → 与当前 GovIndicatorDefinition 对比
    ↓
MetricDefinitionMapper.toEntities()     → 新增/更新/标记废弃
    ↓
repo.saveAll() + audit log
    ↓
ResultCache.invalidateByModels(diff.changed)
```

### 3. `GovIndicatorDefinition` 扩字段

需要的新列（9002 liquibase changelog）：

```sql
ALTER TABLE gov_indicator_definition ADD COLUMN dbt_unique_id VARCHAR(200);
ALTER TABLE gov_indicator_definition ADD COLUMN dbt_manifest_version VARCHAR(32);
ALTER TABLE gov_indicator_definition ADD COLUMN source_model VARCHAR(128);
ALTER TABLE gov_indicator_definition ADD COLUMN source_column VARCHAR(128);
ALTER TABLE gov_indicator_definition ADD COLUMN resolved_sql TEXT;
ALTER TABLE gov_indicator_definition ADD COLUMN meta_dts_snapshot JSONB;
ALTER TABLE gov_indicator_definition ADD COLUMN sync_status VARCHAR(32);  -- active|orphaned
ALTER TABLE gov_indicator_definition ADD COLUMN last_synced_at TIMESTAMP;

CREATE INDEX ix_gid_dbt_unique_id ON gov_indicator_definition(dbt_unique_id);
CREATE INDEX ix_gid_source_model ON gov_indicator_definition(source_model);
```

### 4. 冲突与 orphan 处理

| 场景 | 处理 |
|---|---|
| 新 metric（manifest 有，表里没有） | INSERT，`sync_status = active` |
| 更新 metric（label/type/SQL 变化） | UPDATE，`meta_dts_snapshot` 更新 |
| 删除 metric（表里有，manifest 里没） | **不删**，改 `sync_status = orphaned`，UI 显示警告 |
| 工程师手动创建过的 metric | 保留，但 linter 警告"建议迁移到 dbt" |
| manifest 解析失败 | 事务回滚，ingest 任务失败记审计 |

### 5. JoinGraph 持久化

join 关系图不进 `GovIndicatorDefinition`，单独建表（F3-T01 用）：

```sql
CREATE TABLE gov_join_edge (
    id BIGSERIAL PRIMARY KEY,
    from_model VARCHAR(128) NOT NULL,
    to_model VARCHAR(128) NOT NULL,
    join_type VARCHAR(32) NOT NULL,     -- many_to_one etc.
    on_clause TEXT NOT NULL,            -- 未渲染模板
    relationship VARCHAR(16) NOT NULL,  -- inner | left
    fanout_warning BOOLEAN NOT NULL DEFAULT FALSE,
    approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT,
    manifest_version VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE (from_model, to_model, on_clause)
);
```

本 Task 建表 + ingest 能力，F3 消费。

### 6. 并发与幂等

- Ingest 任务用 Redis 分布式锁（`semantic:ingest:<manifest_hash>`），锁失败直接返回 409
- 同一 manifest_hash 再次 ingest 是幂等的（diff 为空）
- ingest 过程中的查询请求继续用旧元信息，无需阻塞

### 7. DTO & repository

```java
@Entity
@Table(name = "gov_join_edge")
public class GovJoinEdge { ... }

public interface GovJoinEdgeRepository extends JpaRepository<GovJoinEdge, Long> {
    List<GovJoinEdge> findByManifestVersion(String v);
    List<GovJoinEdge> findByFromModel(String from);
}

@Service
public class ManifestIngestor {
    public IngestResult ingest(InputStream manifestJson) { ... }
    public IngestResult ingestFromObjectStorage(String key) { ... }
}
```

## 影响范围

| 类型 | 文件 |
|---|---|
| 新建 | `service/semantic/manifest/*` 四个类 |
| 新建 | `domain/governance/GovJoinEdge.java` |
| 新建 | `repository/governance/GovJoinEdgeRepository.java` |
| 新建 | `config/liquibase/changelog/9002__sprint13_semantic_snapshot.xml` |
| 新建 | `web/rest/semantic/SemanticManifestResource.java`（POST ingest） |
| 修改 | `domain/governance/GovIndicatorDefinition.java`（加 8 字段） |
| 修改 | `service/governance/IndicatorDefinitionService.java`（兼容 orphaned 状态） |
| 测试 | `ManifestIngestorTest.java`、`MetricDefinitionMapperTest.java` |

## 验证

- [ ] 3 个样例 manifest.json（放 `it/sample-manifest/`）能完整 ingest
- [ ] ingest 后 `gov_indicator_definition` 有 N 行、`gov_join_edge` 有 M 行
- [ ] 手工改 manifest（加/删/改 metric）再次 ingest，diff 行为正确
- [ ] 单元测试：mapper 覆盖所有字段映射 + orphan 逻辑
- [ ] 审计日志正确记录

## 完成标准

- [ ] `ManifestIngestor.ingest()` 能在 5 秒内处理 1000 个 metric 的 manifest
- [ ] 没有原子指标的 UI 新建入口（通过 `IndicatorDefinitionService` 加校验：如果 `dbt_unique_id` 非空且 `sync_status=active`，UI 修改返回 403）
- [ ] 单元测试覆盖率 ≥ 80%
- [ ] changelog 在 `liquibase-changes.md` 登记
