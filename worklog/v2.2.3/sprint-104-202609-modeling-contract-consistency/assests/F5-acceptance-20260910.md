# F5 编码、正式部署与外部 Chrome 验收

日期：2026-09-10。范围：Sprint-104 F5/T25–T31。使用授权测试账号，未手工修改数据库，未新增代码级测试。

## 实施与镜像

- 36655e29a：统一准入、批量读取、完整锁定、来源身份、字段目录及提交语义校验。
- 68d896eea：已提交输入/作者草稿恢复、显式版本选择、HashRouter 上游链接及布局。
- 749a58795：首次作者草稿允许从待完善设计建立；无初始依赖快照也执行完整验证。
- a3a8260e4：以待保存设计为根读取依赖图；首次创建后保存失败不覆盖输入。
- 4896d62f1：只读领域诊断不预先标记草稿写事务回滚；未处理失败仍由写入口阻断。

各轮源码均经 Git 提交推送，deploy 目录 ff pull 后使用 builds/dts-build.sh --image dts-platform dts-platform-webapp --no-save。必要 Maven 编译、TypeScript/兼容版打包执行；未把跳过代码测试写成测试通过。无迁移。镜像 tar/独立离线包未导出，本轮不宣称离线验收。

最终运行代码：4896d62f158ae2159dbcb68fb132e4e369c63e7b。platform 与 webapp 均使用同 SHA 正式镜像，platform healthy；镜像 ID、RootFS、JAR SHA256、打包类及正式构建日志指纹见 [构建部署证据](F5-build-deploy-4896d62f1.json)。

## 外部浏览器

桌面可见 Chrome 152.0.7977.82（CDP 9335）与 Chromium 95.0.4638.0（CDP 9336）。95 实测 UA 为 Chrome/95.0.4638.0，无 Headless 标志；MCP 的 Headless 会话未计入验收。两版本分开登录，95 的跨标签页流程使用同一会话，避免单用户登录互踢。

## 已有现场证据

- 原模型 项目进度汇总_v1：9b11c69d-0ee4-477a-a819-ac7ab3040639，r3/i4，implementationChecksum ccb2ec07eb13dc495127ec9c36319f844bbef7b099035fc558d1807c36dab89c。
- Chrome 152：错误 cost_amount 暂存成功，提交 422 MODEL_IMPLEMENTATION_SOURCE_FIELDS_INVALID，分别指出 fieldMappings.actual_cost_amount 和 settings.aggregations[2].sourceField；未发起物化。修正 src_0.actual_cost 后提交及物化成功。
- Chrome 95：原错误草稿重开仍保留 cost_amount；修正后再次提交 r3/i4 并物化完成，候选 45d172bd-610d-427c-a630-1d94c90f303f。已恢复原模型的有效编辑配置。
- 无实现 DWD 的设计可被 DWS 设计引用，实现候选明确显示未提交实现并禁选；有有效实现、已物化但仍 DRAFT 的候选可选。
- 95 上游链接正常打开正确 HashRouter 模型，1366/820 宽度候选行无挤压遮挡；已提交完整六字段恢复正常。
- 物理输入 ods_test_prj 返回 23 个字段、RESOLVED；实际中断字段目录请求显示失败，重试按钮恢复 23 个候选，原 task_snapshot_id 保留。
- GENERATED 切换为 SCHEMA_ONLY 后隐藏外部字段输入，明确说明仅建空表；未提交/执行生成模式，完整执行验收仍未覆盖，随后重新加载恢复原物理模式。
- 多来源先选 0909 后选排序更前的 0908，实际 inputs 顺序不变；移除原 src_0 后原映射标为失效，不转绑同名字段。
- 实际浏览器请求 20/200/201 输入窗口：20 与 200 返回 200、分别约 175/182ms；201 返回 400。样本包括 14 个真实模型及不可用 UUID，不能视为 200 个真实依赖闭包压测；查询语句数量尚无运行采集。

原始证据：F5-browser-36655e29a.json、F5-browser-followup-20260910.jsonl、F5-browser-checks-20260910.jsonl。截图见同目录 F5-IT34-* 与 F5-Chrome95-*。不保存密码、Cookie 或认证头。

## 未覆盖边界

T26/T30：真实 200 模型依赖闭包与实际 SQL 次数/并发快照预算。T27/T30：独立越权账号、实现撤回状态、完整跨方案/循环反例。T29：正常归档保护浏览器操作及并发残留的 CURRENT+ARCHIVED 预检分支。T31/T30：完整 GENERATED 执行、复杂 joins/歧义等矩阵。正常入口不可构造的状态不改库造样本，不标通过。

任务编码完成不等于全部验收通过。T25 为 DONE；其余保留 IN_PROGRESS，并以具体覆盖矩阵记录缺口。

## 设计/实现漂移的最终连续复验

Chrome 95、正式镜像 4896d62f1：

