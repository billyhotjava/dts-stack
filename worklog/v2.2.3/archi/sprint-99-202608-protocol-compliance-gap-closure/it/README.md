# Sprint-99 集成验收证据

**状态**: PENDING
**原则**: 无占位证据。"TODO screenshot" ≠ done。每条 IT 必须指向 `evidence/` 下的真实产物。

## IT 清单

| IT | 对应 | 验收内容 | 证据位置 | 状态 |
|----|------|----------|----------|------|
| IT-01 | F1/T01、T03 | 弱口令被 Keycloak 真实拒绝；合规口令通过；策略接口值与 realm 实况一致 | `evidence/it-01-password-policy/` | PENDING |
| IT-02 | F1/T02 | 连续失败达阈值账号真实锁定；审计可查；不存在账号与存在账号失败文案一致（反枚举）| `evidence/it-02-login-lockout/` | PENDING |
| IT-03 | F2/T01、T02 | BMB 条款表可查；≥4 项 AUTO 判定；**依赖不可达时返回 UNKNOWN 而非 PASS** | `evidence/it-03-bmb-checks/` | PENDING |
| IT-04 | F2/T03、T04 | 整改单全生命周期（登记→指派→补证据→关闭）；证据包解压后 4 个目录齐全且 manifest 校验通过 | `evidence/it-04-assessment-ledger/` | PENDING |
| IT-05 | F3/T01～T03 | 真实 catalog 扫描产出发现；负样本不误判；**日志与结果表中无敏感原文** | `evidence/it-05-sensitive-scan/` | PENDING |
| IT-06 | F3/T04 | 确认后普通用户查询该表**返回值确实被脱敏**；降级建议被拒 | `evidence/it-06-masking-classification/` | PENDING |
| IT-07 | F4/T01、T02 | 故障注入 → 告警到达 Alertmanager → 恢复 resolved；抑制生效无风暴 | `evidence/it-07-alerting/` | PENDING |
| IT-08 | F4/T03 | 六个 `dts_` 业务指标可见；标签基数受控；Grafana 看板随入湖任务变化 | `evidence/it-08-dashboard/` | PENDING |

## 四态证据要求

带 UI 的 IT（IT-03、IT-04、IT-05、IT-06）必须包含：空 / 加载 / 错误 / 成功 四张截图，Chrome95 下拍摄。

## 关键否定性断言（最容易被糊弄过去的地方）

这四条如果没有真实证据，本 Sprint 不得置 DONE：
1. **IT-03**：停掉 Keycloak 后基线校验返回 `UNKNOWN`，不是 `PASS`
2. **IT-05**：全量日志 grep 不到任何完整身份证号/手机号
3. **IT-06**：普通用户实际查询结果被脱敏（不是"规则创建成功"就算完）
4. **IT-07**：告警是真的送达 Alertmanager，不是"规则语法正确"
