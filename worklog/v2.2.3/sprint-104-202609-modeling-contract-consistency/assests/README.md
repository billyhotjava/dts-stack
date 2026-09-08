# S10DC 开放 Bug 处理台账

用户指定目录名为 `assests`。本目录集中保留不能直接解决的解释、待决策项以及修复验收证据；不保存账号密码。

本次用 Chrome 的谢志民（xiezm）登录会话读取 Jira，查询 `project = S10DC AND resolution = Unresolved ORDER BY priority DESC, updated DESC`，共 35 项，当前均为开放、缺陷。逐项读取标题、描述、附件列表和已加载评论；附件中 S10DC-70、52 已查看大图。Jira 日期存在时区显示差异，以问题编号和原附件文件名定位。

## 当前修复范围

- S10DC-50：定位每次读取菜单触发种子同步，已有未绑定菜单被父/兄弟角色重新授权；修复已有菜单的空授权被覆盖。历史已产生的多余授权不自动清除，须核对原授权意图。
- S10DC-68：固定默认工作台地址和静态路由未检查菜单；增加菜单加载完成状态，按已授权菜单选择首页，没有菜单时明确提示。
- S10DC-53：已保存的个性化偏好可把服务端禁用组件重新显示；尊重禁用状态和服务端空组件清单。各岗位组件授权范围仍须明确。

本段是源码处理范围，测试、镜像、页面结果将在验收记录中单独填写。未满足原场景验收的 Jira 不标为解决。

## 全量问题及下一步

