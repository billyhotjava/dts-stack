# T05: 集成测试（8 动作 / 越权 / 矩阵生效 / 生效期 / 不冲突）

**优先级**: P0
**状态**: READY
**依赖**: T04

## 目标

端到端验证操作权限矩阵：8 动作全覆盖、越权动作被 `canPerform` 拦截、矩阵配置生效、生效期边界正确，且与既有 RLS/FIELD 策略不冲突。

## TDD 测试先行（RED）

- 新增 `OperationPermissionMatrixIT`（Testcontainers + MockMvc，Spring Boot），放 `dts-platform/src/test/java/com/yuzhi/dts/platform/integration/`：
  - **8 动作全覆盖**：参数化对 CREATE/DELETE/UPDATE/COPY/IMPORT/EXPORT/ARCHIVE/DESTROY 各配一条 ALLOW 策略，断言对应入口放行；未配置时同动作被拒（默认拒绝）。
  - **越权拦截**：低权角色调导出/删除/归档/销毁入口返回 403 `asset_action_denied`，并产生审计记录。
  - **矩阵生效**：经 T04 审批流落库后策略即时生效，撤销后回到拒绝。
  - **生效期边界**：validFrom 未到 / validTo 已过 的 ALLOW 策略不放行；区间内放行。
  - **与既有策略不冲突**：同数据集叠加 `IamDatasetPolicy` 的 ROW(rowExpression)/FIELD 策略时，`canPerform` 仅判动作维度，行级裁剪与字段可见性仍由既有 `PolicyService`/`ExploreResource.isRowLevelAllowed` 生效，互不覆盖。

## 技术设计（GREEN）

- 复用 T01-T04 产物，不新增生产代码；仅补测试夹具（种子角色、数据集、矩阵策略）与断言。
- IT 证据归档到 `worklog/v2.2.3/sprint-36-202606/it/evidence/operation-permission-matrix/`（请求/响应 + 审计片段），对接 sprint-36 IT 准入（F6）。

## 影响范围

- 新增：`source/dts-platform/src/test/java/com/yuzhi/dts/platform/integration/OperationPermissionMatrixIT.java`
- 证据：`worklog/v2.2.3/sprint-36-202606/it/evidence/operation-permission-matrix/`
- 只读参考：`source/dts-platform/.../web/rest/ExploreResource.java`（RLS 不冲突断言依据）、`source/dts-platform/.../service/iam/PolicyService.java`

## 验证

- [ ] 8 动作各有 ALLOW 放行 / 无配置默认拒绝的成对断言。
- [ ] 越权动作 403 `asset_action_denied` 且审计留痕。
- [ ] 生效期边界与"撤销即拒绝"验证通过。
- [ ] 叠加 RLS/FIELD 策略时动作校验与行/列裁剪互不干扰。

## 完成标准

- [ ] 矩阵能力达成协议 2.3.2.5 操作权限验收点，IT 证据可供 F6 准入引用。
