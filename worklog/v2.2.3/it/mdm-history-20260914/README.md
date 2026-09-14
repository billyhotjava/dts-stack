# MDM 重复导入历史与批次事务修复

日期：2026-09-14。当前版本修复，不是六月初现场分支的交付包。

## 问题与修复

- `person_import_record` 是导入历史，重复同步可合法引用同一个 Keycloak ID。旧的单列唯一约束会拒绝新增历史记录。
- 新增 `20260914-01_person_import_history_constraints.xml`，在 PostgreSQL 当前 schema 内按字段定义移除单列 `keycloak_user_id` 全表唯一约束及独立唯一索引，保留/创建普通索引。不修改历史迁移，不执行记录更新、置空或删除；不删除主键、批次外键、复合约束，不影响其他 schema。
- 迁移使用 5 秒锁等待上限。依赖阻止删除、结构缺失或索引名称冲突时失败退出，不使用 CASCADE。默认事务回滚本次迁移变更。
- `PersonnelImportService` 改为 `NOT_SUPPORTED`，挂起调用方事务。批次通过仓库事务先提交，明细继续使用现有 `REQUIRES_NEW`，避免明细看不到批次导致外键失败。终态批次通过仓库事务保存。
- Keycloak 调用仍是外部操作，不能通过业务数据库回滚撤销；本次不宣称实现跨系统原子提交。

## 提交与范围

- 修复提交：`f9b1c6a20290119785c33c0378cdb5e7b07737c8`，仅包含服务事务注解、新迁移及主清单三文件。
- 回归断言修正：`795355fb3dd36ea8c568340c50ca980671bb8ee4`。批次成功终态为 `COMPLETED`，不是明细状态 `SUCCESS`。
- 最初新增测试在 `ce90a3b9a`。该提交因共享暂存区并行写入混入其他任务文件，已经推送并保留；后续回移测试应仅选取 `PersonnelImportHistoryRegressionTest.java`，不要整提交 cherry-pick。
- 工作区其他改动未回退；后续六月初分支本轮未创建。

## 源码回归（发布前）

正式 Maven 入口在 `/data/dts-stack` 下执行。共享构建根目录有并行任务生成 root 所有者产物，初次测试遇到权限错误；修复验证改用 Git 隔离检出：

`/data/dts-stack/.worktrees/mdm-f9b1c6a20`

该检出已通过 `git pull --ff-only origin 795355fb3dd36ea8c568340c50ca980671bb8ee4` 快进至最终测试 SHA，没有复制未提交源码。

```bash
mvn -B -f source/pom.xml -pl dts-admin -am -Dskip.npm \
  -Dtest=PersonnelImportHistoryRegressionTest,PersonnelImportServiceTest,KeycloakUserProvisioningServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false -DskipITs package
```

- 修复前：新增回归 6 项，事务场景 2 项复现外键失败；4 项因修复迁移尚不存在报错。
- 修复后：8 项通过，0 失败、0 错误、0 跳过；Maven `BUILD SUCCESS`。
- PostgreSQL 17.4 Testcontainers 验证：小写约束、多 schema 导致旧迁移 `MARK_RAN`、大小写混合约束、独立唯一索引、已取消约束的数据库。
- 四种迁移场景各保留 8,000 条历史记录，迁移前后完整行内容摘要一致；重复运行升级不重复变更。
- 验证同一 Keycloak ID 可新增另一批次历史；主键、外键、复合约束和其他 schema 唯一约束保持有效。
- 服务通过真实 Spring 事务代理及 PostgreSQL 验证调用方事务回滚后已提交的批次和明细仍在、同用户重复导入成功、单条写入失败可记录失败并继续后续记录。
- 服务测试的仓库适配使用 JDBC 执行真实 SQL/事务；Keycloak、快照仓库与审计使用 mock。没有执行真实 Keycloak 或页面验收。
- 单次集中静态检查：GitNexus 影响等级 LOW；四个导入入口受服务事务边界影响。提交范围、XML、主清单引用、差异空白检查通过。

## 构建产物

- JAR：隔离检出的 `source/dts-admin/target/dts-admin-2.2.3-SNAPSHOT.jar`。
- SHA-256：`07327c3240dcacae127f7581d5705ea7100d0c40a91ca777ed8b4ef0abdcf9dd`。
- 已核对包内新迁移与检出源码逐字节一致，且包内主清单引用新迁移。
- 完整日志和清单位于同一 target 的 `mdm-verification/`。
- 上一阶段完成源码回归及 JAR 构建；本机镜像发布和联调结果见下节。未修改现场数据库或重放现场人员数据。

## 本机模拟器与容器联调（2026-09-14 11:24）

结论：真实模拟器 → admin 回调 → JSON 解析 → 部门同步 → Keycloak 人员同步 → 导入历史保存，两次连续同步通过。

### 运行配置与发布

