# Sprint-5 集成测试

## 脚本与资源位置

域数据文件位于 `services/dts-dbt/deploy/project-progress/`，CLI 工具位于 `bin/`：

```
bin/
├── dts-plan                               # 项目空间管理
├── dts-deploy                             # 一键部署
└── dts-manifest-gen                       # 清单生成

services/dts-dbt/deploy/project-progress/
├── deploy.conf                            # dts-deploy 部署配置
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
bin/dts-deploy --config services/dts-dbt/deploy/project-progress/deploy.conf

# 试运行
bin/dts-deploy --config services/dts-dbt/deploy/project-progress/deploy.conf --dry-run
```

## 验证命令

```bash
# 导入测试数据
psql -h localhost -U dts_platform -d dts_platform \
  -f services/dts-dbt/deploy/project-progress/seed-ods-project-progress.sql

# 运行 dbt（在 dts-dbt 容器中）
dbt run --select tag:project-management

# 验证指标
psql -h localhost -U dts_platform -d dts_platform \
  -f services/dts-dbt/deploy/project-progress/validate-indicators.sql
```

## 执行记录

| 日期 | 环境 | 操作 | 结果 | 备注 |
|------|------|------|------|------|
| - | - | 待现场执行 | - | - |
