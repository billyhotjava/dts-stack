# T04: 历史 execution 数据回填脚本

**优先级**: P1
**状态**: READY
**依赖**: T02

## 目标

把 T01 上线之前已经成功跑过的 IngestionExecution 也回填一份 lineage 边到 `catalog_dataset_lineage`，避免上线后 LineagePage 上"近 30 天"的图突然只有少数节点。

## 技术设计

### 形式

提供一个一次性 Spring Boot CLI 命令：

```
./mvnw -pl source/dts-ingestion spring-boot:run \
  -Dspring-boot.run.profiles=lineage-backfill \
  -Dspring-boot.run.arguments="--backfill.window-days=30 --backfill.dry-run=false"
```

新增 `LineageBackfillRunner`（`@Profile("lineage-backfill") @Component implements ApplicationRunner`）：

```java
@Override
public void run(ApplicationArguments args) {
    int windowDays = ...;
    boolean dryRun = ...;
    var since = Instant.now().minus(windowDays, DAYS);
    var executions = repo.findByStatusAndEndTimeAfterAndLineageSyncedAtIsNull(SUCCESS, since);
    for (var exec : executions) {
        if (exec.getSourceTables() == null) {
            // 老 execution 没有 T01 字段 → 从 task.tableMapping 即时解析
            exec.setSourceTables(parseFromTask(exec.getTaskId()));
            exec.setTargetTables(parseFromTask(exec.getTaskId()));
        }
        if (dryRun) { log.info("[DRY-RUN] would write lineage for {}", exec.getId()); continue; }
        try {
            platformLineageClient.write(AddaxLineageRequest.from(exec));
            exec.setLineageSyncedAt(Instant.now());
        } catch (Exception ex) {
            log.warn("backfill failed for execution {}", exec.getId(), ex);
        }
    }
}
```

### 安全栏

- 默认 `--backfill.dry-run=true`，必须显式传 `false` 才真正写
- 日志聚合 + 进度条（每 100 条一打）
- 限速：每秒 ≤ 50 条调用 platform，避免压垮

### 文档

`worklog/v2.2.3/sprint-20-202604/it/lineage-backfill-runbook.md` —— 操作手册，包含执行命令、回滚步骤（直接 DELETE WHERE relation_type='ADDAX' AND created_at < cutoff）、预期结果。

## 影响范围

- 新增 `dts-ingestion/.../task/LineageBackfillRunner.java`
- 新增 profile `application-lineage-backfill.yml`（关闭定时任务、降低线程池等）
- 新增 `worklog/v2.2.3/sprint-20-202604/it/lineage-backfill-runbook.md`

## 验证

- [ ] dry-run 模式打印计划，不写库
- [ ] 真实模式跑测试环境 30 天数据，`catalog_dataset_lineage` 出现对应 ADDAX 边
- [ ] 重复跑两次（幂等）：第二次没有新增（upsert 命中）
- [ ] 回滚 SQL 可清理仅 backfill 写入的边而不影响 T03 在线产生的边（按 `notes='backfill'` 字段区分）

## 完成标准

- [ ] CLI runner 已实现并跑通 dry-run
- [ ] 测试环境完成 30 天回填，验证图变完整
- [ ] runbook 文档已写完并 reviewed
