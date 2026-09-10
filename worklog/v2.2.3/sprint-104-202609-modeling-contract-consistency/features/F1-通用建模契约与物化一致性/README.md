# F1：通用建模契约与物化一致性

**优先级**：P1  
**状态**：IN_PROGRESS  
**目标**：用户通过既有模型工作台完成通用模型编辑、恢复、物化与质量检查，不因不同组件的规则冲突被误拦截。

## 契约定义

API 前缀均为 `/api/modeling/model-specs`，复用 Sprint [账本 C07](../../README.md)。

| ID | 输入 / 输出契约 | 保持不变与整改点 |
|---|---|---|
| K1 模型 | POST 根路径 / PUT `/{id}`；UUID 身份和业务归属；modelType 五类（SOURCE/DIMENSION/FACT/SUMMARY/APPLICATION；SOURCE 固定 ODS，详见 K31）；grain={statement:string,keys:string[]}；fields 含 name/dataType/nullable/role；返回 ApiResponse 包裹模型及 ETag | 当前 contractVersion=2；If-Match 及修订冲突；禁止未知字段，但不得拒绝合法业务过程/主题域 |
| K2 创作草稿 | authoring-context；authoring-drafts 的创建、保存、validate、commit；snapshot 的 modelSpec、visualImplementation 和 schemaVersion；dimensionProfile 完整对象 | 复用现有 Create/Save/Validate/Commit 请求类型及草稿 CAS，不编造新版本头；T01 冻结确切字段名/存储落点后 DoR 才通过 |
| K3 阶段/标准 | GET `/{id}/stage-gates`；Stage=DRAFT_SAVE/DESIGNED/IMPLEMENTATION_READY/RELEASE_READY；策略 NONE/KEY_AND_MEASURE/ALL_FIELDS；版本化标准引用 | 输出阶段状态和 blockers(code,field,message,repairRoute)，缺失、失效、不可用分开 |
| K4 编译/质量 | POST `/{id}/lifecycle/compile\|tests`；当前模型/实现身份；grain.keys[]、fields.KEY；输出确定性 SQL/YAML 与当前证据 | 完整键组合唯一，每个必要键非空；不新增业务唯一约束或假键 |
| K5 实现/物化 | PUT `/{id}/implementation`，If-Match + If-Match-Implementation；settings.loadStrategy、partitionFields[]、materialization；POST `/{id}/build-intents` 的 planId/environment 和 Idempotency-Key | 既有 settings_json/实现修订；当前值优先，显式 FULL/[] 不回退；同键重放沿用现有协议 |
| K6 能力 | GET `/implementation/capabilities`；原四类及 SOURCE 的 inputModes、loadStrategies、materializations 与增量键要求 | 页面/服务/编译一致；不支持项前置说明，不默认扩大执行能力 |

**错误与并发共同约束**：沿用既有 ApiResponse、errorCode、关联 ID 和异常映射；格式/阶段错误按当前 4xx 契约返回，不转成 500；旧版本冲突不覆盖数据；无权操作保持 401/403 现有语义。每个入口的确切状态码由 T01 实测、T02 写入样例矩阵，不假定所有入口一致。
**数据与迁移**：复用账本 C08 的模型与实现 JSON/修订；创作草稿落点由 T01 补录。无新增业务表计划，不修改已执行 changeSet。

## UI/UX 规格

