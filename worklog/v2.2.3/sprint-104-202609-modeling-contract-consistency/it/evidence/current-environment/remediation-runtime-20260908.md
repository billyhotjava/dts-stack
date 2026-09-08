# Sprint-104 整改验证（2026-09-08）

本轮开始基线18b92e8f9，源码实施1cedaebe8/5e79f2e72/9ecc5502a，测试登记与夹具修复eba4ea4ea，最后候选替换单模型保护c3187bc394776f4a29ffc8a1765f33560bccb731。原他人 review 未修改/未提交。

## 源码与正式测试

所有编译测试均在 /opt/prod/s10/deploy；Maven 使用既有 3.9.9/Temurin21 构建容器。开发目录只执行源码及静态检查、commit/push。lefthook 不在 PATH，未声称 hook 通过；Git diff --check 和 GitNexus detect_changes 已执行。

| 范围 | 受测版本/结果 | 日志 |
|---|---|---|
| build-intent、状态读取、候选应用/命令、阶段门禁、授权 adapter、目录 ETag | 9ecc5502a；156 PASS | /tmp/s104-remediation-tests.log（本次资产 IT 失败另表处理，不能把整次命令记成功） |
| 资产登记/发布/归档真实 PostgreSQL IT、标准证据、范围矩阵、迁移/回退保护 | eba4ea4ea；17 PASS，BUILD SUCCESS | /tmp/s104-assets-scope-retest.log |
| 分析连接注册、真实 H2/JPA 内外事务碰撞 | 9ecc5502a；5 PASS，BUILD SUCCESS | /tmp/s104-analytics-transactions.log |
| 最后单模型替换保护的候选命令与 build-intent 复验 | c3187bc39；40 PASS，BUILD SUCCESS；为上表子集重跑，不重复累计 | /tmp/s104-final-scope-tests.log |
| 前端候选类型契约 | 9ecc5502a；tsc --noEmit 退出0，此后前端未变 | /tmp/s104-remediation-tsc.log |

合计178个不同 Java 用例通过。新增测试已登记进平台 Maven 显式 testIncludes，未把未编译的测试视为通过。

资产 IT 实际验证：人工 owner/description 保持、主动清空不被补写、人工 tag 保留、version 递增、陈旧版本写入拒绝、归档递增。新迁移验证既有 BUILT 不变、普通候选规划唯一、多结构候选共存、旧模型占用唯一约束保留、旧 origin 检查、升级/回退/再升级及新历史阻断回退。分析事务回归证明唯一冲突后旧 legacy 行仍无归属且外层成功提交赢家；不是只用 mock 声称并发可用。

初次失败已修复：测试泛型断言编译歧义、StartResult 引用、资产字段查询白名单、依赖 JSON 空格的 LIKE 断言、血缘夹具 SQL 未引用实际物理表名。没有修改运行数据库来使测试通过。

## 正式交付

c3187bc39 四镜像正式构建、包校验及容器部署已完成，详情见下节。Chrome 95 实测发现后续缺陷，中间版本8a805000c构建完成；最终修复版本9a5e7ce60构建及包校验完成，部署复验进行中。

## 浏览器与当前剩余工作

已通过正常登录进入当前环境工作台；Chrome95实际 userAgent 为 HeadlessChrome/95.0.4638.0，1366×768。测试账号关闭记住我，不在证据写入凭据。已在 c3187bc39 正式部署页面执行真实物化；后续修复需在新镜像复测。

待本次部署后实际验证四层连续结构物化/字段键零行、数据模块登记及接入写数、人工字段/CAS、治理与分析独立恢复。独立离线环境连接信息已向用户询问，当前未指定，不能记 IT-24 完整通过。

## c3187bc39 正式包验证

四镜像正式构建退出0，包 data/sprint104-release/remediation-c3187bc39.tar.gz（748M），SHA256=371d98d324fb4ea4d80adbdaf26d4d310a91fef6c82efab8016bf3f509f8efe8。逐镜像归档校验和、源码 revision、最终 image ID 与包内 dts_schema_only 宏均校验通过；manifest 包含4项镜像。证明文件 remediation-c3187bc39.tar.proof.json 留在部署目录。

