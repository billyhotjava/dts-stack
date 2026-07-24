# Sprint-67 IT 与交付证据计划

**状态**：IN_PROGRESS（23/34 Task 已关闭；F2-T05、F3-T02/T06/T07/T08、F6-T05/T06 为 IN_PROGRESS，F3-T09/T10、F6-T07/T08 为 READY）

## 0. 人工验收入口

- [Sprint-67 建模主线人工 E2E 操作手册](manual-e2e-guide.md)：面向首次使用者，按真实界面说明上下文、按钮关系、双起点、四类模型、分阶段门禁、发布与指标交接。
- 手册引用既有 Chrome 95 证据，不重复执行已通过的相同浏览器场景；每轮人工测试应使用手册中的记录模板保存本轮真实 ID 和结果。
- 2026-07-21 首次人工操作发现规划台账、计划头编辑和归档入口缺失，已新增 [F2-T05](../features/F2-规划输入与分阶段门禁/T05-补齐建设规划台账与编辑归档.md)。当前最终 production build 与真实 Chrome 95 浏览器中的台账、编辑、409、归档、只读、失败恢复和 390px 已通过；dts-admin 权威目录、dts-platform 权限/CAS/来源部门严格相等及 PostgreSQL 也已分层通过，最终只读复审无 Critical/Important。由于浏览器 API 使用精确 mock，部署后真实登录/API/PostgreSQL 联动仍是 Sprint DONE 前的最后门禁。
- 同轮人工测试发现数据元错误依赖浏览器规划会话、业务分类页会自行生成临时计划，已新增 [F6-T05](../features/F6-专业模块交接与集成验收/T05-统一数据元正式规划上下文与落标草稿门禁.md)。代码、60 项定向测试、核心 lint、最终类型检查与 production build 已通过；真实 Chrome 95/API 联动完成前保持 IN_PROGRESS。
- 2026-07-22 模型新建人工测试发现来源 ID/版本与维度/层级系统码需要手工填写，已新增 [F6-T06](../features/F6-专业模块交接与集成验收/T06-统一模型来源选择系统编码与提交时实时复验.md)。前端 11 项 TDD RED、扩展 focused GREEN 59/59 和 10 文件 Biome 已记录；后端 resolver、最终类型检查/构建及真实 Chrome 95/API/PostgreSQL 仍待验收。
- 同日 FACT 创建复核确认，已同步具体表只是可选的直接物理上游，不是当前模型目标表。已新增 [F3-T06](../features/F3-维度与四类表直接建模/T06-拆分上游输入与目标模型并后移来源门禁.md)：草稿可无输入保存；进入实现前满足有效 `sourceRefs OR dependsOn`，两类同时提供时全部引用都要有效。自动化、最终构建和真实联动证据齐备前保持 IN_PROGRESS。
- 2026-07-23 继续复核发现四类模型仍可选择 ODS/STG 目标层，已新增 [F3-T07](../features/F3-维度与四类表直接建模/T07-建立模型类型与分层依赖矩阵并收敛ODS入口.md)：ODS_RAW/ODS_STANDARDIZED/STG 归接入/技术链，四类目标层固定为 DIMENSION/FACT→DWD、SUMMARY→DWS、APPLICATION→ADS，并按类型限制上游。后端矩阵/历史只读与前端投影/候选/revision 显式升级已完成聚焦自动化，证据见 [f3-t07-model-layer-dependency-matrix.txt](evidence/backend-contract/f3-t07-model-layer-dependency-matrix.txt)；production build、真实联动和迁移证据完成前保持 IN_PROGRESS。
- 2026-07-24 继续复核确认维度目录仍把概念维度、逻辑维度表和数据实现压入同一 DIMENSION ModelSpec。T02 重新打开，新增 F3-T08/T09/T10 与 [F6-T08](../features/F6-专业模块交接与集成验收/T08-完成四层模型与API-Landing真实端到端验收.md)，权威边界见 [四层模型最小闭环设计](../assets/modeling-four-layer-minimal-loop-design.md)。API 只复用既有采集能力生成 Landing 物理资产，不扩展 OpenAPI/GraphQL/SOAP。

## 1. 验收旅程

### Journey 0：建设规划台账与创建后修正（F2-T05 增量）

1. 空环境进入 `/modeling/plans`，创建第一个规划并返回同一 `planId`；
2. 从工作台和计划详情分别打开同一编辑器，修改名称、目标、范围和责任信息；
3. 使用旧 plan-head ETag 制造 409，验证输入保留、加载最新版和显式重试；
4. 从台账搜索、筛选并查看计划，归档后默认活跃列表移除；
5. 只读账号无写动作，直接 PATCH/archive 仍由真实 Spring Security 拒绝；
6. Chrome 95 桌面与 390px 无横向溢出，列表/API 失败不伪装为空态。

