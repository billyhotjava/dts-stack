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

## 验证

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
- 本轮完成源码回归及 JAR 构建；未构建镜像、未生成完整离线交付包、未部署容器、未修改现场数据库或重放现场人员数据。

## 后续现场分支处理

从确认的六月初交付 SHA 单独建分支，重新核对当时导入代码、数据库类型、schema、实际唯一约束/索引及 `databasechangelog`。当前迁移针对 PostgreSQL；其他数据库不能直接套用。

保留现场导入记录、批次、账号关联及原始文件，备份并在恢复副本验证后再发版。若迁移元数据缺失，先盘点待执行历史迁移，不能让旧的置空去重步骤被盲目重跑。

取消唯一约束后允许出现合法重复历史，不能通过重新添加旧唯一约束回滚；新迁移的 rollback 明确拒绝该操作。需要回退时停同步并采取兼容的前向修复，不删除历史来满足旧约束。
