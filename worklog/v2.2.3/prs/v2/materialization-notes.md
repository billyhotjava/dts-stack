# 花卉 v2 物化操作记录（2026-09-13）

## 当前结果

- 当前 ZIP 已通过 Chrome 导入，31 个模型，572 个字段密级绑定均为内部。
- 31 个模型全部 BUILT / VERIFIED：DWD 17、DWS 5、ADS 9；输出为 `biadmin.public.prs_v2_*`。
- Airflow 本次运行成功：`dts_rc_e029a70f0e2d4d0ea6c172c4ec5187df_v2_a1`，2026-09-13 20:06:26 至 20:06:46。dbt_build、来源准备、manifest 同步及关系核验、完成回写均成功。
- 发布尚未完成。点击“运行工程验证”返回 HTTP 409 / `MODEL_SPEC_GOVERNANCE_QUALITY_START_FAILED`，页面质量证据为新表治理资产 `ASSET_MISMATCH`；未发布分析数据集、未配置运行计划。
- 平台修复已编译并更新 `dts-platform:1.0.0`，未运行代码测试套件或回退演练。页面中的 dbt 构建与关系核验为本次实际物化流程；“运行工程验证”为发布入口必经动作，启动失败。

## 历史操作与阻断记录

从外部 Chrome 登录 xiezm，在花卉数据域筛选并勾选全部 31 个模型，选择开发环境、缺失上游一并构建，点击“创建并运行 31 个模型”。构建被前置校验拦截，尚未开始执行 SQL，未物化、未发布、未生成后续分析数据集。

## 已明确的阻断

1. 17 个 DWD 模型直接输出 `_dts_*` 字段。ModelSpecStageGateService 使用 `^[a-z][a-z0-9_]{0,62}$`，仅 SOURCE 模型放行保留技术字段。因此 DIMENSION/FACT 触发 MODEL_SPEC_FIELD_CODE_INVALID。
2. 已在 v2 源文件与 ZIP 将 DWD 输出改为 `dts_*`，ODS 读取保持 `_dts_*`；同步字段契约、维度属性映射并静态 parse 生成 manifest。通过 Chrome 上传更新包，预览为 31 可更新、0 阻断。
3. 实际提交更新失败：7 失败、24 下游阻断、0 更新。合并后的 proposedModelSpec.fields 是新 `dts_*`，standardBindings 却保留旧 `_dts_*`。17 个模型共 102 个密级绑定引用了已不存在的字段。ModelSpecContract 的更新校验拒绝这些绑定；预览只校验合并前对象，未检出合并后错误。页面将具体校验错误包装为 MODEL_IMPORT_CANDIDATE_FAILED。

需修复重新导入时字段重命名与治理绑定的合并及合并后校验，然后重新导入当前 ZIP，再继续物化。没有通过清空密级、创建重复模型或直接写数据库绕过。

## 当前交付状态

- 平台仍为之前成功导入的 31 个 r1 草稿，本次更新 0 个成功。
- 当前目录 ZIP 是字段命名已修正、尚未成功更新到平台的候选包。
- 未修改平台代码、未构建服务镜像、未做回退或额外业务测试。
- Chrome 停留在更新导入失败结果，详细结果见 materialization-result.json。
- 查看客户维度代码模式时点击过“开始编辑”，产生了未修改、未提交的代码草稿。

## 修复后进展

- 导入合并修复已部署：主分支提交 `9e0e8741c`，运行分支提交 `75706b8ff`。31 个模型全部更新成功，字段与内部密级绑定各 572 个，无失效绑定。
- 后续构建发现两处事实形态/时间语义不匹配。已通过 Chrome 修正月度结算为周期快照 + 统计周期，报花单头为累积快照 + 业务/申请/完成里程碑；两模型当前为 r3。模型 YAML、manifest 和 ZIP 同步更新。
- 页面修改设计后，加工版本仍指向 r2；预览将设计版本漂移误判为其他包占用。已修复为在同一归属、草稿状态和映射重新确认前提下同步加工版本，主分支 `bfde5e70c`，运行分支 `87d2939d4`，正在编译更新。
- 尝试页面提交加工配置时，创作草稿校验报 `DBT_DRAFT_DEPENDENCY_UNDECLARED`，对应原 dbt source uniqueId。未提交该草稿，保留原已导入 SQL；该路径独立记录，不通过清空依赖或直接写数据库绕过。

- 版本衔接修复已编译部署，重新导入 13 更新/18 跳过/0 失败。首次构建发布单已生成，但调度阻断 `MATERIALIZATION_SOURCE_MISSING`。来源固定逻辑将物理 schema/table 同时作为 dbt source/table，无法匹配包内 `prs_ods.原表名` 别名。
- 已在模型包将 22 处 SQL source 调用及 19 个来源声明对齐 `public.ods_prs原表名`，保持实际 ODS 表与业务 SQL 逻辑不变。当前包重新导入 17 更新/14 跳过/0 失败，572 个密级绑定保持内部。此为当前运行器契约适配；运行器支持任意 dbt 来源别名的能力未修改。
- 批量读取交付状态还出现连接池 10 个连接全部占用、等待 33、30 秒超时，已记录为独立问题；没有通过盲目扩大连接池绕过。

- 适配来源后发布单 `e029a70f-0e2d-4d0e-a6c1-72c4ec5187df` 已进入 BUILDING。Airflow 首次返回 DAG 404；实际 import_error 是缺少 `dts_runtime`。宿主 `/data/dts-stack/services/dts-airflow/extra` inode 为 11929211，而运行中 scheduler 挂载 inode 为 11927808，说明仍引用迁移前旧目录。目录标签路径相同不足以证明挂载内容已更新。确认无运行任务后，按原 Compose 重建 scheduler/webserver/triggerer，保留原镜像和数据。
