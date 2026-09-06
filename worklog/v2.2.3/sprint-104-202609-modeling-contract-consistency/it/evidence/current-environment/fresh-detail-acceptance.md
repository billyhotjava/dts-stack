# 新建无时间明细页面验收

环境：66fcd49dd正式镜像，2026-09-06。浏览器连接恢复后执行。

- 新模型：`e71715b7-ad1d-49b5-86e2-a91fa91e1b07`，S104 无时间明细复测，目标 `dwd_s104_detail_retry`。与旧草稿A分开保留。
- 两字段 project_id/remark，均STRING属性；无grain keys、无时间语义，FULL/table。来源s104_project_month，按名称映射。
- 正式页面保存成功，模型r2，操作流程显示保存/校验/提交均完成；字段显示名“项目编号/说明”保持正确。
- 选择测试环境创建并运行，17:07:36首次物化BUILT，attempt1；页面目标 `biadmin.public.dwd_s104_detail_retry`、关系已核验。
- 随后点击运行工程验证，报 `MODEL_SPEC_GOVERNANCE_QUALITY_START_FAILED`；候选进入QUALITY_RUNNING，质量证据ASSET_MISMATCH，规则版本/绑定/运行均空。
- 切回生成物化任务并选测试环境，“创建并运行”disabled，因此第二次物化未发起，不能标记重复物化通过。
- 旧模型A再次校验仍报 `MODEL_AUTHORING_UNMANAGED_FILE_CHANGED`，关联ID `8bddf19f-033b-4b45-abdc-957d52a7d6a8`；实际冻结源kind为FROZEN_SOURCE_BUNDLE，此前修复被该guard排除，正在补充真实生命周期条件。
- 当前Chrome下1366x768操作/错误区可见；390x844页面完成加载且主体可见。本记录不等于Chrome95或全页面布局验收通过。

## 质量规则补齐与发布

- 从页面配置质量规则，资产 `a73937d1-9982-3c9f-95ae-890490d2d86f`（S104 无时间明细复测）。规则 `d9abfde2-7576-4a32-b01f-2bebc41550ce`，名称“S104 复测项目编号完整性”，编码`s104_retry_project_not_null`，v1已发布并启用。
- 检测SQL：`SELECT * FROM public.dwd_s104_detail_retry WHERE project_id IS NULL`。
- 实际规则运行 `72d7bc72-259e-4ff5-8c33-cf0856cb7997`：3行通过、0行失败，100%，耗时115毫秒。工作流17:12:03–17:12:08通过1/1。
- 返回模型后候选自动收敛QUALITY_PASSED，工程验证/治理质量完成；规则版本`9f78a4ce-99db-4dfc-890d-6af0b474ebcb`，绑定`d39545e8-5995-495d-81de-da37e8bf2a07`。此前MISSING属于验收环境未绑定规则，不是不可恢复状态缺陷。
- 正式页面点击发布上线成功，候选PUBLISHED，目录同步中，运行计划DEPLOYING，test/MANUAL_ONLY；当前仅发布登记完成，不能宣称运行计划上线完成。
- QUALITY_PASSED下同候选“创建并运行”仍disabled，未发起第二物化。再次物化语义还需后续页面核验。

## 2161b2cda部署后复测

- 旧模型A：关闭此前残留发布窗口并完整刷新后，校验成功、提交实现成功；模型r4，字段显示名项目编号/说明均保留。旧占位schema归属阻断已通过页面复验。
- A再次物化入口显示历史CANCELLED候选v4（旧r3失败attempt1），当前模型r4，选测试环境后“创建并运行”disabled；未产生新运行，该分支仍待修复/复验。
- 新模型e717已发布后目录显示同步失败；运行状态待平台核验，test/MANUAL_ONLY/dts_release_build_postgres_primary。点击“立即运行并核验”显示“运行计划未能启动”，第二次执行未通过。
- 源码2161b2cda：SecurityTest27/27、正式双镜像构建、部署完成，后端healthy，其他容器无变化。

## 最后只读核验

- 目录：SYNC_FAILED / ANALYTICS_SEMANTIC_PUBLISH_HTTP_400。Analytics数据库注册表0行，语义发布请求dataSourceName=null，无法解析分析数据库，HTTP400。
- 再次运行：已生成第二个OPERATIONAL_RUN `5850295c-7516-3899-bd2c-aea5726f3e2f`，QUEUED；绑定ACTIVE/MANUAL_ONLY且未paused。对应Airflow DagRun不存在，dispatch UNKNOWN、已2次恢复，持久化错误MODEL_OPERATIONAL_DISPATCH_RECOVERY_FAILED。不是第二次物化成功；底层HTTP/传输异常未记录，不能断言具体上游原因。
- 上述读取没有做运行环境写入或强制重试。完整去敏证据见同目录 `model-e71715b7-first-materialization.json`。

## 5e00e914f 旧A关系核验误报复验

本轮实测原页面创建按钮可点击，纠正上文“取消候选入口disabled”的未充分核实结论。旧A的历史BLOCKED来自DAG未注册，物理观测为0；状态映射和历史标识已修复。通过现有页面创建当前r4新候选，17:38:30 BUILT/关系已核验，目标biadmin.public.dwd_s104_detail为TABLE、2列3行。详见[完整复验记录](blocked-relation-check.md)及[只读运行证据](blocked-relation-check.json)。
