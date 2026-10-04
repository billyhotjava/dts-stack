# T03: IT 证据归档到 it/evidence/

**优先级**: P0
**状态**: READY
**依赖**: F1-F5

## 目标

把 F1-F5 的集成测试证据按机密级测评要求归档到 `it/evidence/`，覆盖弱口令被拒、失败锁定、越权拦截、敏感扫描命中、BMB 台账导出五类核心场景，作为第三方测评（2.3.2.10-2）整改证据底座。

## TDD 测试先行（RED）

- 先写 IT 级集成测试，未产出对应 evidence 文件即 FAIL：
  - `WeakPasswordRejectedIT`：弱口令注册/改密被 Keycloak `passwordPolicy` 拒绝，落 `it/evidence/password/`。
  - `LoginLockoutIT`：连续失败触发 `bruteForceProtected` 锁定，落 `it/evidence/lockout/`。
  - `UnauthorizedActionBlockedIT`：越权动作经 `AccessChecker.canPerform` 返回拒绝，落 `it/evidence/access-deny/`。
  - `SensitiveScanHitIT`（`com.yuzhi.dts.platform.service.security`）：`SensitiveScanService` 命中样例敏感字段且不外泄样本明文，落 `it/evidence/sensitive-scan/`。
  - `BaselineExportReportIT`：`SecurityBaselineService.exportReport()` 导出 BMB 台账整改证据包，落 `it/evidence/bmb-ledger/`。

## 技术设计（GREEN）

- 复用各模块测试上下文运行五类 IT，断言行为后将请求/响应/审计摘要写入对应 evidence 子目录（脱敏后）。
- BMB 台账证据复用 `source/dts-platform/.../service/security/baseline/SecurityBaselineService.java` 的 `exportReport()` 与 `SecurityBaselineRemediation` 状态机（NOT_STARTED/IN_PROGRESS/DONE/WAIVED）。
- 敏感扫描证据只记录命中规则与字段坐标，禁止落库样本明文（对照 M10 识别引擎评审要求）。
- 在 `it/README.md` 为每类证据登记路径与判定标准。

## 影响范围

- `worklog/v2.2.3/sprint-36-202606/it/evidence/{password,lockout,access-deny,sensitive-scan,bmb-ledger}/`
- `worklog/v2.2.3/sprint-36-202606/it/README.md`
- `source/dts-platform/src/test/**`、`source/dts-admin/src/test/**`（新增 IT，引用 `SecurityBaselineService.exportReport()` 前先 `gitnexus_impact`）

## 验证

- [ ] 五类场景各有 evidence 路径且可复跑。
- [ ] 敏感扫描证据不含样本明文。
- [ ] `it/README.md` 每类证据有判定标准。

## 完成标准

- [ ] IT 证据可驱动测评整改归档，而非口头确认。
