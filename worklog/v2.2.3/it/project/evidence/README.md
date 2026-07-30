# Demo 验收证据目录

本目录只归档实际执行证据，不放设计推测。

建议文件：

```text
01-source-ingestion.md
02-planning-metadata.md
03-standards-master-data.md
04-model-design.md
05-build-materialization.md
06-quality-failure-recovery.md
07-metrics.md
08-consumption-permission-lineage.md
09-ops-audit.md
```

每份证据至少记录：

- 环境、时间、操作者角色。
- Git commit 和镜像版本。
- 页面路由或 API。
- 对象 ID、revision、run ID。
- 输入批次和输出行数。
- 期望、实际、结论。
- 截图或响应文件的相对路径。
- 敏感信息脱敏说明。
- 未闭合项和下一步。

禁止归档：

- 数据库密码、token、Cookie、私钥。
- 客户真实数据。
- 仅 Mock 的“成功”结论。
- 无对象 ID/revision/run ID 的口头判断。

若当前环境缺少自动验收包 API，按上述 9 份材料手工归档，并在最终结论中明确“手工证据包”。
