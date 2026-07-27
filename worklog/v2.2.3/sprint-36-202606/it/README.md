# Sprint-36 集成测试与准入计划

## 目标

证明数据安全（2.3.2.5）与安全保密（2.3.2.10）的机密级 P0 控制在现网真实生效：弱口令被拒、连续失败触发锁定、前端不泄露 token、越权动作被 `canPerform` 拦截、敏感字段被自动识别、BMB17.x 整改台账可导出测评证据包。全部以 TDD（测试先行）驱动，不接受静态假数据或旁路放行。

## 证据目录

| 证据 | 路径 | 状态 |
|------|------|------|
| 口令策略与失败锁定 | `it/evidence/password-policy-lockout/` | READY |
| 会话安全整改 | `it/evidence/session-security/` | READY |
| 操作权限矩阵 | `it/evidence/operation-permission-matrix/` | DONE |
| 敏感数据自动识别 | `it/evidence/sensitive-discovery/` | READY |
| BMB 符合性台账 | `it/evidence/bmb-baseline-ledger/` | READY |
| 安全评审 gate | `it/evidence/security-review/` | READY |

## 验收命令草案

```bash
# 后端单元/集成测试（M05/M10 改动模块）
cd source
./mvnw -q -pl dts-platform -Dtest='*Iam*,*AccessChecker*,*ActionPolicy*,*Sensitive*,*Masking*,*SecurityBaseline*' test
./mvnw -q -pl dts-admin -Dtest='*Session*,*Pki*,*PasswordPolicy*,*BruteForce*' test
```

```bash
# 前端会话整改验证（双 webapp）
cd source/dts-admin-webapp && pnpm run test:source && pnpm run build
cd source/dts-platform-webapp && pnpm run test:source && pnpm run build
# 断言生产构建产物中无 Authorization 明文日志
! grep -rIn "console.log.*[Aa]uthorization" source/dts-*-webapp/dist 2>/dev/null
```

```bash
# Keycloak realm 策略校验（口令策略 + 暴力破解保护已启用）
grep -q '"passwordPolicy"' services/dts-keycloak/realm-dts.json
grep -q '"bruteForceProtected" *: *true' services/dts-keycloak/realm-dts.json
```

```bash
# 操作权限矩阵：越权动作应被拒（示例，需登录态 token）
curl -sS -X POST http://127.0.0.1:18082/api/iam/asset-action-policies \
  -H 'Content-Type: application/json' \
  -d @worklog/v2.2.3/sprint-36-202606/it/fixtures/action-policy-export-deny.json
# 以无 EXPORT 权限角色调用导出，期望 403 / action_denied
```

```bash
# 敏感数据扫描：对样例数据集触发扫描并查询命中
curl -sS -X POST http://127.0.0.1:18082/api/security/sensitive/scan \
  -H 'Content-Type: application/json' \
  -d @worklog/v2.2.3/sprint-36-202606/it/fixtures/sensitive-scan-request.json
curl -sS 'http://127.0.0.1:18082/api/security/sensitive/results?datasetId={id}'
```

```bash
# BMB 测评整改证据包导出
curl -sS 'http://127.0.0.1:18082/api/security/baseline/report?format=bmb-evidence' -o /tmp/bmb-evidence.md
```

```bash
# 门禁套件
tests/run_gates.sh
```

## 阻断条件（出现即不予准入）

- Keycloak 可注册/设置弱口令（不满足长度≥12 或复杂度），或暴力破解保护未启用。
- 前端仍把裸 token 持久化到 localStorage，或生产构建残留 `console.log(Authorization)`。
- `TEST_SESSION_ENABLED` / `handleDevFallback` 旁路在生产 profile 下仍可绕过认证。
- 任一写动作（删除/导入/导出/归档/销毁等）未经 `AccessChecker.canPerform` 校验即放行。
- 敏感扫描把原始样本数据外泄到日志/响应，或扫描结果不可追溯。
- BMB 台账无法导出包含条款号、状态、证据指针与两轮迭代记录的测评证据包。
- 整改破坏既有 PKI/CA + USBKey 登录，或绕过既有 RLS/masking/FIELD 策略。
- security-reviewer 复审存在未闭合的 CRITICAL/HIGH。

## TDD 与影响分析约定

- 每个 task 先红后绿：先提交失败测试，再实现，最后在绿灯下重构。
- 改既有 symbol 前 `gitnexus_impact({target, direction:"upstream"})`，HIGH/CRITICAL 风险先报告；提交前 `gitnexus_detect_changes()` 核对影响范围。
- Java 侧禁用 `Optional.get()`，统一 `orElseThrow()`（modernizer 强制）。
