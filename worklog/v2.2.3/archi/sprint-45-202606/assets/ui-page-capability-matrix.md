# UI 页面能力矩阵

## 使用方式

本矩阵用于每个页面整改前的准入检查。任何页面若不能回答“入口在哪里、展示什么真实状态、主按钮做什么、完成后去哪里”，就不能标记为完成。

## 总矩阵

| 页面组 | 路由/入口 | 当前风险 | 目标产品角色 | 归属任务 | 必备按钮 | 必备组件 | 真实性证据 |
|--------|-----------|----------|--------------|----------|----------|----------|------------|
| 工作台总览 | `/workbench` | 领导视角和数据管理动作割裂 | 数据中台首页/总览 | F2-T01 | 查看数据管理、查看待办、查看异常 | 主题概览、风险列表、快捷动作 | `/api/workbench/leader-overview` 或真实聚合接口 |
| 数据管理工作台 | `/workbench/data-management` | 已有主题看板但下一步动作仍需强化 | 产品主控台 | F2-T01 | 配置数据源、治理检查、发布服务、查看运维 | 黄金链路 Stepper、主题卡、动作面板 | `/api/golden-chains` |
| 待办中心 | `/workbench/todo` | 菜单有入口但路由未注册 | 全局待办与阻断项中心 | F1-T02 / F2-T04 | 处理、跳过、刷新、返回工作台 | 待办列表、阻断原因、空态 | WorkflowCenterPage 或待办 API |
| 数据源 | `/foundation/data-sources` | 单页能力与入湖/建模未串起 | 数据接入起点 | F3-T01 | 新建、测试连接、发现表、创建入湖任务、查看链路 | 数据源表格、连接状态、Schema 预览抽屉 | 数据源 API、连接测试 API |
| 连接器 | `/foundation/connectors` | 与数据源入口分离 | 接入方式配置 | F3-T03 | 启用、停用、配置、查看模板 | 连接器卡片、状态 Tag、配置 Drawer | 连接器 API |
| JDBC 驱动 | `/foundation/jdbc-drivers` | 偏运维工具感 | 驱动资产管理 | F3-T03 | 上传、校验、启用、禁用、删除 | 驱动表、版本 Tag、校验结果 | 驱动 API |
| 元数据目录 | `/catalog/metadata` | 与资产门户和血缘割裂 | 元数据同步入口 | F3-T03 / F4-T02 | 同步、查看差异、登记资产 | 同步状态、差异表、错误详情 | catalog/metadata API |
| ETL 转换 | `/explore/etl/transform` | 运行结果未反向进入工作台 | 入湖后转换执行 | F3-T02 | 新建转换、运行、停止、重跑、查看实例 | DAG/列表、运行状态、日志 Drawer | ETL/实例 API |
| 任务编排 | `/explore/etl/orchestration` | 调度与任务实例割裂 | 调度编排中心 | F3-T02 | 新建 DAG、启用调度、暂停、补数、查看告警 | DAG 表、Cron 展示、实例列表 | 调度 API |
| 开发项目 | `/studio/projects` | 菜单假入口 | 数据开发项目入口 | F1-T03 / F3-T04 | 新建项目、导入、进入 SQL 建模、归档 | 项目列表、成员/环境 Tag、空态 | 项目 API 或明确前端聚合 |
| SQL 建模 | `/studio/sql-modeling` | 菜单假入口/未映射 | SQL 建模工作台 | F1-T04 / F3-T04 | 新建模型、预览、校验、生成 ODS、发布 | 编辑器、Schema 面板、结果预览、发布门禁 | SqlModelingPage + 建模 API |
| dbt 文件 | `/modeling/dbt-files` | 工程对象暴露 | 建模方案文件管理 | F3-T04 | 导入、校验、预览、发布 | 文件树、diff、校验面板 | dbt 文件 API |
| 治理中心 | `/governance/*` | 入口多但首页不完整 | 发布门禁中心 | F4-T01 | 新建规则、运行质量、修复阻断、查看报告 | 治理总览、规则卡、质量趋势 | governance/quality/rules API |
| 数据安全 | `/security/data-security` | 与权限审批割裂 | 分级分类与安全策略 | F4-T04 | 新建分级、绑定资产、申请例外 | 分级树、策略表、审批 Drawer | security API |
| 资产目录 | `/catalog/assets`, `/catalog/search` | 资产详情动作不足 | 数据资产门户 | F4-T02 | 申请权限、查看血缘、生成产品、创建报表 | 搜索、资产表、详情 Drawer、血缘入口 | catalog/assets API |
| 数据产品 | `/catalog/data-products`, `/services/products` | 目录产品和服务产品语义重复 | 数据产品生命周期 | F4-T03 / F6-T02 | 新建、发布、下线、申请、查看消费 | 产品卡、状态 Tag、发布门禁 | data-products API |
| 权限审批 | `/security/dataset-access-approval` | 申请与资产动作未闭环 | 数据访问审批 | F4-T04 | 申请、批准、驳回、撤回、查看审计 | 申请单、审批时间线、权限范围 | permission API |
| 指标中心 | `/bi-apps/metrics/*` | iframe/工程语言割裂 | 指标资产与语义消费入口 | F5-T01 | 新建指标、发布、查看血缘、创建报表 | 指标卡、语义状态、iframe 壳层 | dts-metrics 或平台语义 API |
| BI 数据 | `/bi/data` | 与资产/产品缺少上下文 | 报表数据集选择 | F5-T02 | 选择数据集、预览、创建问题、创建报表 | 数据集表、字段面板、预览表 | BI 数据集 API |
| BI 问题 | `/bi/questions` | 分析问题与主题脱节 | 自助分析入口 | F5-T02 | 新建问题、运行、保存、生成图表 | 查询面板、结果表、图表预览 | BI questions API |
| BI 看板 | `/bi/dashboards` | 看板不回溯数据链路 | 报表交付物 | F5-T02 | 新建看板、添加图表、发布、分享 | 看板列表、发布状态、权限 Tag | BI dashboard API |
| 数据大屏 | `/bi/screens` | 管理能力强但和链路未串起 | 大屏交付中心 | F5-T03 | 新建、编辑、预览、发布、复制、导出、删除 | 大屏表、模板选择、预览 Drawer | screens API |
| 数据 API | `/services/apis` | API 与数据产品/权限脱节 | 数据服务发布入口 | F6-T01 | 新建、测试、启用、禁用、复制地址、查看调用 | API 表、测试 Drawer、调用统计 | services/apis API |
| 共享交换/令牌 | `/services/tokens` | 菜单名和令牌功能不一致 | 安全共享凭证中心 | F6-T03 | 生成、复制一次、撤销、续期、查看审计 | Token 表、范围选择、过期提示 | tokens API |
| 任务运维 | `/ops/overview`, `/ops/instances`, `/ops/alerts`, `/ops/backfill` | 只看运行态，不能反向定位链路 | 运维验收中心 | F6-T04 | 重试、补数、查看日志、查看源任务、创建待办 | 状态总览、实例表、告警列表、日志 Drawer | ops API |
| 事件审计发布治理 | `/ops/events`, `/ops/audit-evidence`, `/ops/release-governance` | 静态路由存在但菜单隐藏 | 客户验收证据中心 | F6-T05 | 查看事件、导出证据、发起发布复核 | 事件流、证据包、发布门禁 | audit/release API |

## P0 断点

| 断点 | 处理方式 | 验收 |
|------|----------|------|
| `/workbench/todo` | 注册静态路由或动态 resolver，指向真实待办页面 | 菜单点击不 404，空态/异常态正常 |
| `/studio/projects` | 提供项目页或从菜单下线并改为真实开发入口 | 菜单不再落入空白或 fallback |
| `/studio/sql-modeling` | 接入 `SqlModelingPage` 或同等真实页面 | 新建/预览/校验/发布按钮可见 |
| `/services/products` | 与 `/catalog/data-products` 语义收口 | 菜单名、页面标题、按钮含义一致 |
| `/services/tokens` | “共享交换”与令牌管理分层表达 | 不把安全凭据伪装成数据交换产品 |

## 客户验收语言

- 使用“数据源、入湖任务、数据资产、质量检查、权限审批、报表、数据 API、数据产品、运行告警”等客户能理解的词。
- 避免默认展示“F1/F2、Sprint、artifact、permissive、iframe、dbt 文件”等工程词。
- 技术词必须出现在详情或高级设置中，并提供业务解释。
