# F13 发布质量处理与失败恢复设计

状态：DRAFT；修订日期：2026-09-24；本轮源码核对基线：`576aba566`。与[F13/F14联合设计](F13-F14-联合设计与实施顺序.md)的A01–A05一起阅读。本文替代原草案，尚未编码。

## 1. 目标、事实与待证问题

仅处理构建结果核验完成后的质量阶段。保留发布状态及通过时冻结质量结论的规则，新增可恢复的检查状态和用户操作；不把治理质量失败写成构建失败。

### 发布质量策略（本轮明确）

复用ModelGovernancePolicyPort与PLATFORM_DEFAULT的ADVISORY/BLOCKING，不新建策略引擎，不把本轮设计解释为已修改现场配置。两模式均要求工程核验、有效资产登记及现有权限/密级/来源版本检查通过。

| 实际业务质量状态 | ADVISORY：仅提示 | BLOCKING：必须通过 |
|---|---|---|
| 必要规则均通过且证据有效 | 允许形成发布检查通过结果 | 允许形成发布检查通过结果 |
| 未配置规则/未检测 | 提示“未检测”，允许；不强制补规则 | 阻止，指向规则配置/执行入口 |
| 正在运行 | 提示真实runId，允许；不等待质量任务结束 | 等待真实结果，有界超时诊断 |
| 不合格/过期 | 保留不合格/过期提示，允许 | 阻止，按绑定和权限提供处理动作 |
| 质量服务超时或不可读 | 有界读取失败后显示“质量状态未确认”，允许 | 阻止，平台故障单独诊断 |
| 策略不可读、构建证据损坏、资产身份冲突、权限/密级拒绝 | 拒绝，不能当业务质量告警吞掉 | 拒绝 |

“允许”仅指质量条件不阻止，不自动执行发布或赋予下游访问权。ADVISORY不得自动启动质量SQL，也不得因无规则一直停在QUALITY_RUNNING；只在有合法工程与登记结果后完成既有发布前检查。界面分别显示实际质量状态与发布检查结论；QUALITY_PASSED兼容表示发布前检查通过，不能伪造质量PASSED。

本期保留冻结语义，策略取值指纹随本轮检查结果保存。冻结前的策略/规则绑定变化必须使旧结果失效；冻结后按已确认快照发布，适用策略明确可见，不把后续平台策略变化追溯套用到旧快照。独立权限和来源有效性仍按发布时校验。旧快照缺少必要身份/策略证明时明确拒绝并走已有重新检查或替代候选流程，不猜测默认值。

历史观测表明本机一张单在候选v3→v4后持续登记失败，但尚无真实异常证明版本变化是原因。现有登记唯一键含`evidence_ref`，允许不同证据引用并存。F13-T01必须取得异常链并以真实PostgreSQL复现；不能预先更改台账唯一性或删除历史记录。

## 2. 检查阶段与完成条件

| phase | 含义 | 推进/恢复条件 |
|---|---|---|
| ENGINEERING_PENDING | 构建/工程验证证据暂未齐全 | 从F14当前批次核对；超出等待阈值进入SYSTEM_BLOCKED并保留已确认成功阶段 |
| GOVERNANCE_SUBMITTING | 用户已发起重跑，F8受理结果尚未确认 | 按幂等受理记录恢复runId；有界超时转明确故障，不假称任务已运行 |
| GOVERNANCE_WAITING | 工程验证通过，BLOCKING下治理质量有真实在途运行 | 按本轮runId查状态；ADVISORY不进入业务质量等待 |
| ACTION_REQUIRED | BLOCKING下缺规则/不合格/过期，或两种模式的密级/授权待处理 | 对应用户操作完成且当前版本/权限仍有效后重新检查 |
| SYSTEM_BLOCKED | 登记、必要工程/策略读取、回写故障，或BLOCKING下质量服务故障 | 故障恢复后重试；不自动绕过任何业务检查 |
| RESOLVED | 检查轮次结束、被新轮次替代或候选已离开该阶段 | 记录CHECK_ACCEPTED/QUALITY_FAILED/SUPERSEDED/STALE/BUILDING等实际去向；CHECK_ACCEPTED不代表业务质量通过 |

`QUALITY_PASSED`只能在工程核验和资产登记通过、已解析的质量策略允许时提交并冻结快照；BLOCKING要求业务质量通过，ADVISORY保留告警。工程验证单独显示真实结论，不能为了页面显示完成提前迁移候选。候选被重建、替代或终止时旧检查行停止；旧轮次或版本不匹配的worker不能提交候选迁移或覆盖新行。

## 3. 错误分类与动作

`classify(code, context)`返回类别、责任和候选动作；context包含阶段、规则/运行标识、当前版本、异常类型、当前权限。再由既有权限服务过滤`allowedActions`，服务端执行时重新检查。一个码可对应不同合法动作。

分类与是否阻止发布分别计算：下表治理业务码在BLOCKING下形成阻断，在ADVISORY下仅形成warning；策略不可用、资产/工程与安全错误始终是阻断。输出含blocking与warnings，不以category=SYSTEM一概决定是否放行。