### Journey A：从业务目标开始

1. 创建 WarehousePlan；
2. 选择业务分类和分层策略；
3. 不创建业务对象，登记一个维度；
4. 创建明细表并填写“一行代表什么”、粒度键、来源和时间语义；
5. 关联字段标准；
6. 生成/接管实现并完成质量、审核和发布；
7. 从已发布模型创建原子指标和派生指标；
8. 返回工作台时九站投影和唯一下一步正确。

### Journey B：从现有资产开始

1. 从表或 dbt 节点创建计划；
2. 确认业务分类、分层和来源；
3. 生成维度/明细/汇总/应用候选；
4. 人工确认模型类型、粒度、键和用途；
5. 不经过业务对象页面完成实现、发布和指标引用。

### Journey C：旧深链与旧数据

1. 访问旧 `/modeling/semantic/objects?objectId=...`；
2. 权限不扩大地跳转到维度或模型目标；
3. legacyRef 可定位迁移后的 ModelSpec；
4. 旧写 API 明确拒绝并留下调用审计；
5. 回滚开关可恢复旧只读视图但不能恢复双写。

### Journey D：全局目录与正式规划/模型交接（F6-T05 增量）

1. 不带 planId 直接进入数据元和业务分类，确认全局列表/维护可用且没有“缺少数仓规划”整页阻断；
2. 无 planId 从业务分类点击进入模型，确认只前往建设规划台账，不生成 `warehouse-plan-*` 或 session 计划；
3. 从计划基线进入业务分类，维护后返回同一 planId 的 categories Tab；
4. 从模型详情“字段标准”进入数据元，经标准包导入页往返后回到同一 modelSpecId/revision/planId；
5. 验证外站、跨模型、跨计划和额外参数 returnTo 被拒绝；数据元 API 失败显示重试而不是空态；
6. 在模型字段保存标准稳定 ID/版本，确认数据元页不产生 `standardDraftId` 或 session fallback。

### Journey E：规划来源与系统编码（F6-T06 增量）

1. 只创建或测试数据连接而不同步元数据，确认具体表不会出现在模型来源中；
2. 完成具体表元数据同步，在当前计划按“已验证连接 → Schema → 具体表”加入并确认；确认系统自动关联来源标识和版本，不执行 ETL/ELT 仍可进入模型设计；多人并发时验证“加载最新版、合并并重试”不会丢失或重复来源；
3. 在 FACT 表单中按业务名称选择规划来源，确认类型、标识、绑定 ID 和版本自动带出且不可编辑；
4. 新建维度和多个层级，确认系统编码自动生成、只读，修改业务名称不改变已生成编码；
5. 切换计划，确认原来源立即清空；把旧来源改为过期或漂移版本，确认详情只读诊断并引导回来源盘点；
6. 伪造 `bindingId/ref/version` 或在提交前改变后端来源，确认 resolver fail closed、不产生新 revision；部门账号同时伪造其他部门的 `planId`/`X-Active-Dept`，确认计划读取返回遮蔽 404 且连接、目录表不会越权；最后在 Chrome 95 桌面与 390px 记录 API/DB 证据。

### Journey F：FACT 草稿与两类上游输入（F3-T06 增量）

1. 新建 FACT，仅填写目标数仓分层、名称、用途和 grain，不选择物理来源或上游模型；保存草稿应成功，进入实现门禁应提示补齐一种上游输入；
2. 选择当前计划的已确认物理来源，确认“上游来源分层”和“目标数仓分层”分别显示，且实现门禁接受该路径；
3. 新建另一 FACT，只选择锁定 revision 的上游 ModelSpec，不重复选择它对应的具体物理表；确认保存成功、编译投影使用上游模型引用；
4. 同时选择物理来源和上游模型，再让其中一类发生版本漂移；确认门禁 fail closed，不以另一类当前证据掩盖错误；
5. 构造 FACT 直接/间接依赖自身，确认循环依赖被拒绝；
6. 在未运行 ETL/ELT 的前提下验证逻辑草稿可保存，同时确认编译、构建、运行和发布仍必须通过各自真实门禁。

### Journey G：模型类型、分层依赖与 ODS 技术入口（F3-T07 增量）