| 服务 | 不可变镜像 tag | image ID |
|---|---|---|
| platform | dts-platform:s104-c3187bc39 | sha256:bfe492624ebbc35681ede8ce2b33e42050fecd369799b687a02cec1d1787aa5d |
| analytics | dts-analytics:s104-c3187bc39 | sha256:264cc8b7bb5f659fc23cda1ba99da9b92e3a5d1173c7c6b8f6974ba553ea2046 |
| ingestion | dts-ingestion:s104-c3187bc39 | sha256:dcd31abafd1652602a9c4edc3974403fe3f2902632c4a74efa4b603c4f5e908e |
| webapp | dts-platform-webapp:s104-c3187bc39 | sha256:1e475fce2a6e124197663efc029865604e40d658b1fddbe5d285a767c3719c6c |

Compose 已渲染核对四项 tag，原 project=deploy 与挂载证明在 previous-containers.json。受控四服务更新完成，三个后端 healthy，webapp running；未重建依赖服务/数据卷。Liquibase 20260908-01-model-schema-candidate-scope=EXECUTED。


## c3187bc39 Chrome 95 运行验收与后续修复

- ODS r3 / implementation r2：候选 e9ad328c-74fc-4ffe-9a29-f9ddf13fcba6，BUILT v3；DWD r4 / implementation r1：候选 e8495f1a-2016-45fd-9f4c-75b0e90eceba，BUILT v3。均由页面“开始物化”发起202，真实dbt和物理核验完成，页面刷新显示建模已完成。
- biadmin.public 的 ods_raw_s104_f3_2236_final（12字段）与 dwd_s104_f3_2300（2字段），只读核验类型/nullable 与模型声明一致，主键均record_id，业务行数均0。原批次6eef96db-5f6f-484c-80ac-309e5b53384d仍为BUILT v11。
- DWS r4 未声明度量，编译正确返回422 MODEL_SPEC_SUMMARY_MEASURE_REQUIRED；已通过模型页面将amount改为DECIMAL/MEASURE，保存为r5。此后发现旧实现仍允许下一步、预览事务回滚变500。
- ADS候选f7adb8ea-fef5-445b-9a54-615a1f384cc7实际进入BUILD_FAILED，原因MODEL_UPSTREAM_MATERIALIZATION_REQUIRED：结构物化派发误用了逻辑上游实表核验。保留失败证据，没有改候选状态或数据库内容。
- 07c1a0a37修复结构候选派发不解析物理上游，普通数据构建保留原门禁；18项相关Java测试通过（/tmp/s104-schema-dependency-tests.log）。
- 8a805000c修复模型新版本要求重交实现、预览解析用独立只读事务返回业务阻断；6项页面测试通过（/tmp/s104-wizard-actions-tests.log），12项计划/真实PG事务与迁移测试通过（/tmp/s104-preview-transaction-tests.log）。
- ODS数据管理入口正确携带modelSpecId/environment/candidateId；未定密的登记返回422 MODEL_SPEC_GOVERNANCE_CLASSIFICATION_REQUIRED，同时建模完成状态不受影响。登记、接入、治理/分析的完整成功路径尚未验收。
- 8a805000c四镜像正式构建日志/tmp/s104-runtime-fixes-build.log；最终结果待补充。

## 资产 PATCH / Chrome95 双窗口证据

已发布模型 e71715b7-ad1d-49b5-86e2-a91fa91e1b07 的环境是 test，候选393ab1c2-42b4-4d4d-ade9-bee8c48cfde3；dev页面无该版本证据，切换test后正确显示物化完成/已发布/已有质量规则。

资产a73937d1-9982-3c9f-95ae-890490d2d86f：窗口A以v27保存说明成功200/v28；窗口B以旧版本保存返回409，显示重新加载且保留“S104并发旧窗口草稿”。只读数据库确认A说明保持。随后A主动清空owner/description，200/v29；只读核验均SQL NULL，页面刷新仍为空。最后通过页面恢复原owner=xiezm，description保持原空值。没有手工数据库写操作。

截图：部署目录 data/sprint104-acceptance/cas-conflict-chrome95.png。

文件接入：remediation-input.csv两条合成行上传/解析/INTERNAL封存成功；第三步选择ODS模型返回409 MODEL_INGESTION_TARGET_NOT_MATERIALIZED。根因为ModelIngestionTargetService误用listForWorkbench的LIMIT 2，源模型被较新候选挤出；b3ecc132b改为现有精确模型/版本/环境查询，保留实现和物理证据校验，待正式测试部署。