| 码/条件 | 类别 | 默认责任 | 动作条件 |
|---|---|---|---|
| ASSET_REGISTRATION_FAILED | SYSTEM | 平台运维 | 查看关联ID；须保留脱敏原始异常，不认定一定是幂等问题 |
| QUALITY_EVIDENCE_UNAVAILABLE / QUALITY_POLICY_UNAVAILABLE | SYSTEM | 平台运维 | 服务恢复后重新检查 |
| MODEL_RELEASE_QUALITY_EVIDENCE_MISSING | SYSTEM | 平台运维 | 查询F14执行事实，禁止自动重复SQL |
| GOVERNANCE_QUALITY_MISSING | USER_ACTION | 数据管理员 | 无有效绑定→配置规则；有有效绑定且可执行→运行治理质量 |
| GOVERNANCE_QUALITY_FAILED / EXPIRED | USER_ACTION | 数据管理员 | 查看运行/重新运行；绑定版本失效时先配置规则 |
| GOVERNANCE_CLASSIFICATION_REQUIRED | USER_ACTION | 数据管理员 | 按现有资产入口补充密级 |
| MODELING_EXECUTION_INITIATOR_MISSING / MODEL_EXECUTION_AUTHORIZATION_REVOKED / MODEL_OPERATION_SCOPE_DENIED | 按真实码核对 | 模型维护者/权限管理员 | 发起人停用或撤权不再自动执行；恢复合法授权或通过已有流程新发起，刷新人不能代替原发起人 |
| GOVERNANCE_QUALITY_RUNNING | WAITING | 无 | 显示真实runId、起始时间；无重跑按钮 |
| MODEL_RELEASE_CANDIDATE_STALE | USER_ACTION | 模型维护者 | 原检查行RESOLVED，按已有流程创建替代发布单 |
| 未登记码/读取无法确定 | SYSTEM | 平台运维 | 明示状态尚未确认；登记诊断后再扩充目录 |

表内省略前缀的码是分组说明，T01须从产生位置登记完整真实码；不得把它们当新增错误码直接实现。公开响应只含可见对象标识与业务提示；`causeMessage`脱敏后截断512字符，仅运维职责可见，不原样输出SQL、凭据或远程响应。

## 4. 数据与并发设计（新增表为方案，M0冻结DDL）

复用候选和资产表；计划新增`modeling_release_quality_reconcile_state`，不新增业务事实台账。主键`(tenant_id,candidate_id,quality_round_id)`，外键引用既有候选；candidate_version保留为开始/提交时的并发校验值，不能充当轮次身份。当前候选最多一个活动检查轮次，以唯一约束和同一候选锁保证，DDL类型在M0对照真实表确定。

| 字段 | 类型/约束 | 用途 |
|---|---|---|
| phase/blocker_code/category/owner_role/detail_json | varchar + jsonb；枚举约束 | 当前检查结果与脱敏诊断 |
| quality_round_id/build_result_ref/policy_fingerprint | uuid/既有证据引用/稳定摘要 | 本轮身份、当前构建事实及有效策略值；不复制SQL或运行文件 |
| selected_evidence_refs | 有界结构化引用集合 | 完整预期资产、规则/绑定版本与本轮runId；不能只取“最后返回的结果” |
| first_seen_at/last_seen_at/phase_started_at | timestamptz；时间单调 | 当前原因持续时间；阶段超时不能因换码被无限重置 |
| attempt_count/next_attempt_at | 正整数/timestamptz | 退避与到期查询 |
| state_revision | bigint单调递增 | 旧结果不能覆盖新结果 |
| wake_generation/handled_generation | bigint非负，handled≤wake | 事件唤醒不丢失，不能仅覆盖next_attempt_at |
| claim_token/claim_until | uuid/timestamptz，可空 | 定时与手动请求并发只领取一次；过期可恢复 |
| last_warn_at/last_summary_at/last_escalated_at | timestamptz可空 | 重启/并发后仍可控制日志频率 |
| resolved_at/resolution | timestamptz/varchar可空 | 历史去向，不伪造完成 |

到期索引覆盖未结束行的next_attempt_at；公平排序含候选ID，不能只取最老20张导致饥饿。读取绑定当前有效构建与当前检查轮次；候选正常迁移后的冻结结果按命令记录读取。旧轮次/失效版本行结束，指标不统计历史行。

执行：短事务领取并记录qualityRoundId、state_revision与wake_generation→事务外访问依赖→短事务重新核对当前轮次、候选版本、构建引用、策略/绑定/runId选择、claim_token及revision后提交。旧轮次不仅不能写诊断，也不能调用候选通过命令。合法候选迁移、冻结快照及审计由既有命令事务提交；诊断行可随后修复。领取失效丢弃旧结果，不撤销已合法发生的外部操作。唤醒递增generation；完成时有新generation则next_attempt_at不得被退避覆盖。无状态行时wake幂等创建；同一检查重复wake不新建轮次。