1. 分别新建 DIMENSION、FACT、SUMMARY、APPLICATION，确认目标层自动且只读显示 DWD、DWD、DWS、ADS；
2. 尝试从页面和直接 API 把四类模型目标层改为 ODS_RAW/ODS_STANDARDIZED/STG 或错误业务层，确认返回 `MODEL_SPEC_TYPE_LAYER_MISMATCH`；提交类型不接受的 sourceRefs/dependsOn/generationStrategy 时返回 `MODEL_SPEC_INPUT_KIND_NOT_ALLOWED`，数据库均无非法 revision；
3. 验证上游候选：DIMENSION 为 ODS/STG，或当前计划已确认的存量/外部管理 DWD 物理来源，也可用生成策略；FACT 为同类物理来源或 FACT@DWD，DIMENSION 另走 dimensionRefs；SUMMARY 为 DIMENSION/FACT@DWD 或 SUMMARY@DWS；APPLICATION 为任意合法 DWD/DWS/ADS 四类模型；
4. 构造 DWD 反向依赖 DWS/ADS、未锁 revision、漂移、循环和无权候选，确认稳定 blocker 与修复入口且 fail closed；
5. 对已有 ODS 表执行“元数据同步 → 标记 ODS_RAW/ODS_STANDARDIZED → 加入当前规划”；对尚未产生的 ODS 表从接入映射/同步任务创建，运行和同步后再纳入规划；
6. F3-T07 完成专属分类/UI 后，打开历史 ODS/STG ModelSpec，确认审计/血缘可读，编辑、实现、发布复用 `MODEL_SPEC_LEGACY_READONLY`；当前不得把该后续目标记为已有证据；
7. 以真实 Chrome 95/API/PostgreSQL 记录允许/禁止组合、blocker、revision 和数据库计数，mock 截图不得替代服务端事实。

### Journey H：四层模型、API Landing 与三阶段 UI（F3-T08/T09/T10、F6-T08 增量）

1. 无连接登记业务维度，确认目录记录不包含来源、目标层和物化配置；从该维度创建 DIMENSION 逻辑草稿；
2. 不选择来源完成逻辑设计，再选择受控生成器物化日期维度，验证目标物理资产和血缘；
3. 完成数据库连接、具体表元数据同步、规划确认和模型实现，验证连接测试不会自动加入计划；
4. 创建并试跑真实 API 采集任务，确认 Landing 表、字段、checkpoint 和元数据 revision 后加入规划并完成模型物化；
5. 验证 FACT → SUMMARY → APPLICATION 只使用合法资产或锁定模型 revision，漂移后阻塞并可恢复；
6. 验证轻量新建、逻辑设计/数据实现/物理资产三阶段详情、受控 returnTo、失败恢复和 Chrome 95 390px；
7. 对存量 DIMENSION 执行 dry-run、迁移、计数对账和回滚，旧深链保持可读且无新混合写入。
8. 普通模式生成 ephemeral STG 且物理资产表无虚假记录；转换 dbt 高级模式后保留原逻辑模型，真实 STG view/table 登记为技术资产并进入血缘。

## 2. 自动化矩阵

| 层级 | 必测内容 | 证据 |
|---|---|---|
| Contract | ModelSpec 无 objectId、四类型门禁、FACT 的 DRAFT/IMPLEMENTATION 输入差异、类型→目标层→允许上游矩阵、WarehousePlan 基线 | Java/TS 单元测试输出 |
| API | 计划、分类、模型、来源提交时 resolver、门禁、迁移 dry-run、旧写拒绝 | 后端集成测试报告 |
| Migration | 幂等、校验和、冲突、孤儿、租户隔离、回滚 | SQL/服务 dry-run 报告 |
| UI source-contract | 菜单禁用退役词、路由映射、唯一主动作、参数白名单、规划来源选择与系统编码只读 | tsx/node 测试输出 |
| Browser | 两条主线、旧深链、权限、失败恢复、窄屏 | Chrome 95 截图/视频和日志 |
| Build | dts-platform、dts-admin 菜单、两个 webapp | Maven/pnpm 构建记录 |
| Scope | 预期模块和 execution flow | GitNexus detect_changes 报告 |

## 3. 必测输入边界

- 无 planId 浏览目录与发起创建；
- planId 无权限、domainId 无权限、domainId 已归档；
- FACT DRAFT 缺粒度；FACT IMPLEMENTATION 两类输入均为空、已填写引用失效、缺时间语义；
- DIMENSION 缺维度键；
- SUMMARY/APPLICATION 缺上游；SUMMARY/APPLICATION 同层上游未锁 revision、非 CURRENT 或形成循环；
- 四类模型目标层为 ODS_RAW/ODS_STANDARDIZED/STG 或与类型不匹配；上游层反向/越层；
- sourceRefs 的模糊 ODS 未确认 RAW/STANDARDIZED；dependsOn 未锁 revision；
- revision 漂移、来源删除、标准版本漂移；
- 只有连接但未同步具体表、来源未确认、跨计划绑定、伪造来源身份/版本；
- 旧对象可自动迁移、需拆分、应归档三类；
- 同一迁移批次重复执行；
- API/网络失败、刷新、返回、窄屏和 Chrome 95。

## 4. 禁止回归