分析恢复：同一已发布模型在test环境点击“重试分析准备”，POST serving-sync/retry=200；2026-09-08 07:21:43后台CatalogModelSemanticSyncWorker claimed=1 succeeded=1 failed=0。刷新delivery-status后materialization/quality/publication/catalog/analysis均SUCCEEDED且matchesCurrentTarget=true，analysis消息“分析准备已完成”。资产owner=xiezm/description为空未被同步覆盖。

Chrome95数据管理桌面1366×768与窄屏390×844截图已检查：非空白、控件未互相覆盖，窄屏body.scrollWidth=390。部署证据data-management-desktop95.png / data-management-narrow95.png；本次为c3187bc39数据页，新增向导修复仍待新版页面复验。


## 后续读取范围修复

8a805000c四镜像和正式包构建退出0，尚未部署；测试期间发现后续读取范围缺陷，因此最终交付继续以新代码构建，不把中间包当最终验收包。

b3ecc132b / 48bc20f35：模型接入目标精确查询、强制绑定环境；ModelIngestionTargetServiceTest共4 PASS，BUILD SUCCESS（/tmp/s104-ingestion-target-tests.log）。dbe470650进一步区分普通发布工作台最近历史与完整活动占用集；创建和build-intent冲突/复用查活动集，工作台排除结构候选，防止多个独立结构候选使普通批次或旧模型占用不可见。影响分析LOW；ModelIngestionTargetService不在当前GitNexus索引，已执行影响请求，结果UNKNOWN，另以源码确认调用仅用户解析与执行校验两入口。

DWS通过页面补度量后又显式将测试字段定为INTERNAL，当前模型r6，尚待新版页面重新提交实现并物化；没有替测试数据绕过定密门禁。


活动范围数据库回归的首次/再次运行分别因旧夹具引用已移除的modeling_business_object、缺少modeling_model_spec_revision外键记录而失败；9b7ddb18e/9a5e7ce60只修复夹具。dbe470650业务代码的80项服务测试通过，真实PG范围用例最终日志/tmp/s104-active-scope-postgres-final.log待补。没有更改生产库约束/记录来绕过失败。


9a5e7ce60：真实PG范围回归PASS（1/1，BUILD SUCCESS，/tmp/s104-active-scope-postgres-final.log），证明普通工作台仍返回旧批次、活动查询返回全部4条占用、精确模型查询能返回最早结构候选。此前80项服务测试结果保持有效，后续仅修夹具。最新四镜像正式构建日志/tmp/s104-final-runtime-build.log。


Chrome95帮助入口实测：由模型实现切换到模型物化，显示“物化成功即完成建模”和数据管理后续写数/治理/发布/分析；390px面板截图检查无文字覆盖。已把上述四张截图保存为本目录20260908-c3187bc39-*.png，方便随证据审阅。

## 9a5e7ce60 阶段正式交付（已由后续修复替代）

四镜像正式构建及升级包生成成功（/tmp/s104-final-runtime-build.log）；包SHA256=338e2055d86bfe38f3d2e9e383b0bd92a62b40f15ed4e5eb399964b52c96acf5。逐归档校验、四镜像sourceRevision与本地最终image ID及结构宏验证通过，见本目录20260908-9a5e7ce60-package-proof.json。正式Compose四服务定向更新日志/tmp/s104-final-deploy.log，健康和Chrome主线结果待补。

## 四层与数据治理实际主线（9a5e7ce60）

