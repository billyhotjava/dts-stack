# 发布安全计划（Gate G3）

**变更类型**：API + 数据结构 Expand + 兼容迁移 + UI 能力替换  
**风险等级**：高（模型创作 command boundary 与存量草稿/实现兼容）  
**当前状态**：PENDING，回滚演练前不得 PASS

## 1. 迁移策略

| 阶段 | 内容 | 本 Sprint | 回滚边界 |
|---|---|---|---|
| Expand | 在 `modeling_dbt_implementation_draft` 增加可空 `model_spec_snapshot`、`projection_summary`、`authoring_origin`；新增 authoring facade；旧 API 保留 | 是 | 旧代码忽略新列；新路由可不暴露 |
| Migrate | dry-run 统计后，按 draft ID 分批补 provenance/projection；COMMITTED/过期草稿只补可证明字段 | 是 | 每批带 migration batch id/报告；按批恢复原 null 值，拒绝漂移行 |
| Contract | 删除 `implementationMode`、ownership transition、convert 路由或旧 dbt resource | 否 | 需独立 Sprint 和真实调用量证据 |

新增列首轮保持 nullable；若后续要加 NOT NULL/check constraint，必须在全量对账通过后的独立 changeSet 执行。

## 2. 兼容性

| 消费方 | 证据 | 影响 | 处置 |
|---|---|---|---|
| 当前模型工作台 | Sprint-91 F1～F4 | UI 行为替换 | 新 frontend 仅调用 authoring facade；旧 frontend 仍可调用旧后端 |
| `/dbt-drafts` 客户端 | `DbtImplementationDraftResource` | 路由保留 | 委托同一 service/repository；响应字段只增不删 |
| ownership transition 客户端 | `ModelLifecycleResource` | 产品语义被替代 | 兼容期保留并记录 deprecation/审计；不再造成 visual 整页只读 |
| `convert-to-designer-generated` 客户端 | `ModelLifecycleResource` | 存在未知消费 | 不删除；F5/T01 取得调用分母后再决定 Contract |
| release/materialization | Sprint-91 F5/F8 | 不应感知 UI 来源 | 保持 model/implementation/dependency pins 和 API 不变 |
| Sprint-93 治理 | AssetKey/serving/quality/lineage | 不应感知 provenance | 回归稳定身份和二次物化 |

## 3. dry-run 与分批补齐

1. `preview` 报告只输出数量、ID、状态、可判定 provenance、projection coverage 和冲突原因，不输出 SQL 正文。
2. apply 默认每批 100 个 draft；按 `(last_modified_date,id)` 游标推进，不用 offset。
3. 仅当 base pins 与 preview 一致时更新；漂移、缺 bundle、无法判定全部进入 unresolved 清单。
4. 不自动改写 ModelSpec/implementationMode，不复活过期草稿，不修改 COMMITTED receipt。
5. rollback 按 batch id + before checksum 恢复；发生后续用户修改时拒绝盲回滚。

## 4. 部署顺序

1. 备份/记录 migration preview、旧 API smoke 和当前镜像标签。
2. 部署 nullable Expand migration。
3. 部署后端：新 facade + 旧路由兼容 adapter；保持旧前端可用。
4. 执行后端契约和旧 REST smoke。
5. 部署新 `dts-platform-webapp`，启用统一工作台。
6. 执行现代 Chrome smoke；全部编码完成后执行集中 Chrome95/E2E。
7. 观察错误率、冲突率、COMMITTING 卡顿和旧路由调用；通过后再分批 Migrate。

## 5. 回滚

- UI 异常：先回滚 webapp 镜像；旧后端路由仍兼容。
- 新 facade 异常：回滚后端镜像；nullable 新列保留，不影响旧代码。
- 迁移异常：停止批次，按 batch report 和 before checksum 回滚；不 DROP 新列。
- 已产生的新 ModelSpec/implementation revision：不删除，标记为失败/废弃并沿既有生命周期显式处理；不得回写历史 PUBLISHED。
- 不可逆部分：已生成的审计记录和历史 revision；它们是证据，只允许追加纠正记录。

## 6. 演练门槛

- [ ] clean DB apply → rollback → re-apply。
- [ ] old backend + expanded schema、new backend + old frontend、new backend + new frontend 三组兼容 smoke。
- [ ] 100 条 dry-run/apply/rollback，含漂移、过期、COMMITTED、缺 bundle。
- [ ] 新 commit 后回滚 frontend/backend，旧页面仍能读取模型且候选/物化不丢失。

## 7. 2026-09-03 模型删除与归档语义增补

- `DELETE /model-specs/{id}` 保留原路由，但只允许永久删除匹配强 `If-Match` 的草稿；删除模型元数据、版本及未提交的创作草稿，不删除数据源、已存在的物理表或已提交交付证据。
- 新增 `POST /model-specs/{id}/archive`；仅用于非草稿、非已归档模型。默认目录排除已归档模型，显式选择“已归档”仍可查询。
- 后端先行、前端后行：旧前端仍可调用原删除路由，但后端的状态、引用、提交证据与数据库外键防线会拒绝不安全删除；新前端将“删除”和“归档”拆成两个入口。
- 审计动作目录以前向 changeSet 增补“归档模型定义”；删除与归档分别记录审计动作，不修改既有 changeSet。
- 回滚代码可恢复旧接口行为，但已永久删除的草稿元数据不可由代码回滚恢复。因此永久删除前必须二次确认，并以草稿状态、强 ETag、活跃引用检查和提交证据外键作为不可逆门禁。
- 存量误归档记录只允许按精确 ID 做一次性修复：执行前导出目标行及从属行，事务内复核状态、版本、校验和及引用计数；发现漂移立即停止。该修复与本次代码发布分开验收。

本增补在“后端/旧前端、新后端/新前端兼容 smoke、Chrome95 页面验收、不可逆草稿删除演练”完成前保持 **PENDING**。
