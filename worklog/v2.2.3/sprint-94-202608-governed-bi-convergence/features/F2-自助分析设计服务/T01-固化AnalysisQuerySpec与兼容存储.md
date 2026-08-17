# T01：固化 AnalysisQuerySpec 与兼容存储

**优先级**：P0
**状态**：DRAFT
**依赖**：F1/T01

## 可测试目标

后端可创建、读取、更新、复制、归档和恢复 `dts.analysis/v1` Analysis；数据钉定平台数据集契约，保存幂等、并发更新受控，旧 Card 仍可读，而新写路径不能提交 MBQL/raw SQL。

## 契约与持久化

- 按 F2 README 固化 JSON schema、validation/errorCode 和 `AnalysisDto`。
- 复用 `analytics_card`；Expand 字段：`query_dataset_id`、`query_dataset_version`、`semantic_contract_version`、`lifecycle_status`、`published_revision_id`；`card_type='analysis'`。
- migration 顺序：add nullable/index → backfill 可证明行 → validate → 后续 Sprint 再考虑 contract；rollback 只回应用版本，不删除新列/历史。
- 新 `/api/analysis` facade 委托现有 repository/revision seam；旧 `/api/card` 不新增写能力。
- 对旧 payload 分类：可映射的兼容读、legacy-read-only、invalid；不得用名称猜 dataset identity。

## 错误、并发与审计

| 场景 | 结果 |
|---|---|
| malformed/超上限/raw SQL/MBQL | 400，稳定字段路径 |
| 无 read/write | 403 |
| dataset/version/checksum 漂移 | 409 |
| 字段/指标/表达式非法 | 422 |
| 乐观锁冲突 | 409，返回 currentVersion 摘要，不泄露 spec |
| 重复 idempotency key | 返回同一 Analysis，不产生重复 revision |

审计保存 actor、analysisId、datasetId/version、spec checksum、outcome/correlationId；不存业务值或 SQL。

## RED → GREEN

1. JSON schema 参数化 RED：未知 version、21 derived metrics、51 filters、limit 10001、raw SQL 被拒绝。
2. CRUD GREEN：create/update/copy/archive/restore；copy 不带 published pointer/受众。
3. 并发 GREEN：20 个相同 idempotency request 只生成一个实体；两个 version update 只有一个成功。
4. 兼容 GREEN：三类旧 fixture 正确分类，新写持久化 snapshot 无 MBQL/raw SQL。
5. migration GREEN：旧空表/旧 Card/混合数据均可启动和回滚应用。

## 影响范围与先决 impact

预计 symbol：Card entity/repository/service/resource、RevisionService、Liquibase、Platform access registrar。逐 symbol fresh impact；Card/Revision 若 HIGH，先固化兼容 facade contract，禁止重写旧 `/api/card` 调用方。

## Definition of Done

- [ ] schema、CRUD、幂等、乐观锁、审计和错误码测试通过。
- [ ] Expand migration 在旧/混合 fixture 可前滚，旧应用仍可读。
- [ ] 新写 JSON 中无 MBQL/raw SQL/凭据；dataset reference 完整钉定。
- [ ] 旧 Card API 回归通过，三类兼容状态可查询。
- [ ] 无新 Analysis 业务表或权限 owner。

