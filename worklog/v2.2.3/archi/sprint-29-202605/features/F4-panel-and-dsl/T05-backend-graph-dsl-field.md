# T05: 后端 IngestionTask.graph_dsl jsonb 字段

**优先级**: P0
**状态**: PARTIAL
**依赖**: T03

## 目标

dts-platform 后端 `IngestionTask` 实体新增 `graph_dsl jsonb` 列，含 Liquibase changelog；扩展 GET/PUT API 支持读写；服务端校验 dslVersion。

> 2026-05-05 复核：当前代码中 `IngestionTask` 实体实际位于 `source/dts-ingestion`，`dts-platform` 只是 proxy。GitNexus impact 对 `IngestionTask` 返回 CRITICAL（80 affected / 47 direct），本轮仅做最小持久化字段，不改变执行逻辑。

## 技术设计

### Liquibase changelog

`source/dts-ingestion/src/main/resources/config/liquibase/changelog/20260505_01_ingestion_task_graph_dsl.xml`：

```xml
<changeSet id="20260504-add-graph-dsl-to-ingestion-task" author="billy">
  <addColumn tableName="ingestion_task">
    <column name="graph_dsl" type="jsonb">
      <constraints nullable="true"/>
    </column>
  </addColumn>
</changeSet>
```

### 实体

```java
@Column(name = "graph_dsl", columnDefinition = "jsonb")
@Type(JsonType.class)            // hibernate-types 6（项目已有）
private JsonNode graphDsl;

public JsonNode getGraphDsl() { return graphDsl; }
public void setGraphDsl(JsonNode graphDsl) { this.graphDsl = graphDsl; }
```

### DTO

```java
private JsonNode graphDsl;
// getter/setter
```

项目当前为手写 Mapper，已补 `toDto` / `toEntity` / `partialUpdate`。

### Service 校验

```java
// IngestionTaskService.update(...)
if (dto.getGraphDsl() != null) {
    String version = (String) dto.getGraphDsl().get("dslVersion");
    if (!SUPPORTED_DSL_VERSIONS.contains(version)) {
        throw new BadRequestAlertException("Unsupported dslVersion: " + version, ENTITY_NAME, "dslunsupported");
    }
}
```

`SUPPORTED_DSL_VERSIONS` 初期 = `Set.of("1.0")`。

### 演进路径（YAGNI 注解）

当前方案：`dslVersion` 存在 `graph_dsl` jsonb 内（`graph_dsl ->> 'dslVersion'`）。

**何时需要拉出独立列 `dsl_version varchar`**：
- 需要按版本批量查询（如统计有多少任务还在用 v1.0）
- 需要在版本上加 DB 索引或外键
- 需要在不反序列化整个 jsonb 的前提下做版本路由

满足以上任一条件时，再开新 sprint 加列 + Liquibase 迁移 + service 同步写两边。当前**不预先优化**。

### REST API

PUT `/api/ingestion-tasks/{id}` 已存在，DTO 加字段后自动支持。
GET 单条返回 graphDsl。
列表 GET 不返回 graphDsl（避免 payload 过大），单独 GET `/api/ingestion-tasks/{id}/graph-dsl` 端点提供。

## 影响范围

| 类型 | 文件 |
|------|------|
| 新增 | Liquibase changelog（含 master.xml include） |
| 修改 | `IngestionTask.java`、`IngestionTaskDTO.java`、`IngestionTaskMapper`（自动）、`IngestionTaskService`、`IngestionTaskResource`（如需 dedicated endpoint） |
| 测试 | `IngestionTaskResourceIT`（新增保存 + 读取 IT） |

## 验证

- [ ] Liquibase 在 dev/CI 环境跑通，jsonb 列可见
- [x] PUT DTO 带 graphDsl → mapper 写入；GET 单条 DTO 返回 graphDsl
- [ ] dslVersion=999 被拒，错误 message 清晰
- [ ] IT 测试：保存 → 读取 → 反序列化（前端反序列化逻辑入 e2e 也行）
- [ ] 列表 GET payload 不变大（当前 DTO 会携带 graphDsl，需后续拆 summary DTO 或 dedicated endpoint）
- [x] Maven compile + mapper 单测通过

## 完成标准

- [x] DB schema 完成；`compile` 通过
- [ ] IT 用例覆盖：保存 / 读取 / 版本拒绝
- [ ] 已对齐 sprint-28 服务间鉴权（不影响 ingest filter）
- [ ] CHANGELOG.md（如有）记录 schema 变更

## 当前实现说明

- 已新增 `graph_dsl jsonb` Liquibase changelog 并 include 到 `source/dts-ingestion` master。
- 已在 `IngestionTask` / `IngestionTaskDTO` / `IngestionTaskMapper` 接入 `JsonNode graphDsl`。
- 已补 `IngestionTaskMapperTest` 覆盖双向映射与 partial update。
- 本轮未实现 dslVersion 后端校验、单独 graph-dsl endpoint、列表裁剪。
