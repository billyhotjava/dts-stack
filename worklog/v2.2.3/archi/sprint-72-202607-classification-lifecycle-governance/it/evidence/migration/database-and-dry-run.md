# 数据库迁移与存量 dry-run

## Liquibase

- 现有升级库已应用 Sprint-72 catalog facts、monotonic guards、propagation、lifecycle、
  consumer dependency、migration control 变更集。
- 独立空库 `sprint72_liquibase_empty` 完成 354 个变更集并成功启动 Platform。
- 空库验证确认 8 张 Sprint-72 核心表存在。
- 验证结束后已永久删除该临时空库；其中不含用户业务数据。

## 真实 dry-run

- run id：`1ce6696c-a404-42e0-a92c-690c24561d0c`
- 状态：`DRY_RUN_COMPLETE`
- 总记录：8991
- 可自动迁移：942
- 阻断：8049
- 已应用：0
- 双读差异：1000（接口返回上限）
- 报告校验和：`539f7cea2bbf5f059ecaca84347a97519203387e46484d183753a2b20e9eebd6`

dry-run 只写迁移控制报告。由于仍有阻断和双读差异，本轮没有执行 apply，也没有冻结任何旧写入口。

最终源码已补充 column 从所属 table/dataset 继承密级的候选逻辑，并由
`CatalogClassificationMigrationServiceIT` 在隔离 PostgreSQL 中验证通过。现网 dry-run 运行于该修正
部署前，因此上述 8049/1000 仍是生产放行基线，不能用隔离测试结果替换；部署后必须重新 dry-run。

结论：Liquibase 与隔离迁移逻辑 `PASS`；当前存量生产放行 `NO-GO`。
