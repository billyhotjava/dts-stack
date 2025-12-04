# main_todo（更新于 2025-11-16）

## 基本原则
- **优先级顺序**：1) 人员主数据；2) BI 平台；3) 其他平台能力（合同/项目/PLM/QMS 等域、数据治理配套）。
- **讨论先行**：每个功能或改动在编码前先与客户确认范围，防止需求跑偏。
- **日志合规**：所有业务/安全操作继续写入审计日志；纯运维脚本输出到独立日志文件，按 100 MB 分片滚动（超出立即新建）。需要在实施阶段补上自动轮转脚本及监控。

## 近期 ToDo（按优先级）

### 1. 人员主数据（P0）
**当前进度（2025-11-17）**：完成导入能力第一阶段并接通院端回调：支持 API/Excel/手工 + MDM 回调（IP 直连），人员全量自动落库，组织树（deptCode/parentCode）自动建模并可同步 Keycloak 组，落盘与审计日志已就绪。下一步聚焦数据校验、Keycloak 属性补写、Excel 模板固化。
1. **数据接入设计**  
   - 明确院级 API & Excel 两种来源的字段映射、认证方式、调度频率。  
   - 设计双通道校验与冲突解决策略（API 与 Excel 数据冲突时的优先顺序、人工审批流程）。
2. **落地与存储**  
   - 已落盘 MDM 回调文件，人员/组织直接入库；后续需要补“院原始层 + 所扩展层”表结构与溯源字段、批次号、数据状态。  
   - 开发 ETL/导入任务：抓取 → 校验 → 写入原始层 → 触发清洗/扩展逻辑。  
3. **Keycloak 同步**  
   - dts-admin 增加 `dept_code` 等属性管理接口、批量补写脚本（待做）。  
   - Keycloak 客户端配置 Attribute Mapper，保证 Token 暴露 `dept_code`，BI & 其他应用可读取（待做）。  
4. **监控与日志**  
   - 为 API、Excel 导入、清洗、同步链路添加统一日志分类，纳入 100 MB 轮转策略。  
   - 配置失败告警、重新执行工具，并记录操作审计。  
5. **角色 + 密级整合**  
   - 建立 role_template/role_mapping，覆盖数据管理员、开发员、所/部门领导（ROLE_INST_LEADER / ROLE_DEPT_LEADER）及普通员工（ROLE_EMPLOYEE），并与授权流程打通。  
   - 开发角色同步任务：本地角色 → Keycloak Client Role/Group，记录状态与失败日志，确保领导权限 ≥ 对应管理员，普通员工仅具备 BI 访问。  
   - 调整授权/共享工作流，校验“角色 + person_security_level ≥ 资源 security_level”，同时把领导/员工可视范围映射到 BI（Superset/Metabase）RLS。

### 2. BI 平台（P1）
1. **组件选型落地**  
   - 定版 Superset/Metabase + Airflow + dbt 组合，并写明部署与网络/安全要求。  
   - 拟定容器化/Compose 清单，定义镜像来源与加固措施（`services/dts-superset/`、`services/dts-metabase/` 目录、镜像 tag 写入 `imgversion.conf`，挂载配置、证书、日志路径）。  
   - 元数据数据库选型 PostgreSQL，准备迁移/备份策略；禁用匿名、配置只读连接池，限制外网访问。
2. **SSO & PKI 集成**  
   - 通过 dts-admin 统一登录入口接入 Keycloak OIDC/PKI，验证 BI 前端认证链路；补 HTTPS 反代与信任链。  
   - 定义角色/行列权限映射，确保 `dept_code`、岗位、密级等属性可在 BI 中使用；统一组/角色命名，单点到 Superset/Metabase 客户端角色。
   - 按 100 MB 轮转配置 BI 应用日志与访问审计，确认容器挂载路径与 logrotate 生效。
3. **人员主题仪表盘 MVP**  
   - 选取 2~3 个所需指标（编制 vs 在岗、岗位分布等）作为压测用例。  
   - 建立调度 → 数据集 → 仪表盘全链路并记录运行日志。
4. **Superset 落地**  
   - Compose 服务定义：`SUPERSET_SECRET_KEY`、`SUPERSET_DATABASE_URI` 指向内网 PostgreSQL，启用 Celery/Redis 如需异步查询；挂载 `superset_config.py`，关闭 demo，限制注册。  
   - OIDC：配置 Keycloak Client，启用 `AUTH_REMOTE_USER`/OIDC Auth，映射 `dept_code`/`person_security_level`/角色到 RBAC + RLS（基于自定义 Security Manager 或 Row Level Security Rules）。  
   - 数据源：接入星环 Inceptor（Hive/SQL），使用只读 schema 账号，开启 SQL Lab 禁止危险命令（`ALLOW_DML=False`），预建角色/数据库级过滤模板；准备 JDBC 驱动挂载。  
   - 运维：导出/导入 dashboards & datasets 版本库；配置健康检查、备份 metadata；日志轮转与审计挂载到宿主。  
5. **Metabase 落地**  
   - Compose 服务定义：`MB_DB_FILE`/PostgreSQL 元数据库，禁用匿名，限制新用户注册；挂载 `metabase.env`；配置 HTTPS/反代。  
   - OIDC/SAML：Keycloak Client 映射组/角色到 Metabase 组，使用 `dept_code`/密级作为 Segment/RLS 参数；Inceptor 数据源使用只读账号。  
   - 内容治理：预设集合/权限矩阵，固化审计查询模板；开启查询超时与结果缓存策略。  
   - 备份与日志：定期备份元数据库；访问/审计日志归档并轮转 100 MB。

### 3. 其他能力（P2）
1. **数据治理 & 资产目录**：在 dts-platform 中实现标准管理、审批流程（含数据集共享授权工作流）、血缘与质量监控，按模块拆解。  
2. **业务主数据扩展**：项目、合同、PLM/QMS、财务指标等按域分批接入，复用人员主数据的流程。  
3. **安全 & 运维**：完善 IP 白名单、PKI 审计、HA/容灾、日志轮转脚本、容量监控等。

## 日志策略实施步骤
1. 盘点现有审计日志与运维日志输出位置，补上缺失的分类与字段。  
2. 选定 logrotate 或自研脚本，设置单文件 100 MB 上限，命名规则例如 `<name>-YYYYMMDD-HHMM.log`.  
3. 将轮转脚本纳入运维手册，并在 dts-admin 内记录执行操作。  
4. 定期（每日/每周）汇总日志摘要写入 worklog，便于追踪。

> 后续所有待办会继续追加到本文件；每次更新同时在会话内说明，并在实施前复核需求。