| Jira | 说明和处理边界 | 需要的资料、决定或验收 |
| --- | --- | --- |
| [65](https://jira.yuzhicloud.com/browse/S10DC-65) | 数据治理与资产目录拆分，描述只有标题；属于菜单方案调整 | 与 55、67 合并确认最终菜单树、旧入口兼容和角色绑定继承规则 |
| [63](https://jira.yuzhicloud.com/browse/S10DC-63) | 集市与卡片权限，无角色/对象/操作矩阵 | 明确谁可查看、编辑、发布、授权，个人授权和部门权限如何叠加 |
| [62](https://jira.yuzhicloud.com/browse/S10DC-62) | 只有“数据建模报错”，无附件、错误码或步骤 | 需模型名称、步骤、报错时间/截图；不能认定与 70 或 F4 是同一错误 |
| [61](https://jira.yuzhicloud.com/browse/S10DC-61) | 建模数据权限，描述只有标题 | 明确模型设计权限与表/行/列读取权限边界，跨域及密级规则 |
| [60](https://jira.yuzhicloud.com/browse/S10DC-60) | 希望接入时设置业务域、分层 | 明确接入阶段必填还是可选，采集继承规则及已有未归域资产处理方式 |
| [59](https://jira.yuzhicloud.com/browse/S10DC-59) | 接入完成后还需采集元数据 | 数据接入和目录登记是两个过程；需确认是否自动触发采集，以及失败重试和目录可见时机 |
| [57](https://jira.yuzhicloud.com/browse/S10DC-57) | 按人授权入湖 | 需授权主体、对象范围、有效期、审批与角色授权优先级 |
| [56](https://jira.yuzhicloud.com/browse/S10DC-56) | 取消字段密级控制、只允许向下的表述不完整 | 需确认表/文件/字段密级继承规则及“向下”的含义；不擅自放宽现有密级校验 |
| [55](https://jira.yuzhicloud.com/browse/S10DC-55) | 大屏看板升为一级菜单 | 与 65、67 合并决定菜单树；避免种子、迁移、角色默认、静态路由相互覆盖 |
| [54](https://jira.yuzhicloud.com/browse/S10DC-54) | 领导与业务用户待办区分，缺少具体待办规则 | 需各岗位可见事项、负责范围和办理动作；现有 useWorkbenchRole 已区分员工/部门/机构领导，不能据此宣称满足需求 |
| [53](https://jira.yuzhicloud.com/browse/S10DC-53) | 个性化恢复禁用组件的确定性缺陷本轮修复 | 还需完整岗位组件矩阵和原账号验收；本地回退组件清单不等于业务授权 |
| [67](https://jira.yuzhicloud.com/browse/S10DC-67) | 主子菜单和操作顺序合理性，属于总体设计 | 提供最终认可的业务操作顺序，与 55、65 一次收敛 |
| [70](https://jira.yuzhicloud.com/browse/S10DC-70) | 附件：项目任务快照明细_v3 r3；MODEL_LIFECYCLE_GATE_BLOCKED，FACT 缺少稳定业务过程引用 | 当前源码已有中文指引、业务过程绑定和数据域修复；须为该模型选择并保存真实业务过程，再验证物化，不能绕过门禁 |
| [69](https://jira.yuzhicloud.com/browse/S10DC-69) | 来源字段当前是文本框；希望真实字段下拉和同名映射 | 与 49 合并；来源 binding 视图不含字段，需按来源类型/版本读取受权限保护的字段元数据，不能用目标字段冒充来源字段 |
| [68](https://jira.yuzhicloud.com/browse/S10DC-68) | 无工作台菜单仍跳转工作台，本轮修复 | 验收：仅 BI 菜单账号不挂载工作台；空菜单给出提示；授权账号保持可用 |
| [52](https://jira.yuzhicloud.com/browse/S10DC-52) | 附件显示保存时没有可写建模上下文 | 当前 saveModelDraft 会尝试 resolveDefaultModelingContextId；仍需原失败草稿与可写规划记录核对，不为消除报错伪造规划 |
| [51](https://jira.yuzhicloud.com/browse/S10DC-51) | ModelFieldEditorTable 中导入明确被禁用；关联依赖 canAssociate | 需补齐受控来源字段选择/导入契约，并保留字段类型、密级和来源版本；按钮置为可用不能算修复 |
| [50](https://jira.yuzhicloud.com/browse/S10DC-50) | 已有未绑定子菜单被种子同步再次授权，本轮修复 | 需原角色与应授权菜单清单核对历史污染；不能按当前多余授权猜测原意图 |
| [49](https://jira.yuzhicloud.com/browse/S10DC-49) | 自动映射当前按目标名称直接生成，未验证真实来源字段 | 与 69 合并；同名多来源时应由用户选择，缺失字段不应生成假映射 |
| [48](https://jira.yuzhicloud.com/browse/S10DC-48) | 已有归档/删除错误指引修复 | 有引用/物化证据的模型不能强删；须原对象复测提示与可执行操作 |
| [47](https://jira.yuzhicloud.com/browse/S10DC-47) | 已有空白字段过滤和重复点击回归代码 | 待当前页面验证原空行场景；不重新重复实现 |
| [31](https://jira.yuzhicloud.com/browse/S10DC-31) | 缺敏感数据自动识别，人工标注；属于能力补齐 | 需识别对象、规则、误报复核、扫描范围及结果是否自动生效；不凭字段名直接降低密级 |
| [32](https://jira.yuzhicloud.com/browse/S10DC-32) | 工作流与资产集成端到端验收 | 历史 PASSED 不能替代本次；需指定验收资产/规则及可执行检查，关联本次运行结果 |
| [33](https://jira.yuzhicloud.com/browse/S10DC-33) | 旧大屏导入密级保存失败；已有数据库标识预检修复 | 仍需原始导入文件、旧版本和密级/数据源映射；不能绕过后台校验 |
| [36](https://jira.yuzhicloud.com/browse/S10DC-36) | 未归域跳数据查询，已有 _UNASSIGNED_ 参数转换修复 | 待登录页面点击核对 domainUnassigned 查询 |
| [40](https://jira.yuzhicloud.com/browse/S10DC-40) | 资产详情“密级与生命周期”，无文字步骤 | 需对照附件、原资产和账号复现；不能从标题推断期望行为 |
| [41](https://jira.yuzhicloud.com/browse/S10DC-41) | 历史发布 BUILD_FAILED | 须当前目标模型运行、构建日志与实表结果；PUBLISHED 历史状态不代表本次成功 |
| [42](https://jira.yuzhicloud.com/browse/S10DC-42) | 数据类型、数据域下拉已有实现 | 待当前标准代码编辑器页面验收 |
| [43](https://jira.yuzhicloud.com/browse/S10DC-43) | 治理中心七个入口已有路由修复 | 待当前角色逐个点击，不以路由存在代替授权和页面验收 |
| [44](https://jira.yuzhicloud.com/browse/S10DC-44) | 字段标准来自 /modeling/metadata-standards，非标准代码 | 需原账号真实接口和下拉结果；库中有记录不等于有权限读到 |
| [45](https://jira.yuzhicloud.com/browse/S10DC-45) | 标准代码已有草稿/已发布/已废弃选择 | 待真实保存、重开确认状态；不把归档当发布 |
| [46](https://jira.yuzhicloud.com/browse/S10DC-46) | 来源绑定失效已有中文指引与版本校验 | 需原失败草稿重新确认来源后保存；不放宽绑定版本检查 |
| [66](https://jira.yuzhicloud.com/browse/S10DC-66) | 现场旧版升级保留数据、看板 | 必须有源版本、数据库/附件/配置完整备份与恢复演练、映射清单；现场资料未提供，不能保证无损迁移或直接覆盖数据库 |
| [64](https://jira.yuzhicloud.com/browse/S10DC-64) | 选择数据源表显示别名，只有概述 | 需明确哪个选择器和别名来源（表注释、资产名称或人工别名）；物理标识仍保持稳定 |
| [58](https://jira.yuzhicloud.com/browse/S10DC-58) | 大屏联动管理希望右侧内嵌预览 | 需定位当前联动入口并核对预览能力和跨应用会话；保留编辑/发布动作与仅预览的区别 |

## 集中待决策

1. 菜单方案：55/65/67 的最终菜单树及迁移规则。
2. 权限方案：53/54/57/61/63 的角色/人员/对象/操作矩阵；50 的原始授权清单。
3. 密级规则：56 的准确继承方向与可修改范围。
4. 自动化范围：31 的识别审批、59/60 的接入与目录登记时机。
5. 原场景资料：62 的报错步骤，33/66 的原始导入与现场版本/备份，建模失败对象与可写规划/业务过程。
