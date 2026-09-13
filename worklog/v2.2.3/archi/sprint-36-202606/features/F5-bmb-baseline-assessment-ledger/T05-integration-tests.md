# T05: 集成测试：映射完整性/AUTO 判定/两轮流转/证据包

**优先级**: P0
**状态**: READY
**依赖**: T01, T02, T03, T04

## 目标

以端到端集成测试验证：BMB17.x 条款映射完整性、AUTO 检查项自动判定、两轮迭代状态机流转、证据包导出内容完整。

## TDD 测试先行（RED）

- 新增 `SecurityBaselineResourceIT`（放 `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/`，与既有 `*ResourceTest` 同包，使用 MockMvc + `@WithMockUser` 持 INSTITUTE 权限）。
- 映射完整性：`GET /api/security/baseline/checks` 返回项全部含 `clauseCode`/`evidencePointer`，类别覆盖口令/会话/操作权限/错误屏蔽/加密/审计/TLS。
- AUTO 判定：审计类 AUTO 检查项（原 `SEC_BASELINE_AUDIT_LOG` 对应条款）状态由系统自动判定，无需人工置位。
- 两轮流转：`PUT /api/security/baseline/checks/{checkKey}` 依次推进 NOT_STARTED→IN_PROGRESS→DONE，第一轮 OFFLINE、第二轮 ONLINE 留痕并存；非法跳变返回 400。
- 证据包：`GET /api/security/baseline/report` 返回 Markdown 含条款号、两轮整改与复测结论。

## 技术设计（GREEN）

- 复用既有 web 层集成测试基建（参考同包 `DirectoryResourceTest`/`EtlResourceTest` 的 MockMvc + 鉴权装配）。
- 校验未鉴权请求被拒（`INSTITUTE_PRIVILEGED_EXPRESSION`，`SecurityBaselineResource.java:26-27`）。
- 断言审计动作码 `SECURITY_BASELINE_CHECK_LIST` / `SECURITY_BASELINE_REMEDIATION_UPDATE` / `SECURITY_BASELINE_REPORT_VIEW` 仍被记录。

## 影响范围

- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/SecurityBaselineResourceIT.java`（新增）
- 证据沉淀：`worklog/v2.2.3/sprint-36-202606/it/evidence/bmb17-baseline/`

## 验证

- [ ] 映射完整性、AUTO 自动判定、两轮状态机流转、证据包内容四类用例全绿。
- [ ] 未鉴权访问被拒，审计动作码完整记录。
- [ ] 覆盖率 ≥80%，状态机与导出分支覆盖达标。

## 完成标准

- [ ] 集成测试套件证明 F5 条款映射、整改两轮迭代与证据包导出端到端可用。
