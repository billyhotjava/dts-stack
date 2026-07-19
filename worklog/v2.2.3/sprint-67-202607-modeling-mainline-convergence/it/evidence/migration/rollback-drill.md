# F5 回滚演练记录

- 部署前保留镜像：`dts-platform:sprint67-f5-before`、`dts-metrics:sprint67-f5-before`、`dts-platform-webapp:sprint67-f5-before`。
- 新 changeset 仅扩展迁移/调用审计台账，不删除旧结构，旧数据仍可由只读兼容层读取。
- 相同 migration batch 可幂等重放，第二次返回 `replayed=true`，不重复写目标。
- 回滚边界：允许恢复旧 GET/read projection；旧 POST/PUT/PATCH 仍保持冻结，禁止恢复双写。
- 因退出门禁为 `NO-DROP`，本次无需也未执行数据恢复或物理结构恢复。