- 最终四镜像ID已与包证明核对；三个后端healthy、webapp running。11项补充结构/登记/写入保护测试PASS（/tmp/s104-final-boundary-tests.log）。
- DWS通过新版页面重新提交实现成为模型r7，checksum=e3f4e996ddc5c646f476a05ce1f68d8b17c31fd4ccb2ace3ae6324a55d72cfcd，候选23530616-daad-4272-b731-c24018cea1a3、runGroup=3f4d0340-a5b8-3b4a-b83b-5af77e21db5c真实物化成功；ADS原失败候选通过“重试构建”第二次成功，BUILT v5。
- 四层实表均0行；四表主键均record_id，DWS amount为numeric，ADS amount为text；旧普通候选6eef96db仍BUILT v11。截图20260908-9a5e7ce60-ads-complete-*已检查1366×768、390×844；响应式过渡结束后窄屏无横向溢出。
- DWS INTERNAL登记资产200，复用物理身份01d94b75-7aa1-30c1-bed9-43799853c01e。正常数据页新建规则f0ce195f-4544-44b6-9c41-9e13e1b73ddc，编辑复杂规则后正确返回原模型/环境。测试初始COUNT SQL不符合异常行结果约定；修正为SELECT record_id AS id ... WHERE record_id IS NULL，发布v2并正常运行25481e8a-75ee-495e-9b55-52465580b815 SUCCEEDED，候选QUALITY_PASSED；全程模型仍已完成。
- 初始错误SQL同时暴露审计bug：长mcq关联键当作actor导致varchar(64)异常、旧run cf38fbcc回滚为QUEUED。d251dae4a改用createdBy/lastModifiedBy，保留旧短手动actor回退，不扩DB列。
- DWS确认发布进入PARTIAL，根因结构候选把逻辑依赖误当物理输入；2a815d427保留模型逻辑血缘，结构候选无物理输入；普通路径不变，待部署正常重试。
- 文件计划#6上传解析封存及精确绑定成功（200），保存/生效成功，目标public.ods_raw_s104_f3_2236_final。首次执行6在Addax初始化因writer列为空失败，0行；a1974db51补生成列映射，继续禁止DDL/清空SQL，同时修正确认页目标与追加文案、审计destructive=false。45项Java、2项摘要Vitest、详情页Node契约均PASS；正式四镜像/包构建成功，未当最终部署验收。
- cfd1cb46a修复预览SQL仅识别原始SCHEMA_ONLY而漏DBT_MANAGED config.buildMode封装；原始/受控封装均停止物理递归，普通数据构建保留依赖解析。
- 9a5e7ce60浏览器事件中三个旧JS chunk 404来自跨部署保留的文件向导标签，路由加载自动恢复，新页面可正常保存/执行；后续重载后需独立记录最终页面错误。资产旧表单409为并发保护预期，未覆盖。

2a815d427最终补充回归：ModelSchemaCandidateScopePostgresTest 3、CandidateSchemaOnlyLineageTest 1、QualityRunAuditTest 10、CandidatePublicationRepositoryIT 8，共22 PASS/BUILD SUCCESS（/tmp/s104-publication-edge-tests.log）。PG测试同时证明受控DBT封装不递归逻辑上游，普通DBT仍读取上游；新审计测试已补入pom显式testIncludes。最终正式构建日志/tmp/s104-publication-edge-build.log。

旧质量工作流78ebec10-d63d-4cf9-8288-45bbcd17ab60已在Chrome95运行记录中按“取消→确定”正常取消，POST /api/governance/quality/workflows/{id}/cancel=200，页面“已取消”；旧子运行事实保留。新v2成功运行未受影响。

## 2a815d427 部署复验与文件 DAG 遗漏保护

正式包748M，SHA256=2817da7452c208eb70bf3387bd1c11ee8bd15bc0a1f55509594ae9d900f9d246；归档/镜像/结构宏校验通过，见20260908-2a815d427-package-proof.json。现有Compose四应用更新退出0，三个后端healthy，webapp running，未迁移数据卷。

- Chrome95 DWS正常“重试发布”200，候选23530616-daad-4272-b731-c24018cea1a3成为PUBLISHED v8。目录资产仍01d94b75-7aa1-30c1-bed9-43799853c01e，v58，owner=xiezm、人工说明“S104物化后数据资产治理验收，人工说明保留。”保持。只读确认DWD→DWS的MODEL_DEPENDENCY逻辑边保留，当前物理输入血缘为0。旧普通候选6eef96db仍BUILT v11。
- ADS正常预览POST200，canStart=true、blockers=[]，仅ROOT一项；逻辑上游旧版本不再被误当作结构物化的物理依赖。见20260908-2a815d427-browser-events.json。
- 文件确认页正确展示精确目标及“追加写入已有模型表（不清空、不重建）”。执行10在结构校验处失败；只读发现原ODS已有13列，多出带默认值的id bigint，仍0行、原record_id主键保持。之前“无DDL”的专项只证明TargetTableProvisioner与Addax配置，不能覆盖Airflow预任务；原首次执行6生成的init_target_table遗漏绑定保护，确实增加了id。此处纠正此前覆盖范围，不把0行当作结构未变证据。
- 064c802ce使AirflowDagService.buildCreateTableDdl在modelTarget绑定时返回空，不生成CREATE/ALTER/初始化任务。保留旧失败/受影响表，不手工删列。新增隔离模型0ed841a5-b4d5-4ba0-8ba4-e611471e5eeb由正常API创建和暂存、Chrome95提交实现并物化；目标ods_raw_s104_file_guard_0830，写入前只读确认12列、record_id主键、0行。
- 初次同组59项回归中新增DDL保护及Addax/guard45项通过，两个既有staged DAG版本命名用例失败：重复追加_task_ID。577a569a0保留已含任务编号的修订DAG身份；AirflowDagServiceTest重新14/14 PASS、BUILD SUCCESS（/tmp/s104-file-dag-guard-retest.log）。两个私有符号GitNexus impact返回UNKNOWN，已按源码定位准确调用入口；detect_changes为LOW。
- 最新正式构建日志/tmp/s104-file-dag-guard-build.log。文件任务#6将通过正常编辑生成新修订绑定隔离目标，旧修订/执行历史保留；新版部署后才执行，不调整数据库内容绕过门禁。

