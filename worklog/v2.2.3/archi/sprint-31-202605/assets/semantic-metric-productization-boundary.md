# 语义指标产品化边界

## 决策

`dts-metrics` 是 DTS 的增值业务服务，不是基础大数据主链路的必需组件。基础版客户如果懂 SQL，只需要：

```text
Connector Center -> ODS -> dbt -> Catalog -> asset_grant -> BI/大屏消费
```

即可完成基础大数据业务。指标语义、行业指标包、DWS/ADS 可视化建模属于商业增强能力。

## platform 保留能力

| 能力 | 归属 |
|---|---|
| IAM / 登录 / 用户角色 | platform / admin |
| asset_grant / 密级 / 授权审批 | platform |
| Catalog / OpenMetadata / OpenLineage | platform |
| 数据源连接和凭据 | platform |
| dbt 发布门禁和 Airflow 触发 | platform |
| 审计和审批事实源 | platform |

## dts-metrics 归属能力

| 能力 | 归属 |
|---|---|
| 主题域、业务对象、维度、指标 | dts-metrics |
| 指标公式 DSL v1 | dts-metrics |
| DWS/ADS 候选 artifact 生成 | dts-metrics |
| metric-pack 校验、预览、导入 | dts-metrics |
| 指标口径文档和示例行业包 | dts-metrics |

## 兼容策略

- platform-webapp 保留统一入口。
- 旧 `/api/semantic/**` 兼容一个 Sprint，后续转为 metrics 代理或明确弃用。
- `dts-metrics` 不能直接访问 platform 表、数据源密码或本地用户角色。
- 发布前必须调用 platform asset contract、permission check、audit 和 dbt publish gate。

## metric-pack v0.1

合作方交付配置包，不交付平台源码：

```text
manifest.yml
domains/*.yml
objects/*.yml
dimensions/*.yml
metrics/*.yml
models/*.yml
datasets/*.yml
dashboards/*.yml
```

约束：

- 不允许任意 SQL。
- 只能引用 platform 已登记资产和字段。
- 预览、发布和回滚必须写 platform 审计。
- 行业应用页面由 app/app-pack 管理，不再塞回 platform 菜单。