- 页面或菜单重新出现“业务对象/语义对象”客户术语；
- 新建模型请求继续写 `objectId`；
- 维度创建要求业务活动；
- 页面访问或 sessionStorage 被当成完成证据；
- 全局数据元或业务分类因缺 planId 被整页阻断，或专业页面自行生成临时 WarehousePlan；
- 模型表单要求手工输入来源绑定 ID/版本或维度/层级唯一系统码，或切换计划后保留旧来源；
- FACT 草稿再次强制具体物理来源，或把目标数仓分层、上游来源分层及目标表混为同一输入；
- 四类模型重新允许自由选择 ODS/STG 目标层，或拒绝合法的 SUMMARY@DWS/APPLICATION@ADS 同层派生，或接受矩阵外上游；
- 历史 ODS/STG ModelSpec 恢复写入、实现或发布，或模糊 ODS 被静默分类；
- 旧路由跳转丢失 planId/modelSpecId 或扩大权限；
- 指标、发布、运行或血缘因语义服务拆分失去真实后端记录。

## 5. 证据目录约定

实现阶段在 `it/evidence/` 下按 `contracts/`、`api/`、`migration/`、`frontend/`、`chrome95/`、`build/`、`gitnexus/` 分类保存。README 只索引真实文件；未产生证据的检查不得标记 DONE。

F6-T02 已集中完成一次后端契约批次、一次 PostgreSQL Testcontainers 集成、一次前端 production build 和一次 Chromium 95 定点回归。对应证据为 `backend-contract/model-lifecycle.txt`、`runtime/compile-test-publish-run.json`、`runtime/failure-repair-loop.md` 和 `chrome95/README.md`；真实部署 E2E 不由 mock 浏览器证据替代。

F6-T03 已在部署环境完成 BUSINESS_FIRST 发布与指标回绑、ASSET_FIRST 四类模型与 FACT 发布、旧深链未分类恢复和旧写 410，真实 Chrome95 结果 3/3 PASS。权限通过真实 Spring Security filter chain，跨租户/自动映射/幂等通过 PostgreSQL Testcontainers，失败恢复由后端门禁测试和 Chrome95 UI 故障注入分层验证；记录 ID 与真实性边界见 `runtime/e2e-record-ids.json` 和 `backend-contract/f6-t03-boundaries.txt`。

F6-T04 已完成最终候选构建、受控镜像回滚/恢复、容器健康检查、数据库前后对账和 Go/No-Go 评审。发布主线为 GO；旧结构物理删除仍为 NO-GO，外部 Airflow/dbt 在 `RUNTIME_DISABLED` 环境下不声明成功。最终索引见 `review/final-go-no-go.md`、`review/completion-layer-matrix.md`、`build/final-verification.txt` 和 `migration/final-rollback-drill.md`。

F6-T05 已完成会话规划/page-wide 落标草稿退役、安全标准 owner 返回链、伪 planId/路径穿越防护、60 项定向测试、核心 lint、最终类型检查、production build、GitNexus LOW 风险审计及前端容器部署验证；证据为 `frontend/f6-t05-standard-owner-context.txt`。真实登录 Chrome 95/API Journey D 尚未执行，因此 Task 与 Sprint 均保持 IN_PROGRESS。

F6-T06 已用首轮 11 项 TDD RED、前端扩展 focused GREEN 59/59 和 10 文件 Biome 固定按业务名称选来源、系统字段自动关联、计划切换清空、过期来源诊断和维度/层级系统码只读契约；证据为 `frontend/f6-t06-model-source-selection.txt`。后端 resolver 定向回归、最终类型检查/构建与真实 Chrome 95/API/PostgreSQL Journey E 尚待执行。

F3-T06 已完成需求与验收矩阵纠偏，自动化实现和证据正在补齐。只有聚焦 Java/TypeScript 回归、一次最终 production build、GitNexus 变更范围审计及部署后 Journey F 全部通过，才可补充证据并关闭 Task；原 F3-T01-T05 的历史证据不能替代本次新增验收。

F3-T07 已完成目标层强制、按类型上游过滤、历史类型边界只读、revision 显式升级及前后端聚焦契约测试。ODS 专用接入/安全返回、旧 ODS/STG 迁移 UI，以及最终 production build 和部署后 Journey G 仍未完成，因此 Task 保持 `IN_PROGRESS`。

F3-T08/T09/T10 和 F6-T08 是 2026-07-24 四层对象纠偏的新增门禁。只有业务维度迁移、统一实现输入、API Landing 资产、三阶段 UI、一次最终构建和真实 Journey H 全部通过，才允许恢复 DIMENSION 主线的完成结论。

## 6. 发布决策

只有 G1-G10 全部通过才能将 Sprint 标记 DONE。若新主线可用但旧调用未归零，允许发布为“业务对象对外退役、旧表只读”，不允许宣称物理删除完成；退出项必须留在 F5 证据中持续追踪。
