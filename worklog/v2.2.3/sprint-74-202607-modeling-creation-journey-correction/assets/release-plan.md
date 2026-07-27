# Sprint-74 发布与回滚计划

**状态**：READY  
**变更类型**：expand + application switch；不删除历史 ModelSpec 字段

## 发布顺序

1. 备份 `dts_platform`，记录当前 `databasechangelog`、ModelSpec/Implementation revision 数量。
2. 部署后端，应用：
   - `20260727-05-model-spec-reclassification-command`
   - `20260727-06-warehouse-plan-governance-policy`
   - `20260727-07-model-implementation-settings-contract`
3. 确认 `/management/health=UP`，用只读请求验证 model detail、stage-gates 和 implementation view。
4. 部署 webapp；Chrome95 验证创建、详情三阶段与改型 preview。
5. 先执行 compatibility dry-run；只有操作者审阅 eligible/conflict 清单后才允许显式 apply。
6. 观察一轮模型创建、实现保存、compile 与 release-gate 日志后再放开改型操作。

## Go 条件

- clean PostgreSQL Testcontainers migration PASS；
- 存量库 changeset 05/06/07 已登记；
- 后端、webapp 容器健康且镜像对应本次 source；
- 真实 API P95 < 1s；
- Chrome95 3/3 PASS；
- compatibility dry-run 不自动 apply；
- release result 无 physicalAssetRef 时不展示资产。

## No-Go 条件

- 任一 changeset 失败或约束与 canonical payload 不一致；
- 旧 snapshot 无法读取；
- DESIGNED 被来源/实现/治理缺口错误阻断；
- ModelSpec 与 ModelImplementation 同时成为实现设置新写 owner；
- 改型覆盖历史 revision、幂等重放新增 revision，或 stale ETag 未返回冲突；
- UI 出现英文原始 blocker、伪造资产或把 dbt 放回发布结果页。

## 回滚边界

- 应用回滚：可回退到上一镜像；05/06 的 expand schema 可保留，不影响旧应用读取。
- changeset 05：仅当 command ledger 为空时允许 Liquibase rollback；有任何改型记录即 fail closed。
- changeset 06：仅当所有策略仍为旧默认 `ALL_FIELDS/BLOCKING` 时允许 rollback；已有新策略数据即 fail closed。
- changeset 07：forward-only。implementation revision 可能已使用新增 settings，禁止数据库函数回退。
- compatibility migration：按 dry-run checksum 定位批次；只删除该批次创建且未被后续使用的 implementation revision，始终保留旧 snapshot 兼容读取。
- 不通过 SQL 手工改写 ModelSpec head/revision/checksum。

## 回滚后验证

1. health UP；
2. 历史 ModelSpec revision 可读；
3. stage-gates 仍 fail closed；
4. current implementation pin 未变化；
5. release candidate/lifecycle 事件未删除；
6. 审计和 reclassification command ledger 仍可追溯。
