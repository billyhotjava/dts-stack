# T01: IngestionExecution 扩展 source/target 表字段

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

让 `IngestionExecution` 在执行开始时就持久化"这次跑的是从哪些表到哪些表"，作为 lineage 回写和故障溯源的事实依据。

## 技术设计

### 数据库变更（Liquibase）

在 `dts-ingestion/src/main/resources/config/liquibase/changelog/` 新增：

```
20260430_01_ingestion_execution_lineage_columns.xml
```

新增列：

| 列名 | 类型 | 说明 |
|------|------|------|
| `source_tables` | `jsonb` / `text` | JSON 数组，元素为 `{datasource_id, schema, table}` |
| `target_tables` | `jsonb` / `text` | JSON 数组，同上 |
| `lineage_event_id` | `varchar(64)` | 关联 OpenLineage `runId`，可空 |
| `lineage_synced_at` | `timestamptz` | F2 写入 OpenLineage 后回填 |

索引：

```sql
CREATE INDEX idx_ingestion_execution_lineage_event ON ingestion_execution(lineage_event_id);
```

### 实体变更

- `IngestionExecution.java`：新增 4 个字段 + getter/setter；JSON 列用 `@Type(JsonBinaryType.class)` 或自定义 converter，与项目现有 `JsonNode` 列处理一致。
- `IngestionExecutionDTO.java` / `IngestionExecutionMapper.java`：同步新增字段。

### 写入时机

`IngestionTaskService.startExecution()`（或现有等价方法）在创建 IngestionExecution 时，从 `IngestionTask.tableMapping` 解析出 source/target 列表立即写入；不要等 execution 完成。

## 影响范围

- `dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/domain/IngestionExecution.java`
- `dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/dto/IngestionExecutionDTO.java`
- `dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/mapper/IngestionExecutionMapper.java`
- `dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskService.java` — 写入时机
- `dts-ingestion/src/main/resources/config/liquibase/changelog/20260430_01_ingestion_execution_lineage_columns.xml`
- `dts-ingestion/src/main/resources/config/liquibase/master.xml`
- `dts-ingestion/src/test/java/.../IngestionTaskServiceTest.java` — 新增单测

## 验证

- [ ] `./mvnw -pl source/dts-ingestion liquibase:update` 在 dev 环境成功
- [ ] `./mvnw -pl source/dts-ingestion test` 全绿
- [ ] 创建一条 jdbc 任务并触发 execution，DB 中 `source_tables`、`target_tables` 不为空
- [ ] 任务用空 `tableMapping` 创建（异常分支），execution 仍能写 `[]` 而不是 NULL，避免后续 NPE
- [ ] modernizer-maven-plugin 检查通过（不要在新代码用 `Optional.get()`）

## 完成标准

- [ ] Liquibase 迁移已合并到 master.xml
- [ ] `IngestionExecution` 实体与 DTO 携带新字段
- [ ] `IngestionTaskService` 在 execution 创建时落 source/target 表
- [ ] 单测覆盖：jdbc 单表、jdbc 多表、file 单表、空 tableMapping 兜底
