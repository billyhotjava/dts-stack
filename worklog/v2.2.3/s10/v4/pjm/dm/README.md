# PJM 数仓规划与模型创建手册

本目录用于指导实施人员把 `dbt_model/ods_ddl` 中的项目管理 ODS 数据，按 DTS 当前建模逻辑登记为数仓规划、维度、DWD 明细表、DWS 汇总表和 ADS 应用表。

这是一套“先确认语义、再录入 DTS、最后导入和发布 dbt 实现”的操作手册，不会执行 ODS DDL，也不代替业务负责人确认数据域、业务过程、密级、负责人或业务主键。

## 使用顺序

1. [00-ODS分析与建模结论.md](00-ODS分析与建模结论.md)：先理解 10 张 ODS 表、当前已落地链路和已知缺口。
2. [01-数仓规划操作手册.md](01-数仓规划操作手册.md)：配置默认业务分类，创建数据域、业务过程、数据集市和主题域。
3. [02-维度与维度表操作手册.md](02-维度与维度表操作手册.md)：先确认维度定义，再建立 8 张业务维度表。
4. [03-DWD明细表操作手册.md](03-DWD明细表操作手册.md)：登记 10 张 DWD 事实模型及其字段角色、粒度和时间语义。
5. [04-DWS汇总表操作手册.md](04-DWS汇总表操作手册.md)：登记 10 张 DWS 主题汇总模型。
6. [05-ADS应用表操作手册.md](05-ADS应用表操作手册.md)：登记 15 张 ADS 指标与应用模型。
7. [06-dbt导入发布与验收手册.md](06-dbt导入发布与验收手册.md)：通过逆向建模导入现有 dbt 包，完成设计检查、构建、测试和发布。
8. [07-二期扩展表设计清单.md](07-二期扩展表设计清单.md)：记录 5 张扩展来源的画像、已实现链路和生产稳定键风险。
9. [08-指标定义与计算操作手册.md](08-指标定义与计算操作手册.md)：在模型上线后登记 76 个稳定指标，绑定实现字段并提交真实计算。

## 五条执行原则

1. 日常建模以数据域为主入口；“研究所业务”保留为默认业务分类，不在每次建模时重复选择。
2. 数据域按业务能力划分，不按 ERP、PLM、QMS、财务、考勤等来源系统机械地“一系统一域”。
3. 业务过程不能删除或用 `<domain>_default` 占位；只有 FACT 明细表和原子指标绑定真实业务过程，维度、DWS、ADS 不绑定业务过程。
4. ODS 和 STG 是贴源/技术处理层。当前模型工作台手工创建的业务模型从 DWD 开始；现有 STG 视图由 dbt 包统一导入和维护。
5. 密级是独立的安全治理事实。源字段 `classification` 不能映射成业务分类、数据域或普通分析标签，真实密级由有权人员确认。

## 当前交付边界

- 已由源码和仓库文件确认：规划策略、数据域、业务过程、维度定义、维度表、FACT/DWS/ADS 表单字段和发布门禁。
- 当前 dbt 主链确认覆盖：10 张 STG、8 张 canonical 维度、10 张 alias 辅助表、10 张 DWD 事实表、10 张 DWS、15 张 ADS，共 63 个 dbt 节点。
- 逆向导入投影为 43 个业务 ModelSpec 和 20 个技术节点；措施/物料链已实现，但候选自然键只通过当前测试样本，生产增量前必须复核。
- DTS 当前模型工作台的“从表/视图导入”按钮不可用，普通模型也没有来源/上游关系编辑器。现有 PJM dbt 链应从“逆向建模”导入；仅靠手工新建页不能完成普通模型的来源绑定和发布闭环。

## 主要依据

- ODS 全量 DDL：[`../dbt_model/ods_ddl/ods_create_tables_v2.sql`](../dbt_model/ods_ddl/ods_create_tables_v2.sql)（仅用于分析，脚本会删表重建）
- MySQL 源端与安全演进：[`../dbt_model/ods_ddl/mysql`](../dbt_model/ods_ddl/mysql)
- ODS 字段映射与核对：[`../dbt_model/ods_ddl/ods-field-mapping`](../dbt_model/ods_ddl/ods-field-mapping)、[`../dbt_model/ods_ddl/ods-verify`](../dbt_model/ods_ddl/ods-verify)
- dbt 分层规则：[`../dbt_model/model-governance.md`](../dbt_model/model-governance.md)
- dbt 当前模型：[`../dbt_model/models`](../dbt_model/models)
- F7 规划简化决策：[`../../../../sprint-87-202608-data-architecture-implementation/features/F7-建模规划上下文简化/README.md`](../../../../sprint-87-202608-data-architecture-implementation/features/F7-建模规划上下文简化/README.md)