## ODS 目录层级适配修复

新隔离ODS通过数据页登记返回422 MODEL_SPEC_GOVERNANCE_ASSET_REGISTRATION_FAILED，failureType=CatalogAssetObservationException。静态定位为目录契约仅接受标准ODS层级，两个模型观察适配器优先发送了ODS_RAW细分代码。f1be305c4在质量前登记及发布观察使用ModelSpec.layer，细分warehouseLayerCode仍保留在模型，仅旧缺layer对象沿用原code回退；不修改目录枚举或存量记录。两处GitNexus impact为LOW，各1个直接调用入口。

CandidateQualityAssetRegistrationServiceTest 4项、ModelPublicationAssetObservationAdapterTest 6项共10 PASS、BUILD SUCCESS，覆盖ODS_RAW/ODS_STANDARDIZED和既有登记路径，日志/tmp/s104-canonical-layer-tests.log。577a569a0中间包未部署，最终统一交付f1be305c4，正式构建日志/tmp/s104-canonical-layer-build.log。

DWS当前delivery-status五项materialization/quality/publication/catalog/analysis均SUCCEEDED、matchesCurrentTarget=true，完整读取证据20260908-2a815d427-dws-delivery.json；分析准备没有重建模型。

## f1be305c4 统一正式交付

最终源码提交f1be305c43094447ab518c7016721ca611434dad，开发目录已提交/推送，部署目录干净工作区ff-only拉取同SHA，正式builds/dts-build.sh四镜像及748M升级包退出0。包data/sprint104-release/remediation-f1be305c4.tar.gz的SHA256=d1bc6793e5e965f86d949a6473fb8f2a7bbb6d007163a7c6f1375cc28574540e。归档摘要、四镜像sourceRevision/本地ID、dts_schema_only宏核验全部通过，见20260908-f1be305c4-package-proof.json。

| 服务 | 镜像tag | 最终image ID |
|---|---|---|
| platform | dts-platform:s104-f1be305c4 | sha256:a85e3e317d823f060d221f683939450d8c47a6f9ebcc39b2e556497732c59c80 |
| analytics | dts-analytics:s104-f1be305c4 | sha256:992c976e5f509994bb3d76b82cd1aafe01220390059fe5b11c86e7363963a6ad |
| ingestion | dts-ingestion:s104-f1be305c4 | sha256:42981b9b3591813edbb19b0f596f16c2c7918958aff03786963e08ae5cce6a8a |
| webapp | dts-platform-webapp:s104-f1be305c4 | sha256:0e064997d033affaef34c61ad8d1b3f47627c5a10f9487c72fd4c1f0241f7c58 |

构建/tmp/s104-canonical-layer-build.log、包校验/tmp/s104-canonical-layer-package-proof.log；原Compose项目deploy四服务定向更新/tmp/s104-canonical-layer-deploy.log，最终运行结果如下续记。

三个后端healthy、webapp running，ID与表一致。新ODS资产75ca5fce-6cad-4d0b-8b0d-35dc983ec417已出现，正常data-registration重放200，层级映射修复通过。文件任务#6首次编辑虽PUT成功，但/admit为HTTP200包裹业务status409，日志CLASSIFICATION_SEAL_STALE；UI没有跳转成功，未把HTTP200误记为准入成功。只读对比revision2→3的sourceConfig变化仅_filePath、_containerPath和_fileLanding，文件未变却被sourceIdentityChanged整体比较清空封存。

