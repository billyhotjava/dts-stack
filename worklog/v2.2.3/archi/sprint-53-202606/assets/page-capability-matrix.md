# Page Capability Matrix

| Page | Path | Menu Source | Component | Capability | Primary Controls | API/Data Contract | Risk | Next Task |
|------|------|-------------|-----------|------------|------------------|-------------------|------|-----------|
| 指标工作台 | `/modeling/metric-workbench` | `portal-menu-seed.json` studio `metric-modeling` | `MetricWorkbenchPage.tsx` | 从主题域、业务对象、指标关系进入可视化指标设计 | 主题域树、React Flow 画布、右栏公式/消费数据 | `/semantic/subject-domains`, `/semantic/business-objects`, `/semantic/metrics`, `/semantic/models` | PARTIAL | F3/T02, F3/T03 |
| 主题域 | `/modeling/semantic/subjects` | studio `metric-modeling` | `SemanticSubjectsPage.tsx` | 维护指标建模的业务主题域 | 列表、新建、编辑 | `/semantic/subject-domains` | REAL | F1/T02 |
| 业务对象 | `/modeling/semantic/objects` | studio `metric-modeling` | `SemanticObjectsPage.tsx` | 维护业务对象和表映射 join 关系 | 对象列表、join 图、新建对象 | `/semantic/business-objects`, `/semantic/business-objects/{id}/table-mappings` | PARTIAL | F3/T02 |
| 指标管理 | `/modeling/semantic/metrics` | studio `metric-modeling` | `SemanticMetricsPage.tsx` | 维护指标定义、公式类型和业务对象归属 | 新建指标、编辑公式 | `/semantic/metrics`, `/semantic/business-objects` | REAL | F3/T04 |
| 模型管理 | `/modeling/semantic/models` | studio `metric-modeling` | `SemanticModelsPage.tsx` | 维护 DWS/ADS 语义模型并触发预览/制品/运行/审核 | 类型筛选、预览、生成制品、触发运行、提交审核 | `/semantic/models/*` | REAL | F1/T03 |
| 发布审核 | `/modeling/semantic/publish` | studio `metric-modeling` | `SemanticPublishPage.tsx` | 审核并发布 dbt、注册 BI 数据集和血缘 | 通过、拒绝、发布 dbt、日志、制品 | `/semantic/models/{id}/publish-dbt`, register endpoints | PARTIAL | F3/T03 |
| 运行监控 | `/modeling/semantic/runs` | studio `metric-modeling` | `SemanticRunsPage.tsx` | 监控语义模型运行历史和 RUNNING 轮询 | 模型选择、手动触发、日志 Drawer | `/semantic/models/{id}/runs` | REAL | F4/T01 |
| 旧指标兼容入口 | `/bi-apps/metrics/*`, `/modeling/semantic-center/*`, `/bi/semantic-modeling` | legacy route only | `MetricsServiceRedirect` | 旧链接兼容 | React Router redirect | platform `/modeling/*` routes | COMPAT | DONE |
| 数据管理工作台 | `/workbench?section=data-management` | workbench | `DataManagementWorkbenchPage.tsx` | 串起数据源、入湖、资产、治理、消费和运行 | 配置数据源、治理检查、查看资产、查看血缘、创建报表 | `/golden-chains`, `/golden-chains/{key}` | PARTIAL | F3/T01, Sprint-54 |

## Notes

- `DWS/ADS` are the default visual metric entry. `DWD` remains advanced upstream modeling, while `ODS/STG` are lineage/diagnostic surfaces.
- The product surface must use platform pages first. Old `dts-metrics` iframe routes have been replaced by compatibility redirects in Sprint-53/F1.