- 入口：数据建模 → 维度建模 → 模型工作台；从现有模型详情进入，不新增菜单。
- 复用布局：基本信息/逻辑字段与标准 → 物理实现与加载 → 发布与物化面板。
- 空态：未选来源、未定义字段或无运行记录时给出对应补齐入口，不填造时间或键。
- 加载态：读取/保存/启动运行时显示进度，避免不受控重复提交。
- 错误态：字段或阶段错误就近展示，保留编辑内容；过期版本提示重新加载/处理冲突。
- 成功态：显示当前保存版本及当前候选/运行结果；“已保存”“物化成功”“质量通过”独立呈现。
- 关键交互：保存→K1/K2；恢复草稿→K2；标准/阶段校验→K3；编译/检测→K4；加载配置/物化→K5；可选项→K6。
- 走查：选择模型→编辑/暂存→关闭恢复→提交→检查实现→启动物化→查看质量及修复项；具体数据和正反分支见 [IT](../../it/README.md)。
- 兼容：Chrome 95、1366×768 与窄屏；键盘可用；不引入仅新浏览器支持的 API/CSS；截图不得含凭据或敏感数据。
- T05/T06 改动页面需保存失败、恢复失败和冲突态；T03/T04 需展示真实阻断原因，不只显示笼统失败。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | [验收基线与契约失败归因](T01-验收基线与契约失败归因.md) | P1 | IN_PROGRESS | 无；作为本 Feature 的前置基线任务 |
| T02 | [三端字段与分阶段校验契约对齐](T02-三端字段与分阶段校验契约对齐.md) | P1 | IN_PROGRESS | T01 |
| T03 | [标准覆盖策略与专业证据范围统一](T03-标准覆盖策略与专业证据范围统一.md) | P1 | IN_PROGRESS | T02 |
| T04 | [复合粒度键与质量测试生成一致](T04-复合粒度键与质量测试生成一致.md) | P1 | IN_PROGRESS | T02 |
| T05 | [维度创作草稿完整恢复与保存](T05-维度创作草稿完整恢复与保存.md) | P1 | IN_PROGRESS | T02；T07 保存/执行边界 |
| T06 | [当前实现配置与旧策略回退语义修复](T06-当前实现配置与旧策略回退语义修复.md) | P1 | IN_PROGRESS | T02；T07 保存/执行边界 |
| T07 | [输入方式与历史执行能力边界冻结](T07-输入方式与历史执行能力边界冻结.md) | P2 | IN_PROGRESS | T01 归因；先冻结边界，后消费 T02 做一致性验证 |
| T08 | [端到端回归与离线交付准备](T08-端到端回归与离线交付准备.md) | P1 | IN_PROGRESS | T02–T07 |

## 当前源码测试证据（2026-09-06）

T02–T07 的已完成源码项及页面待办分别登记在各 Task。部署目录 Java 专项 99/99、工作台 Vitest 92/92 的命令、SHA 和边界见 [源码专项归档](../../it/evidence/source-test-summary-20260906.md)。IT-04 ephemeral 专项已通过；其余页面与运行验收未完成，T08 保持 IN_PROGRESS。

## Definition of Ready

- [x] 目标、现有 UI 落点、五项整改和非目标已写明。
- [x] 现有接口及关键字段已登记。
- [ ] T01 环境/真实样本/草稿 CAS 与存储确认；现场缺口按影响范围阻塞验收，不阻塞已获授权且输入已固定的源码整改。
- [ ] T02 阶段矩阵及 13 项失败归因固定，相关依赖满足。
- [ ] NFR 检查和能力边界经评审；未决项不靠编码猜测。

## Definition of Done

- [ ] 所有 Task 的契约、UI 和实际竖切片均有证据，状态同步 Sprint/queue。
- [ ] 旧 payload 兼容；保存/恢复不丢值；不误判复合键和标准覆盖。
- [ ] 完整契约回归、生产构建、真实物化/质量、Chrome 95 分别验证。
- [ ] 完成 T08 聚焦 review、离线资源清单、操作说明；未部署不得登记已部署。


## 2026-09-10 首次安装补充

本任务既有证据不覆盖“零建模上下文”的首次保存。S10DC-80 的初始化原子性、菜单权限和空库验收由 [F7/T40–T43](../F7-首次建模初始化与菜单授权一致性/README.md) 接续；现行权限以 F7 用户确认规则为准，原任务未验收项继续保留。
