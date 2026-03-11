# Sprint-5 集成测试

## 脚本与资源位置

所有可执行文件位于 `bin/project-progress/`：

```
bin/project-progress/
├── deploy.sh                              # 一键部署（创建项目 + 导入模型）
├── manifest/
│   └── models.tsv                         # dbt-import 清单（11 个模型）
├── seed-ods-project-progress.sql          # ODS 测试数据（40 条）
├── validate-indicators.sql                # 35 个指标验证查询
└── screen-template-project-progress.json  # 大屏模板 JSON
```

dbt 模型位于 `services/dts-dbt/models/{dim,dwd,dws,ads}/`。

## 部署命令

```bash
# 一键部署
export API_BASE="https://bi.example.com"
export TOKEN="<bearer-token>"
export SOURCE_DATA_SOURCE_ID="<数据湖连接 UUID>"
bash bin/project-progress/deploy.sh --plan-name "项目进度分析"

# 试运行
bash bin/project-progress/deploy.sh --dry-run
```

## 验证命令

```bash
# 导入测试数据
psql -h localhost -U dts_platform -d dts_platform \
  -f bin/project-progress/seed-ods-project-progress.sql

# 运行 dbt（在 dts-dbt 容器中）
dbt run --select tag:project-management

# 验证指标
psql -h localhost -U dts_platform -d dts_platform \
  -f bin/project-progress/validate-indicators.sql
```

## 执行记录

| 日期 | 环境 | 操作 | 结果 | 备注 |
|------|------|------|------|------|
| - | - | 待现场执行 | - | - |
