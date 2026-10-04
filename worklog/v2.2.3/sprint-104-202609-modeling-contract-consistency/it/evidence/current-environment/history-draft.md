# 历史草稿真实页面走查

环境：正式部署 bd0670acc，Chrome 当前会话，2026-09-06。
模型：`5c188a43-5cba-4d33-8ac6-a24421e20dbd`，S104 历史配置恢复。独立业务维度 `d701519f-de04-42db-bba5-f4542f8c148b` 已在页面确认定义。

1. 新建六字段：project_id(STRING KEY 非空)、valid_from(DATE)、valid_to(DATE)、is_current(BOOLEAN)、region(STRING)、city(STRING)。
2. 配置 TYPE2，开始/结束/当前绑定为 valid_from/valid_to/is_current，层级 AREA：region→city。
3. 保存并重载页面，上述全部配置完整恢复。该模型仅验证草稿，不宣称来源包含所有历史字段或可执行历史维护。
4. 仅修改开始/结束绑定及层级名称，表单值发生变化，但“保存草稿”仍 disabled：实际失败，已安排修复完整 profile 的修改检测。
5. 为独立验证后端持久化，再修改描述触发已有修改检测并暂存，页面成功进入“下一步：校验”。这属于诊断，不将绕过禁用按钮后的保存算作第4项通过。

6. TYPE2→NONE 保存并刷新后，历史策略仍为 NONE，三个绑定输入不再出现；AREA 层级及 region→city 完整保留。
7. 同时打开两个页面：主页描述改为“独立历史配置恢复验收：版本冲突获胜版本。”并保存成功；旧页改为“独立历史配置恢复验收：旧页面不应覆盖。”后保存，明确返回 `DBT_DRAFT_ETAG_CONFLICT`（关联 ID `62a04836-1c45-4881-8012-1ae4ef5e8168`）。刷新主页，仍为获胜版本描述，未被旧页覆写。

最终两轮纯历史配置修改仍待修复版部署后补证；NONE 与并发版本冲突页面分支已通过。

## 修复版两轮恢复（3fa21140e，正式容器）

- 第一轮：NONE切换TYPE2，绑定 valid_from/valid_to/is_current，层级名称“区域城市（修复后第一轮）”，AREA region→city。仅变更历史配置后保存成功，刷新全部恢复。
- 第二轮：只修改层级名称为“区域城市（修复后第二轮）”，层级顺序 city→region，保存并刷新全部恢复；TYPE2及三个绑定保持。两轮描述均为此前“版本冲突获胜版本”，没有借描述变更触发保存。
- 草稿恢复、NONE清理、并发冲突分支通过；本样例没有完整历史来源字段，不将草稿结果作为历史引擎或提交执行通过证据。

- 随后页面校验和提交实现均成功；刷新后仍为TYPE2、valid_from/valid_to/is_current、AREA及第二轮city→region。
- 测试环境发起普通FULL构建，候选先BUILDING后BUILD_FAILED，页面报告 MODEL_DBT_AIRFLOW_UPSTREAM_FAILED。该动作不是SNAPSHOT或历史维护，不能用它证明历史执行准入拒绝；底层运行原因另行追查。

- 底层确认：16:44:58 Airflow GET DAG=200、POST dagRun=200，prepare_runtime成功；16:45:09 dbt_build报 `column "valid_from" does not exist`。原因是该草稿恢复样例的来源没有历史字段且实现映射为空；不是DAG注册问题，也不是历史引擎执行证据。