- 以 Docker Compose 标签和挂载确认当前实际运行目录为 `/data/dts-stack`、项目 `dts-stack`。`/opt/dts/release/dts-stack` 本机不存在，未迁移运行环境。
- 发布前 admin 运行旧 SHA `4d9fecbe3e2613e4625b8bf04da3165bae813ee0`，未包含本修复。容器内上游配置为 `http://localhost:28080`，不能连接宿主机模拟器；旧模拟器进程的样例路径也已不存在。
- 仅更新运行目录 `.env` 的三项：`DTS_MDM_GATEWAY_UPSTREAM_BASE_URL=http://172.19.0.1:28080`、`DTS_MDM_GATEWAY_CALLBACK_URL=http://172.19.0.1:38012/api/mdm/receive`、`DTS_MDM_GATEWAY_STORAGE_PATH=/data/mdm`。保留鉴权和其他配置。
- 构建检出再次 `git pull --ff-only origin 795355fb3dd36ea8c568340c50ca980671bb8ee4` 并核对 SHA。执行 `builds/dts-build.sh --image dts-admin` 正式入口，脚本默认预构建阶段重新 Maven 打包，随后构建并导出原标签 `dts-admin:1.0.0`。
- 通过实际运行目录的 `docker-compose-app.yml`，指定原项目名、`.env`、`imgversion.conf`，执行 `up -d --no-deps --pull never dts-admin`；仅更新 admin，保持原挂载和网络。容器健康。
- 新迁移 `20260914-01-person-import-history-constraints` 于 11:23:51 `EXECUTED`。同版本还包含两项待执行的建模审计/菜单角色迁移，已随正常启动执行。
- 发布前已保存本机 `dts_admin` 数据库备份、旧 admin 镜像和原 `.env`，位于证据目录；不包含现场数据库。配置备份含凭据，仅本机受限目录保留，不提交 Git。

### 样例与断言

- 模拟器使用同一 SHA，在构建检出中 Maven 打包后重新启动，监听 `172.19.0.1:28080`，PID `359295`。页面：`http://172.19.0.1:28080/`。
- 样例固定一名人员 `MDMTEST20260909U01`、一个部门 `MDMTEST20260909D01`。每次更新 `desp.sendTime` 以保留独立原始接收文件。测试账号保持禁用登录。
- 两次均调用正式 admin 的 `POST /api/mdm/handshake`；模拟器接收拉取请求后通过 multipart 回调 `/api/mdm/receive`。上游与回调均 HTTP 200，日志两次 `users=1 depts=1`，接收文件 MD5 与批次 metadata 一致。

| 次数 | 批次 ID / 状态 | 明细 ID / 状态 | 成功 / 失败 |
| --- | --- | --- | --- |
| 1 | 1601 / COMPLETED | 1701 / SUCCESS | 1 / 0 |
| 2 | 1602 / COMPLETED | 1702 / SUCCESS | 1 / 0 |

- 两条明细的 Keycloak ID 相同：`3c526739-b43c-41e3-9422-cc4a2a0c0519`。Keycloak 数据库确认只有一个测试账号，`enabled=false`；首次历史在第二次导入后保持不变。
- 部门按 `dept_code` 查询仅一条，ID `1551`。首次验证脚本误用 `org_code` 导致断言失败，已用正确字段只读补验，没有再次推送。日志 `applied=6` 是现有组织循环的处理次数，不代表生成六个部门。
- 主键和批次外键仍在，`keycloak_user_id` 保留普通索引；两次异步导入均 `success=1 failed=0`，无批次外键或重复历史唯一约束失败。
- 本机测试前批次/明细为 0，且六月迁移已取消旧唯一约束。本次证明真实链路可重复导入；旧约束修复与 8,000 条存量保留由上节 PostgreSQL 回归覆盖。现场六月初版本与存量库仍需独立验证。

### 发布标识与证据

- 源码 SHA：`795355fb3dd36ea8c568340c50ca980671bb8ee4`。
- 镜像 ID：`sha256:2cc982f86253b5cb928e5e2f165c5e55f27551c0b5e21d1b7bbf7ac0acdbfc0c`。
- 正式脚本重建 JAR SHA-256：`8c1f9913f08db7113562e03dcab32325573e7b14af930966e93e3d25b30a05d7`。镜像内 `/app/app.jar` 哈希一致，新迁移及 master 引用已核对。
- 镜像归档：`/data/dts-stack/.worktrees/mdm-f9b1c6a20/builds/dist/dts-admin_1.0.0-20260914-112100.tar`。
- 镜像归档 SHA-256：`026316fc36b03d1a032f108844c2c52fcfeb0753c650b7b34290ba0f6329fc8a`。
- 原始证据：`/data/dts-stack/data/mdm-integration-20260914/`，包括 `release-manifest.json`、`result.json`、两次握手/接收文件/数据库结果、迁移结果、网关日志及镜像/模拟器构建日志。
- 本轮完成正式 admin 镜像构建与导出、现有容器发布、模拟器真实 API/数据库联调。未生成完整产品离线交付包，未做登录后的业务页面验收，未部署现场。

## 后续现场分支处理

从确认的六月初交付 SHA 单独建分支，重新核对当时导入代码、数据库类型、schema、实际唯一约束/索引及 `databasechangelog`。当前迁移针对 PostgreSQL；其他数据库不能直接套用。

保留现场导入记录、批次、账号关联及原始文件，备份并在恢复副本验证后再发版。若迁移元数据缺失，先盘点待执行历史迁移，不能让旧的置空去重步骤被盲目重跑。

取消唯一约束后允许出现合法重复历史，不能通过重新添加旧唯一约束回滚；新迁移的 rollback 明确拒绝该操作。需要回退时停同步并采取兼容的前向修复，不删除历史来满足旧约束。
