# Sprint-104 审查整改执行契约（2026-09-08）

基线：18b92e8f9；原 feature-review-20260908.md 基于 1ad474167，落后 30 个提交。用户已同意按复核后的顺序实施。本记录只登记契约与执行状态，不替代测试证据。

## 顺序、归属与关闭证据

| 顺序 | 问题 / Task | 冻结要求 | 关闭证据 |
|---|---|---|---|
| 1 | R08/R10/R12/R13；T09/T15/T20 | SOURCE 是第五类，固定 ODS；三步建模替代四步强制治理；历史发布服务保留；任务状态据实际证据 | 任务/Feature/队列一致；手工 IT-19–24 与替代映射完整 |
| 2 | R01/R02；T12/T19 | 已有资产人工 owner/description（包括 null/空值）不由发布补写；技术投影更新和归档递增现有 version；tags 须合并系统键、保留人工键；密级仅提升，生命周期沿用原门禁 | 真数据库登记/再发布/主动清空/归档/旧版本保存冲突；PUT 缺头428、旧版本409、正确版本成功 |
| 3 | 新增 R14；T15/T17 | 在现有候选 owner 内区分结构物化与发布占用；同模型/目标冲突受保护，跨模型结构完成不强制发布；详细字段/索引/锁协议在本记录追加后才编码 | 四层连续物化；同请求重放；并发/目标重叠拒绝；旧 DATA_BUILD/发布兼容；正式迁移验证 |
| 4 | R03/R04/R05/R06/R07；T01/T03/T10/T13 | 状态区分未开始/未登记/失效/读取异常；旧连接归并不污染外层事务；历史模型只读分析；绑定集合单一口径；授权不按每行全量遍历 | 状态矩阵、真实事务并发、历史统计、畸形绑定反例、权限与查询次数 |
| 5 | R09/R11；T08/T14/T20 | 复用已有 dts_schema_only，禁止另建 DDL 引擎；按最终代码差异执行正式测试/包/容器/Chrome | 四层真实表字段/键/零行，接入写数和同资产人工字段保持；离线证据单列 |

## R01/R02 契约冻结

页面：数据资产详情的负责人/说明保存；服务 owner 为目录。PATCH governance-summary 仅修改传入 owner/description，null 表示主动清空。新资产创建可使用操作者/模型说明作初值；已有资产永不借空值补写人工字段。

发布的 registerCatalogDataset 与 rollbackModel 继续在原事务和物理定位锁中执行，不新建目录写入服务。更新 catalog_dataset 必须递增 version，使持有旧 ETag 的 PUT/PATCH 返回409。无需新增数据库列或数据回填。系统 tags 键按当前发布 payload 合并，其他键保留；不得降低 classification 或绕开 lifecycle gate。

PUT /api/catalog/datasets/{id} 要求 If-Match: "catalog-dataset:{id}:{version}"。缺失428，过期409；客户端先读取当前对象/版本，冲突后重新读取并由用户决定重提，不盲目自动覆盖。发布前核查仓内调用和实际外部使用证据；仓内无调用不等于外部无调用。

## 当前验收基线

4e30da13d 对应正式三镜像、后端75 PASS（未变后端沿用8dc3a6261）、前端48 PASS/5原有SKIP及 Chrome 分项记录已存在。IT-20 被活动批量候选 BUILT 阻断；IT-22 写数未执行；独立离线环境待指定。不得把历史成功转填本次整改 PASS。

## 执行边界

开发目录仅源码、静态检查、commit/push；部署目录拉取同 SHA 后执行编译测试和正式构建。正常业务测试及正式版本化迁移可验证，禁止手改业务数据/候选状态绕过缺陷。原始他人 review 保持不变。

## R14 / K33 候选占用增量（FROZEN，待实现/验证）

复用现有不可变 origin varchar(32)，新增 SCHEMA_ONLY_INTENT；该枚举是结构单模型命令的持久化用途，不新增候选表或执行器。外部请求仍为 build-intents.buildMode=SCHEMA_ONLY，只有与当前实现的结构生成器匹配时服务端可创建此 origin；旧请求缺省 DATA_BUILD 和旧 origin 保持不变。前端候选 DTO 同步枚举。

