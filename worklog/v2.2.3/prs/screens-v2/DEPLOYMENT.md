# 花卉租赁 V2 大屏部署记录

日期：2026-09-14。目标环境：DTS v2.2.3，https://bi.yuzhicloud.com 。

## 本次完成

- 12 份大屏配置、258 个组件，已通过 Chrome 的文件导入入口创建为 V2.2.3 草稿，编号 15–26。
- 12 个草稿均为 INTERNAL，数据域为花卉数据管理（c90dfed4-7060-4c3d-9f2f-9d2501176df0），数据库为数仓 biadmin（1）。
- 原编号 3–14 的 V2 草稿保留。当前共 24 个草稿，V2.2.3 搜索结果 12 个，已发布 0。
- 旧 xycyl 表名及 DATABASE_ID 占位符已替换；SQL 参数都有对应全局变量。
- 经营金额限定有效且已结束的单据；租金变动额与月租收入分开；养护工作量改为真实养护记录；回收计划量与实际量分开。
- 经营总览月度趋势先按所选日范围过滤再汇总，避免日期不在月初时遗漏首月。
- 经营总览、审批日志及经营趋势的页面查询已返回正确字段，样例仍为空或计数为零。未执行全面测试、编译或容器更新。

各大屏地址见 manifest.json。prs-flower-screens-v2.zip 是 12 个 JSON 和清单的归档集合；页面导入应选择其中单个大屏 JSON，不是一次导入 12 个屏的专用包。

## 当前阻塞

1. 接入任务 prs_db（5）本次执行 20，以及系统自动重试 21、22、23，均在自动建表阶段失败。最后一次重试于 09:30:14 耗尽，未进入 Addax 数据写入。
2. 页面错误为“无法获取源表字段信息”；服务日志实际错误为 `No suitable driver found for jdbc:mysql`。连接 props 已含 `com.mysql.cj.jdbc.Driver` 和 `mysql-connector-j-9.7.0.jar`，正式目录和容器挂载也存在该驱动。
3. `IngestionSourceResolver.resolve()` 组装 readerConfig 时未携带 props 的 driverClass/driverVersion；`TargetTableProvisioner` 又从 readerConfig 构造 JDBC 信息，导致 `JdbcMetadataService` 无法加载该驱动。
4. 同时发现同一元数据读取类的 MySQL 参数不一致：listTables 已将数据库传入 catalog，readColumns/readPrimaryKeyColumns/readIndexes 仍传入 schema。后者是静态发现的潜在后续问题，本次尚未越过驱动失败到达这一阶段。
5. 模型发布单 e029a70f-0e2d-4d0e-a6c1-72c4ec5187df 仍为 QUALITY_RUNNING。页面能正确显示所属 31 个模型；质量上下文能读取 31 个资产、37 条规则，返回 MODEL_SPEC_GOVERNANCE_QUALITY_FAILED，已不再是此前的证据读取 SQL 错误。
6. 首屏导入返回的密级推导证据为 BLOCKED_UPSTREAM / CONSUMER_CLASSIFICATION_SOURCE_MISSING。大屏未发布，不能把可编辑草稿等同于消费资格就绪。

接入代码修复已发出确认请求，尚未收到回复。本次遵守“界面报错不直接改程序或数据库”的约束，未修改服务源码或业务数据库。

## 发布前仍需处理

- 修复并更新接入服务后，通过页面重新执行 ODS 同步，再重新物化模型、运行质量检查、完成模型/数据集发布及大屏发布。
- 审批操作和租期调整日志暂读取已登记 ODS，并关联 V2 订单；现有 31 个模型未覆盖这两类日志模型。
- V2 回收模型不含库房维度，相关组件改为项目/回收人完成量，不声称具备库房分析。
- “挂起超7天”是观察阈值，不代表合同约定的超期。
- 首次进入编辑器会在日期变量就绪前发送请求，出现 Missing required parameter: dateFrom；变量就绪后再次请求成功。经营总览还观察到一次查询并发预算 429。
- SQL 保留 NULL，但当前数值组件的 toNumber 将 NULL 转为 0；空值显示尚需处理，不能据大屏的零判断真实无业务。
- 旧配置的 valueField、系列顺序及表列可由兼容映射读取，但“数据配置流程”只统计 _fieldMapping，因而仍显示“待映射”。
- 钻取明细屏目前可独立打开；页面“选择大屏”入口仅提供已发布大屏，需先完成明细屏发布，再配置跨屏点击跳转。当前不能宣称完整钻取链路已上线。