1. 独立 DWD 0b225a86-f2d7-4e5c-b97f-6ba780939aad 从无实现 r2 保存为 r3/i1；独立 DWS 77406c05-01e2-402f-8c8f-d8a93ebd3620 原设计仍引用 r2。页面明确选择 r3/i1 后，原失败作者草稿保存及提交成功，DWS 为 r3/i1。该修复正向链路先在 a3a8260e4 实证。
2. DWD 修改实现中的类型转换后，设计仍 r3、checksum 不变，实现由 i1 到 i2（checksum 1c574801a11659a2c5bb9e49c888b2cac7e35c8f5a4f87173ff40f0ec3ea4828）。DWS 显示“上游实现版本已变化”，保留 i1；取消更新仍保持 i1。
3. 旧 i1 下编辑类型转换并暂存，最终镜像返回 200，快照保留 i1、casts 和聚合字段。提交 validate 返回 409 MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE，issues[0].reason=IMPLEMENTATION_REVISION_DRIFT，fieldPath=inputs[0]，关联 ID 89c8d6f1-6f1d-4a16-9fec-e840a5a0f138；未提交新实现。
4. 确认更新 i2 后保存、validate、commit 成功：DWS r3/i2，implementationChecksum c1248089cc7cf8fffec0f564c6b0642f89ecfe5f2da352e527b7fa2556288161。字段/聚合编辑保留，无隐式重钉。随后物化计划对原0909上游 REUSE，对独立 DWD 与 DWS BUILD，已完成并经刷新确认，见下面最终物化证据。

独立上游正常删除入口返回 409 MODEL_SPEC_DELETE_REFERENCED，模型保留；这是删除保护证据，不能替代归档预检分支。Chrome 95 浏览器中途关闭导致会话失效，session/status 明确 authenticated=false 后按正常账号登录恢复；未修改权限。

## 最终运行结果与任务覆盖

独立汇总 r3/i2 在最终镜像物化成功并刷新保持完成：candidateId `ab8da637-c573-4603-8180-56c4d6acd89d`，runStatus=BUILT，relationState=VERIFIED，targetRelation=biadmin.public.dws_s104_f5_input_version，pipelineRunGroupId=`eb2a265c-e9d0-353d-bc07-240b65717f95`，dbtInvocationId=`5cbf991d-a2c4-4b2a-b50e-47df026ff086`。完成时间 2026-09-10 00:31:00 +08:00。截图 F5-Chrome95-materialized-4896d62f1.png。

最终 Chrome 95 批量复核：20 输入 200/176ms，200 输入 200/184ms，201 输入 400 MODEL_IMPLEMENTATION_INPUT_WINDOW_INVALID。数据为已知 ID 加不可用 UUID，只验证窗口、脱敏和响应边界，不能据此验收 200 个真实模型闭包及 SQL 次数。原始响应 F5-Chrome95-batch-4896d62f1.json。

| 用例 | 本轮状态 | 实证与未覆盖边界 |
|---|---|---|
| IT-29 | 已测主线 PASS | 无实现设计可引用/实现不可选，有实现 DRAFT、已物化 DRAFT 可用；独立 DWD/DWS 连续操作 |
| IT-30 | 已测主线 PASS | r2→r3 引用更新后保存/提交；r3/i1→r3/i2 真实升级、返回刷新、明确更新、三级物化完成 |
| IT-31 | PARTIAL | 实现漂移结构化 409、字段多错误 422、旧 pin 暂存保留；越权账号/跨方案/循环/撤回完整反例未覆盖 |
| IT-32 | PARTIAL | 完整六字段保存/恢复/更新通过；删除引用保护 409；归档预检及历史分支未覆盖 |
| IT-33 | PARTIAL | 页面刷新/字段读取失败重试/上游读取失败保留、20/200/201 窗口通过；实际 SQL 次数、真实 200 模型闭包及并发预算未覆盖 |
| IT-34 | PASS | cost_amount 提前阻断，修正 src_0.actual_cost 后原模型物化；最终版本依赖反例及成功物化已复验 |
| IT-35 | PARTIAL | 来源顺序/删除不误绑、错误草稿恢复、物理源 23 字段及故障恢复通过；GENERATED 仅 UI 边界，完整生成执行/复杂关联矩阵未覆盖 |

F5 全部任务描述和编码已落实并进入正式镜像。T25 DONE；T26–T31 保持 IN_PROGRESS，原因是上表验收缺口，不能将编码完成写成全部验收完成。F1–F4 的历史未完成任务不在本轮 F5 实施范围内。

最终 Chrome 152 页面回归：正常重新登录后，原汇总模型 actual_cost_amount 的来源映射与聚合来源均恢复为 src_0.actual_cost，完整引用显示 r3/i1；进入物化步骤显示建模已完成。截图 F5-Chrome152-original-completed-4896d62f1.png，UA 和实际字段值保存在 F5-browser-checks-20260910.jsonl。此为最终版本只读回归，不虚称再次执行物化。
