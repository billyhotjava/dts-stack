# Connector Center 证据目录

本目录用于最终统一测试阶段归档 Sprint-31 F2 的证据。当前按用户要求暂不执行中途编译、测试和容器重建。

## 待最终执行

```bash
curl -sS http://127.0.0.1:18082/api/infra/data-sources
```

```bash
curl -sS -X POST http://127.0.0.1:18082/api/infra/data-sources/{id}/ods-precheck \
  -H 'Content-Type: application/json' \
  -d '{"tables":["public.demo_table"],"odsSchema":"ods_demo"}'
```

## 验收点

- 列表失败显示明确错误，不返回伪空列表。
- 连接参数变更生成 `NEEDS_REVIEW` 影响单。
- 默认预检不执行源表精确 `count(*)`，结果包含 `SOURCE_ROW_COUNT_ESTIMATE`。
- JDBC/API/文件数据源在列表中展示不同链路能力。
