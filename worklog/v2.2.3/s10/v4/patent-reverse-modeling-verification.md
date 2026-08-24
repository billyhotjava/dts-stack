# 专利域逆向建模验证记录

验证时间：2026-08-23（Asia/Shanghai）

## 导入包

- 文件：`patent-reverse-modeling-import.zip`
- SHA-256：`8520efaef6f6245da4db25b038afcb0bfb0943d9f4a8600b5cb7aa9487d0c629`
- dbt Core：`1.10.22`
- PostgreSQL adapter：`1.10.0`
- ZIP 完整性：通过，包含 5 个文件
- 模型：`model.patent_reverse_model.dim_patent_lifecycle_status`
- 模型类型 / 分层：`DIMENSION / DWD`
- 物理来源：0
- dbt 模型依赖：0

该包使用静态专利生命周期状态维度，不引用现有模型、source、ref 或外部数据库对象。测试数据域编码为 `PATENT`。

## DTS 规划前置条件

- 业务分类：`研究所业务（S10_PRJ）`
- 独立数据域：`专利（PATENT）`
- 数据域 ID：`949ca350-a220-47e2-b7e1-7ff1d97bc56b`
- 规划上下文：`平台默认建模上下文`
- 规划 ID：`b1d7bcdc-887e-495e-8b93-8ae0d0ad5870`
- 维度定义：`专利生命周期状态`
- 维度定义 ID：`89b157ad-afb6-4a4c-b64f-179207530243`

这些对象只为本次专利域验证建立，不复用现有研究所业务模型。

## DTS 页面验证结果

通过 `/data-modeling/dimensions/reverse` 执行真实页面流程：

1. 上传 ZIP 并开始识别；
2. 选择已确认的规划上下文；
3. 将包内 `PATENT` 映射到“专利”数据域；
4. 显式确认字段发布密级；
5. 生成预览并生成模型。

检查结果：

- 包结构检查：`SUPPORTED`
- 导入投影：`IMPORTABLE`
- dbt 物化：`CERTIFIED`
- 识别模型：1
- 识别来源：0

预览结果：

- `ready=1`
- `create=1`
- `blocked=0`
- `conflict=0`

应用结果：

- 状态：`SUCCESS`
- 结果：`CREATED`
- 导入运行 ID：`1e0a953e-908a-40a2-a6d6-f55ac3d6a458`
- 应用尝试 ID：`8c07314f-dc4b-46cc-8ee6-0f5adaab7d1e`
- 新模型 ID：`b2b5dbd0-259c-312f-b433-de2ab24f29fd`
- 模型版本：`r1`
- 浏览器：Google Chrome `150.0.7871.128`
- 页面 console error：0
- 页面运行时 error：0

## 独立性核验

数据库持久化结果：

- 模型名称：`dim_patent_lifecycle_status`
- 模型状态：`DRAFT`
- 实现模式：`DBT_MANAGED`
- `domain_id` 指向独立“专利”数据域
- `source_refs` 数量：0
- `depends_on` 数量：0
- 导入结果 `dependency_json` 数量：0
- 模型血缘边数量：0

结论：专利域独立 ZIP 已通过 DTS 的“检查 → 预览 → 应用”完整链路，逆向建模功能正常；新模型未关联现有模型或物理来源。
