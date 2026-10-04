# F4：统一校验提交与生命周期兼容

**优先级**：P0  
**状态**：IMPLEMENTED（原子提交与生命周期自动化通过；真实发布/物化 E2E 待验收）

## 目标

用一个原子 command boundary 校验并提交 ModelSpec、bundle、projection 和依赖，收敛旧 API 为兼容 adapter，并证明后续候选、构建、质量、发布、物化和治理不按来源分叉。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| Validate | `POST .../authoring-drafts/{draftId}/validate` | modelIssues/codeDiagnostics/projectionIssues/dependencyValidation + validatedChecksum |
| Commit | `POST .../authoring-drafts/{draftId}/commit` | expectedEtag、validatedChecksum、dependencyChecksum、idempotencyKey；返回 model/implementation pins |
| Transaction | ModelSpec revision + implementation/artifacts + receipt/audit | 单事务；失败无半提交 |
| Legacy | `/dbt-drafts`、ownership transition、convert | 委托同一 service/repository；响应只增不删；新 UI 零调用 |
| Security | `CATALOG_MAINTAINERS` + read/write/export | 技术正文/写动作 fail closed；审计 action 登记 |
| Lifecycle | existing release candidate/materialization/Sprint-93 seams | candidate pins 和 AssetKey 不含 provenance |

## UI/UX 规格

- 校验结果分“模型定义/实现代码/投影/依赖”四组，可定位文件/节点。
- 提交仅在最新 validation 对应当前 ETag 时启用。
- 409/412 保留本地草稿；同 idempotencyKey 相同 payload 展示已有成功回执。
- 生命周期按钮仍在现有区域，不因当前 view/provenance 改变。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 原子校验提交 ModelSpec bundle 与依赖 pins | P0 | IMPLEMENTED | F1/T03、F2/T03 |
| T02 | 收敛旧 API 权限幂等与审计兼容 | P0 | IMPLEMENTED | T01、F3/T02 |
| T03 | 回归候选发布物化与 Sprint-93 治理证据 | P0 | IMPLEMENTED | T01、T02、F3/T03 |

## 实施证据（2026-08-20）

- validate 统一返回 model issues、code diagnostics、projection issues 和 dependency validation。
- commit 在同一事务中写入 ModelSpec revision、implementation/artifacts、receipt 和 pins，并复用 canonical ModelSpec 校验。
- 旧路由保持兼容，新 UI 不再调用 ownership/convert。
- authoring 后端 164 用例、Spotless 及候选/发布/物化生命周期 9 类/89 用例通过；详见 `../../it/evidence/20260820-automated.md`。

## Definition of Ready

- [x] validate/commit DTO、事务和错误语义已钉死。
- [x] 旧 API 兼容而非删除的策略明确。
- [x] 后续 lifecycle/AssetKey owners 明确。
- [ ] F1/F2/F3 实现已就绪；F0 实时样本与真实生命周期 E2E 尚待执行。

## 完成标准

- [ ] 任一失败点均不留下 model/implementation/artifact/receipt 半状态。
- [ ] 权限、CAS、幂等、过期、dts-dbt 故障和审计测试通过。
- [ ] 旧 REST 可兼容，新 UI 不再调用 ownership/convert。
- [ ] 首次/二次物化和治理证据使用同一稳定身份且 provenance 无分支。
