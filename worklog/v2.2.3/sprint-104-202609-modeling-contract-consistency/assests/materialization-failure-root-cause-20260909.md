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

本问题已完成正式构建、部署与外部 Chrome 验收。代码提交 `fa920a05ba1086072fd61bcd9cc873d3fe80f530`。

1. 保持原业务配置重试：运行组 `fed69cf3-f449-37b5-9595-837b2de34c0a`。不再报 project_snapshot_id 缺失，具体报错变为 `column "task_total" does not exist`；页面醒目区域和逐表原因列均显示完整错误、MODEL_DBT_BUILD_RESULT_FAILED 与运行组 ID。证明真实上游已绑定，失败采集/存储/API/页面链路生效。
2. 通过 Chrome 正常填写现有可视化配置：project_snapshot_id、snapshot_date、project_code、project_name 映射 src_0 同名字段并分组；task_total=COUNT(src_0.task_snapshot_id)、avg_progress_pct=AVG(src_0.progress_pct)、actual_cost_amount=SUM(src_0.actual_cost)。这与该模型已有业务定义一致。保存、校验、提交均成功，实现版本变为 r2，模型设计仍为 r3。
3. Chrome 点击开始物化，正常创建新候选 `f2a8d124-9b15-4e79-af94-4ac04fb12ff3`，运行组 `ff0d660e-903b-3087-b4db-ac554d946960`。候选/逐表运行均为 BUILT，relationState=VERIFIED，failureMessage=null；目标 `biadmin.public.dws_prjtest_090901`。页面显示“已完成 / 建模已完成”，刷新后保持。

## 交付与验证边界

- 后端正式 Maven 编译/测试源码编译/打包通过，测试执行按用户要求跳过；前端 tsc + LEGACY_BROWSER_BUILD 正式构建通过。Python AST 静态解析和 git diff --check 通过。Biome 报告该页面已有整体格式差异，没有进行整文件格式化。
- GitNexus 已做影响分析和提交前 detect_changes，已索引调用链风险 LOW；Python 任务未索引，直接核对 ALL_DONE 和 prepare/build/sync/finalize 依赖。
- 正式包及镜像：见 `materialization-package-proof-20260909.json`；镜像 OCI 配置/RootFS、归档哈希、源提交及包内 Airflow 脚本均已校验。运行中平台容器 healthy；脚本 SHA256 与包内一致。
- 本次验收是真实 Chrome 的 REUSE 上游场景；不是 Chrome 95 实机验收。BUILD 上游共用同一组装逻辑但未增加额外场景；未扩展代码级测试。
- 无数据库结构修改或人工数据修补。配置保存、失败记录、建表及正常物化写入均由产品既有流程执行。
- 本次关闭的是物化失败与原因缺失问题，不代表质量检查、资产发布或全部 Sprint task 已验收。

## 证据

- `materialization-before-fix-20260909.json` / `.png`：原失败。
- `materialization-error-visible-20260909.png`：具体失败原因显示。
- `materialization-acceptance-20260909.json`：错误回传、作者草稿、提交、BUILT/VERIFIED 结果。
- `materialization-accepted-20260909.png`：刷新后的完成页面。
