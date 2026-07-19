# T01：建立存量盘点与迁移 dry-run

**优先级**：P0
**状态**：READY
**依赖**：F1-T02、F3-T01

## 目标与用户结果

在冻结或迁移前，形成可复核的对象、域、模型、维度、指标、表映射、产物、运行和评审清单，禁止凭代码搜索直接删数据。

## 范围与不做

- 范围：dts-platform 两套业务对象、semantic 链、ModelSpec、domain 映射和所有消费者。
- 不做：dry-run 不写目标表，不自动创建缺失业务分类。

## 输入基线

2026-07-18 当前环境只读快照：catalog domain=6、semantic subject domain=7、semantic objects=5、models=4、table mappings=9、dimensions=4、metrics=5、artifacts=13、runs=2、reviews=2；modeling business object/model spec/sprint64 process 均为 0。该数字只作为预期，实施时必须重新采集。

其中 4/5 个旧 object.domain_id 无法匹配 catalog domain，必须进入 `NEEDS_CLASSIFICATION`，不能静默制造新分类。

## 输出产物

- source/consumer inventory；
- 每条记录分类和目标映射；
- `AUTO_DIMENSION/AUTO_FACT_MERGE/MANUAL_SPLIT/ARCHIVE_ONLY/NEEDS_CLASSIFICATION` 结果；
- 行数、引用数、校验和和孤儿报告；
- 可重跑的 migration batch ID。

## 详细设计

1. 扫描 DB FK、HTTP/route/menu/schema/test 字符串和 GitNexus callers，分别报告，不能用单一调用图替代全量审计。
2. dry-run 读取 catalog domain 映射；无显式匹配时不按名称近似自动确认。
3. 表映射中的 primary/joined、alias、joinType、joinExpression、sortOrder 必须映入扩展 SourceRef；旧数据没有 alias 或无法从 tableRole 无歧义还原 joinType 时保留 null/legacyRawRole 并报告冲突，禁止伪造。
4. object businessKey 合并 grain.keys；冲突时 MANUAL_SPLIT。
5. 生成 JSON + Markdown 人工评审报告，记录 checksum、source IDs、proposed target IDs 和原因。

## 影响范围

- semantic/modeling Liquibase 表与 repositories/services
- migration command/resource（实现阶段新增）
- dts-admin menu DB、前端 route/context/tests
- dts-metrics manifest/DTO/tests
- Sprint27 console consumer

## 异常与权限

- 按租户隔离扫描；无权租户不出现在报告。
- 单表读取失败使整个批次 NOT_READY，不以零行继续。
- 报告中敏感 SQL/字段按权限脱敏。

## 实施与测试设计

1. 用固定夹具写分类、域缺失、JOIN、冲突和孤儿测试。
2. 实现 read-only dry-run 和校验和。
3. 在空库、当前库和构造冲突库运行两次，验证输出稳定。
4. 由 reviewer 抽样逐条核对，不通过不进入 T02/T03。

## 验证证据

- `it/evidence/migration/business-object-dry-run.json`
- `it/evidence/migration/reconciliation.md`
- `it/evidence/gitnexus/object-consumer-impact.txt`

## 完成标准

- [ ] 行数/引用数/校验和完整，失败不会伪装为零。
- [ ] 4/5 域不匹配场景进入人工分类。
- [ ] JOIN、key、grain、产物和运行均有目标。
- [ ] 重复 dry-run 结果稳定。
