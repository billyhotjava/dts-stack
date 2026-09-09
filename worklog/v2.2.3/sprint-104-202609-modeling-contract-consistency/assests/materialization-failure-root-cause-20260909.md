# 汇总模型物化失败及原因缺失（2026-09-09）

## 已复现的根因

外部 Chrome 对“项目进度汇总” r3（8898d08d-2ac2-47c8-b6e8-22bd6425e20b）执行物化。
候选 03618653-5a18-40ff-9ef8-490ced2ceafb；运行组 bd36b5d0-fbf5-39ce-bc0e-db847fd32547。
运行项目 b28093496c9aacd39306ca400a051e2854896fb690b124362230c3918e272a8a。

1. dbt run_results 实际报错：`column "project_snapshot_id" does not exist`。运行包仍包含作者模式的 `select 1 as _dts_dependency_placeholder where 1 = 0`。计划已核验的 REUSE 上游 dwd_test_prj 与作者模式代理 dts_ref_57bfaab58c4c 没有衔接。
2. Airflow dbt_build 失败后默认 ALL_SUCCESS 跳过 sync_manifest_and_probe；finalize 只收到 FAILED，产生 MODEL_DBT_AIRFLOW_UPSTREAM_FAILED。运行结果中的真实错误未入库；证据 API 也未提供已有 pipeline message；页面只显示状态/错误码。

## 整改范围

- 在候选运行包组装时，将精确匹配的系统上游占位节点绑定到已纳入不可变运行范围的 BUILD 节点或已验证的 REUSE 代理。保留图循环校验、内容校验和及上游版本校验；缺失绑定明确阻断。
- dbt 失败后仍采集结果；无运行时身份时跳过采集。按候选身份、运行调用 ID 校验后的逐模型错误，截断/清理后保存到现有 message 字段，不改数据库结构或手工数据。
- 当前/最新/历史证据 API 返回 failureMessage；页面显示具体原因、错误码、运行组 ID。
- 保留旧 Java 构造及仓储调用签名。无新增代码级测试；本次采用静态检查、正式构建和外部 Chrome 验收。

## 当前状态

源码已整改，待正式构建、部署及 Chrome 验收。原汇总模型未配置聚合，绑定修复后需在真实页面继续核实其业务配置，不能提前宣称物化成功。
