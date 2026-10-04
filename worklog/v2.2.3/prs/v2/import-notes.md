# 花卉 v2 页面导入记录（2026-09-13）

外部 Chrome 使用 xiezm 完成导入：31 个新建草稿，DWD 17、DWS 5、ADS 9；0 失败、0 阻断。模型字段发布密级统一 INTERNAL，绑定 19 张数仓 PostgreSQL ODS。结果及模型 ID 见 import-result.json。未发布、未物化、未运行 SQL 业务测试。

## 包修正

1. 显式设置物理 alias，避免中文业务名称被作为物理表名校验。
2. 项目、摆放位置维度改为直接关联 ODS；保留原业务连接口径，适配当前维度不得依赖模型的规则。
3. sourceRefs 补齐间接 ODS 及分层；原包只声明直接 ODS 时，预览展开传递来源后分层为 null。

## 本次发现的服务缺陷（未改平台代码）

- 接入写出的 ODS 目录记录仍使用原 MySQL 连接 sourceId，harvestStatus 为 null，且无表字段目录。即使补齐治理与结构，来源登记仍报 SOURCE_NOT_AVAILABLE。通过数仓的正常 JDBC 目录采集生成目标 PostgreSQL 资产后恢复可用。
- /api/datasets/{id}/sync-schema 在事务提交前启动异步任务；本次 worker 查询不到刚创建的任务，抛出 NoSuchElementException，界面状态仍为 QUEUED。任务 ID：131f721e-a4e9-4595-9cd1-f9e7a88c1b9c。此任务没有完成结构同步。
- 导入页“业务名称”可被后端用作物理名称：没有显式 alias 时中文名称触发 targetPhysicalName 校验。当前包已通过显式 alias 避开，平台行为未修改。

## 已执行的目录操作

- 原 MySQL 连接下对应的 19 个 ODS 目录记录已补齐 INTERNAL、花卉数据域、ODS 分层及当前用户/部门；另经现有表元数据导入接口登记真实 19 表、663 字段。上述记录未用于最终模型来源。
- 数仓正常 JDBC 采集发现 public 下 166 表，新建 160 个目标资产、更新原有 6 个，未删除资产。最终只将需要的 19 个目标 ODS 治理为 INTERNAL 并登记到建模规划。
- 创建并发布花卉租赁数据集市 DM_PRS、花卉租赁经营分析主题域 PRS_OPERATIONS。保留已有项目来源登记。
