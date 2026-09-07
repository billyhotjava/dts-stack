# Sprint-104 发布安全计划

状态：G3 GAP（正式镜像及回滚演练待完成），不得据此关闭 Task。

变更：SOURCE/ODS 类型、SCHEMA_ONLY 生成器、三步建模、数据登记与目标绑定。风险中；物化结果探测共享函数影响分析 HIGH，已执行完整定向回归 27 项通过。

## 数据及兼容边界

- 无数据库迁移、回填、存量数据清理。SOURCE 和 modelTarget 使用既有字符串/JSON 存储。
- 用户触发结构物化只创建新普通表；已有目标返回冲突，禁止 DROP/TRUNCATE/ALTER。接入绑定仅写入符合已声明结构的数据。
- 旧请求 buildMode 缺省 DATA_BUILD；delivery 深链继续只读。新增建模结果与数据动作字段均为追加。
- 消费方：平台 webapp 的 modelBuildIntentApi、ModelWizardFrame、ModelDataOperationsPanel；接入的 PlatformInfraClient、TargetTableProvisioner、AddaxJobService、ApiRawLandingService。
- 数据登记从物化事务移至显式数据操作，沿用 CandidateQualityAssetRegistrationService 唯一资产身份。已有质量、发布和分析命令仍保留权限、版本及运行证据门禁。

## 正式发布

开发 commit/push → deploy 干净分支 pull --ff-only 核对 SHA → 定向测试 → builds/dts-build.sh --image dts-platform dts-ingestion dts-platform-webapp → 正式交付包 → 同一 Compose 项目部署 → Chrome。

部署目录 /opt/prod/s10/deploy；配置 docker-compose-app.yml；项目 deploy。依次发布平台、接入、前端，不迁移现有挂载与持久卷。dts_schema_only.sql 通过现有受管理 dbt macros 交付，平台作用域工作目录沿用 DbtScopedProjectService 正式复制逻辑。

## 回退及演练

发布前保留以下旧镜像，禁止清理：
- platform sha256:ec9ff95e31ac7b2ee9ed538388e8d3362d41c240c1f72c6646f9dfa7c411d1c2
- ingestion sha256:15ca2e9d91378739c8fa90604fcb263f496c32e92893ced957a4238f54c193e3
- webapp sha256:a7c1f0f85ec57a32cf835da0b5857e8cae8faf966fad177b2e74d2db0afd1060

演练：在新模型/绑定写入前，使用上述精确镜像 ID 覆盖 Compose IMAGE_DTS_* 环境变量，执行同一配置的 up -d --no-deps 对应服务；验证健康后再切回新 SHA 镜像。实测结果待补。

旧程序不认识 SOURCE，也不保护 modelTarget。产生新类型或绑定后不得直接回退旧程序；使用正常 Git 前向修复，保留业务数据及运行证据。不通过数据库删除或改状态制造回退条件。

## 当前验证

18533ca3e：后端 57 项通过（含物化 27、交付状态 26、数据登记 1、结构生成 3）；前端 21 项通过。aa4d60357：前端模型服务 62 项通过；最新后端回归待完成。正式构建、容器、真实页面验收尚未完成。
