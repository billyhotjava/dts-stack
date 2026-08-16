# Sprint-93 集成验收台账

**当前结论**：PENDING。本文定义证据槽位，不代表任何 IT 已执行；实际执行后必须登记日期、环境、commit/image、操作者、命令/点击步骤、结果和制品路径。

| IT | 验收切片 | 关键断言 | 状态 |
|---|---|---|---|
| IT-01 | 首次物化登记资产 | ModelSpec → candidate → dispatch → observation → 单一 AssetKey/datasetId/projection | PENDING |
| IT-02 | 发布推进同一资产 | 发布不创建第二 dataset；publication/serving 状态按证据推进 | PENDING |
| IT-03 | 存量补齐 | preview/apply/rollback；歧义 fail-closed；回滚不覆盖漂移版本 | PENDING |
| IT-04 | 服务投影消费 | outbox → SYNCED；超时/失败重试；旧 version 不覆盖新 ref | PENDING |
| IT-05 | QualityEvidence Port | pass/fail/running/missing/expired/mismatch 全部结构化判定 | PENDING |
| IT-06 | 发布质量 UI | 工程质量与治理质量分栏；候选钉定 rule/version/binding/run/checksum | PENDING |
| IT-07 | 模型血缘 | ODS→DWD→DWS→ADS 表级和字段级边可查，来源/验证/有效期正确 | PENDING |
| IT-08 | OM 故障降级 | OM 不可用时资产仍可查，技术同步显示失败且可重试 | PENDING |
| IT-09 | 治理页面一致性 | 概览、目录、详情数字/状态/业务归属数据域一致，深链可达 | PENDING |
| IT-10 | 二次物化 | 模型数和资产数不增加；candidate/attempt/observation 增加；servingRef 推进 | PENDING |
| IT-11 | 权限与审计 | xiezm 所级管理员正向通过；部门越权失败；审计动作有分类 | PENDING |
| IT-12 | 发布/回滚/可运维 | 镜像、迁移、feature flag、告警、runbook、回滚演练完整 | PENDING |
| IT-13 | Chrome 95 集中验收 | 真实登录；空/加载/错误/成功；console/network 无未解释异常 | PENDING |

## 集中执行顺序

1. 固定 commit/image、数据库快照和样本 ID。
2. 运行后端契约、迁移和故障注入 IT。
3. 部署受影响服务并验证健康、outbox 和 projection。
4. 使用 xiezm 在 Chrome 完成 IT-01～11；最后执行二次物化。
5. 切换 Chrome 95 完成 IT-13。
6. 执行 rollback 演练，再恢复并复核数据一致性。

任何失败只做针对性修复和重跑，不重新执行无关全套测试。