- build-intents 在原 plan 行锁内读取候选。DATA_BUILD 保留普通候选的规划独占；SCHEMA_ONLY 仅复用同模型/版本/环境的结构候选。任何两类候选同模型同环境重叠均拒绝；无关批量 BUILT 不阻断结构候选。
- canonical createWithOrigin 同样执行范围冲突校验，不能只在 HTTP facade 放行。结构候选恰有一个模型；原批量/数据候选间仍规划唯一。结构与普通候选同模型同环境互斥；模型 active_claim_key 的现有唯一索引继续保护运行。
- 物化快照写入前，按 execution_target_key + target_identifier 获取事务级 advisory lock；存在其他活动候选占用同目标且任一为结构候选则409 MODEL_MATERIALIZATION_TARGET_CLAIM_CONFLICT。同候选不同模型也不可指向相同目标。以当前执行配置为范围，保守拒绝同执行目标下同名表，不猜测其他 schema 可安全共存。目标已有表仍由受控 CREATE 拒绝，绝不 DROP/TRUNCATE。
- 规划发布工作台只选普通发布候选；模型工作台仍按精确模型/修订/环境读取自己的候选。结构 BUILT 保持真实历史，不自动发布或取消。原同模型修订更新沿用显式失效/替换流程，不静默删除历史。
- 前向 Liquibase 20260908_01_model_schema_candidate_scope.xml 扩展 origin CHECK 并将原 uk_model_release_candidate_active_plan 的规划唯一范围限定为非 SCHEMA_ONLY_INTENT 活动候选；旧状态与数据不回填、不改写。保留原模型占用唯一索引。迁移失败整体回滚；若已有新 origin 行则拒绝旧 schema rollback。上线后旧应用不能识别新 origin，因此有新候选时必须前向修复，不能盲目回退旧镜像。
- 只读基线：当前候选7条（批量 BUILT1/PUBLISHED2/STALE1/CANCELLED3），模型12条（SOURCE1/FACT7/DIMENSION2/SUMMARY1/APPLICATION1）；分析绑定1条、无缺 tenant/source 的 legacy 行。当前没有结构 origin 数据，迁移不需要数据清洗。

验收：两种创建顺序的跨用途范围冲突；同请求重放与模式错配；同模型环境竞争；不同模型连续 BUILT；目标重叠拒绝且不产生新运行；旧 batch 独占；规划工作台不报结构候选歧义；隔离空库/升级库迁移和回退保护。当前未执行，不标 PASS。

## R03–R07 实施切片（FROZEN）

- 目录无输出且当前候选有效：NOT_STARTED / MODEL_DELIVERY_CATALOG_NOT_REGISTERED，不要求先发布；候选已过期才 UNKNOWN / MODEL_DELIVERY_EVIDENCE_STALE。分析无发布引用：NOT_STARTED / MODEL_DELIVERY_PUBLICATION_REQUIRED；旧发布不匹配仍 STALE。读取失败保留既有独立错误分支。verification 可执行恢复动作时不附禁用原因。
- 分析旧连接归并新增内部 writer.adoptLegacy(id:Long,tenantId:String,platformDataSourceId:UUID,expectedDetails:String)，REQUIRES_NEW 内按 ID 悲观锁重读，确认旧 details 未漂移且未被其他租户接管后设置归属并 saveAndFlush；外层只传标量，不修改托管 legacy 实体。唯一冲突在内层回滚后重读赢家；现有错误码不变。真实 H2/JPA 事务测试覆盖外层持有 legacy、内层冲突、外层提交，PostgreSQL 唯一约束迁移沿用既有证据。
- 标准 R 用字段索引找缺失，B 始终遍历全部声明引用；字段为空/不存在/重复的声明由专业适配边界判 STALE。NONE 仅令 R 为空，不跳过非空 B。新请求与历史数据的执行拒绝保持一致。
- 授权端口新增 canReadPlan(UUID):boolean，默认兼容实现复用 listPlans；正式 adapter 对配置租户精确 get 一个计划并调用原 canReadPlan guard。候选读侧使用该方法，缺计划或不可读仍拒绝，其他读取异常不当作无权限吞掉。不增加全局缓存。

## R05 存量只读画像（2026-09-08）

在现有 dts_platform 的 READ ONLY 事务中按 ModelSpec 当前修订关联 revision.snapshot_json：当前12个修订、历史25个修订，grain.keys 与 fields[role=KEY].name 排序集合不一致数均0，重复 grain 键数均0。没有修改模型或数据，无需自动修复。catalog_dataset 非空 tags 中非 JSON object 数0。该结果关闭本环境画像缺口，不替代旧模型读取/暂存/提交/编译的行为回归，也不外推到其他客户环境。