591d208c2对有_fileId/_fileHash的托管文件仅排除这三个非来源证据字段；其他来源/类型/字段变化仍清空旧封存。GitNexus impact LOW，直接调用为updateInternal与saveDraftRevisionForActiveTask。公开update路径专项4/4 PASS（新目标/新文件/字段变化3项、既有密钥不复制1项），/tmp/s104-file-seal-focused-tests.log。

完整IngestionTaskServiceTest当前92项中26失败4错误；使用Git工作树检出的已推送基线f1be305c4，在部署目录data/sprint104-acceptance/baseline-f1be305c4正式Maven重跑89项，同样26失败4错误。失败方法与各参数次数完全一致，详见20260908-591d208c2-baseline-comparison.json；这些旧准入/草稿夹具失败保留为基线问题，不宣称整个类通过，也不为此放松业务规则。基线日志/tmp/s104-file-seal-baseline-tests.log，当前完整日志/tmp/s104-file-seal-tests.log。

f1be305c4文件正常重开编辑/保存后，生成并准入revision5（checksum adaaf6371d74155b305fc0ed2c537aa3ee3b7921f8696caab8ad68e8bd6e476b），随后页面立即执行14成功。DAG=ingestion_revision_5_execution_14_task_6，run=manual__2026-09-08T00:53:02.328501+00:00；08:53:28后台同步SUCCESS，页面刷新SUCCESS。只读实表两行s104_0908_001/125.50、s104_0908_002/260.00，仍12列；生成DAG没有init_target_table/CREATE/ALTER，审计destructive=false。页面运行行数暂未回填（显示“-”），实际行数以只读实表为证；可空技术列无自动DDL默认值，当前值为空，运行溯源保留在任务执行审计。没有手工更改表或候选。

591d208c2构建日志/tmp/s104-file-seal-build.log；交付包已生成，尚未完成该提交的包核验、部署和页面验收。第二批文件按用户“先调用Chrome做页面测试”的要求，先在当前f1be305c4运行环境执行，结果见下。

## 09:05–09:08 Chrome95 页面复验

使用已有xiezm登录会话，实际UA为HeadlessChrome/95.0.4638.0。本轮运行版本仍为f1be305c4，不将结果归于尚未部署的591d208c2。

- 文件任务#6：正常页面保存第二批remediation-input-batch2.csv并生效，确认框明确目标public.ods_raw_s104_file_guard_0830及“追加写入已有模型表（不清空、不重建）”。点击确认执行后，运行历史及重新加载的详情均显示SUCCESS。run=manual__2026-09-08T01:05:50.073969+00:00，09:05:46开始、09:06:00结束。有效配置校验前缀c7bc42373862。
- 只读事务核验：原001/125.50、002/260.00保留，新增003/312.00、004/480.50，共4行；仍12列，无id列，PRIMARY KEY(record_id)保留。未通过SQL修改业务数据或表结构。
- 新ODS资产维护：页面填写负责人xiezm及“Sprint104 Chrome95双批追加接入验收，人工治理说明。”，governance-summary PATCH200；重新加载后编辑区与目录均保留说明。
- DWS数据页：最终稳定状态PUBLISHED、模型已完成；已发布质量规则、人工负责人及说明保留。
- 已捕获页面错误事件为空；本轮成功业务操作不代表全部Sprint分支通过。

本轮发现的页面问题：

1. 成功执行的读写行数仍为空/“-”，实际行数须以只读实表核验，尚未修复。
2. 候选数据加载时短暂出现“当前计划候选不包含所选模型”及规则只读提示，加载完成后ODS恢复BUILT、DWS恢复PUBLISHED；不能把加载态当业务不匹配结论。
3. 截图可见资产目录上方模型完成、质量规则、发布和治理区域采用密集原生文字布局，与下方目录卡片样式不一致；功能可用不等于页面视觉验收通过。

截图：20260908-0907-chrome95-file-batch2.png、20260908-0907-chrome95-asset-maintenance.png、20260908-0907-chrome95-dws-state.png。本轮仅确认上述路径；591d208c2的一次编辑封存保留、其他负向分支及独立离线环境仍待验收。