用户提交质量重跑时，先在同一候选事务内预留新qualityRoundId与幂等请求、将旧轮次标为SUPERSEDED并切换当前引用，再通过F8幂等入口发起任务，防止外部请求期间旧worker提交通过。F8受理后按回执绑定本轮runId；响应丢失按原键查询/重放受理，不重复质量SQL。尚无runId时为GOVERNANCE_SUBMITTING，不假称运行中；BLOCKING不得通过，ADVISORY仍按提示策略处理且保留可恢复的重跑请求事实。重跑请求与回执由既有重跑服务持久维护和补偿，不依赖诊断轮次是否RESOLVED；质量检查结束或发布不能丢弃用户已受理的运行请求。事件只唤醒，实际结果从F8按本轮引用读取。多资产结果须逐项齐全；集合缺项/混入旧绑定在BLOCKING下不能通过。

外部动作自身使用原有业务幂等键；主键upsert不等于副作用幂等。状态写入失败不得把业务迁移判成失败：保留真实迁移结果，日志告警，下一轮从候选事实修复；候选已迁出时也须清理旧行。

## 5. 调度、等待与告警

计划预算（M0结合现场运行时长核对，测试可缩短时钟）：WAITING 5秒指数退避至30秒；USER_ACTION/SYSTEM 30秒至5分钟。唤醒优先于退避；单次检查依赖调用必须有上限，领取租期覆盖该上限，超期worker无写回资格。

工程证据在构建已终态后等待超过5分钟、治理运行超过其任务配置超时且无法确认进展时，转为明确的SYSTEM_BLOCKED原因；这只是诊断状态，不直接改写SQL/质量业务结果。无真实运行记录不能一直显示“正在运行”。SYSTEM持续30分钟升级日志；变化WARN一次，同码每小时最多一条摘要，去重跨重启有效。阈值是拟定配置，不是现行SLA。

质量运行完成事件、规则关联变化、用户立即检查都会wake；每5分钟至少有一次扫描兜底，事件丢失不得永久卡住。用户提交质量重跑成功不等于质量检查通过。

## 6. API与页面

- 扩展既有`WorkbenchView.qualityReconcile`：candidateVersion、qualityRoundId、phase、effectivePolicy、policyFingerprint、actualQualityState、publishCheckAllowed、warnings、blocker、allowedActions、firstSeenAt、lastSeenAt、nextAttemptAt、stateRevision。blocker包含code/category/ownerRole/message/actionHint/assetKeys/correlationId。发布命令独立复核，不信任前端的publishCheckAllowed。
- `GET workspace`/单项GET只读，不登记资产、不执行远程质量检查；读取失败通过显式诊断表示未知，不能返回null让前端误以为正常运行。字段缺失兼容旧后端，但不能默认成通过。
- 保持`POST /{candidateId}/refresh`原有使旧发布单失效的语义、请求头和CommandResult响应。
- 计划新增`POST /api/modeling/plans/{planId}/release-candidates/{candidateId}/quality-reconcile`作为立即检查命令：If-Match + Idempotency-Key，body={reason}；检查权限与当前状态后仅持久化wake，返回202及`{candidateId,candidateVersion,requestId,acceptedAt}`，不在HTTP请求中执行长检查。精确DTO与旧ApiResponse封装由T01冻结。
- 页面“刷新状态”调用现有GET；“重新检查”调用上述命令后轮询GET。失败恢复按钮只使用服务端allowedActions，不凭字符串推测权限。不新增页面或菜单。
- 无规则/过期等显示处理入口；工程通过独立勾选；系统故障显示“请联系平台管理员”和关联ID。只有真实通知回执存在才显示“已通知管理员”。

## 7. 登记、身份与F14衔接

同一构建产物重复登记必须不重复建立资产；不同真实构建的历史证据允许保留，不能要求同一资产同通道永久只有一行。物理证据识别至少包含目标定位、pipelineRunId和metadataChecksum；确定性标识策略在真实复现后冻结，候选版本不作为单独去重依据。

登记只消费F14已提交的完成证据引用；不从SQL成功日志或Airflow总状态推断完成。失败只恢复登记/质量检查，不重跑SQL，ADVISORY也不跳过资产身份登记。真实资源ID冲突、定位歧义、来源变化和密级拒绝必须保留。原发起人身份从持久命令读取，每次重试检查当前权限；读取、状态回写和审计一致，正常/异常均关闭身份。不得为消除故障改成管理员或无条件系统身份。

## 8. 待冻结事项与验证

T01：登记真实根因与最小复现、实际错误码清单、身份拒绝处置、立即检查DTO与权限、规则/运行深链、表字段/索引/清理策略、等待预算；与F14-T01共同完成M0。DDL编号暂用`20260924_01_release_quality_reconcile_state.xml`，实施前检查冲突。

验证见[F13验收](../it/F13-发布质量对账验收.md)。保留新增表/字段向前兼容；不手工更改存量任务。F13尚未编码，新增API与表均不是当前已提供能力。
