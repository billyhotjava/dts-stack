# IT-03：关键关系与端到端用例评审记录

**状态**：PASS（架构用例设计）
**目标日期**：2026-08-14
**Accountable roles**：数据架构负责人、建模/发布/资产 owner
**已登记评审人**：xiezm（兼任本记录全部 Accountable roles）

## 输入

- `assets/data-model-relationships.md`
- `assets/f1-t02-decision-pack.md`
- `assets/consolidated-approval-pack.md` D06～D08、N04～N10
- `assets/decision-register.md` ADR-86-13/14/15
- F1/T02 与 NFR-86-04～08/12

## 评审范围

逐项走查 E2E-A～D 以及关系一致性、批量交付、依赖顺序、二次物化、模型到资产、未归域治理和指标上下文。Sprint-86 评审的是用例设计完备性；真实 UI/API/数据库/审计执行证据属于下一实施 Sprint。

## 评审与通过标准

- [x] 每条关系有当前事实、目标基数、稳定键、owner、失败路径和迁移缺口
- [x] Revision、Candidate、Dispatch、Observation、语义资产、物理资产状态不跳级
- [x] 批量候选的依赖闭包、原子/显式排除、幂等和重试规则可判定
- [x] 每个 Given/When/Then 指向下一 Sprint 的 UI/API/数据/审计证据类型
- [x] E2E-D 明确移交 MDM Sprint，不扩大本 Sprint 范围

## 真实决议记录

- 实际时间：2026-08-09
- 参与者真实姓名：xiezm（兼任本记录全部 Accountable roles）
- 通过/驳回的用例：E2E-A～D 设计全部通过；E2E-D 只批准边界和移交，不批准 MDM 实现
- 异议与反例：无调整；接受 blocker 使候选创建零落库/零派发、运行阶段允许逐项失败这一边界
- 行动项/负责人/截止日期：Sprint-87 F2/F6 执行 UI/API/数据/审计和真实运行验证；xiezm；对应 Task DoD 前
- 证据链接：`assets/consolidated-approval-pack.md` D06～D08/N04～N10、`assets/f1-t02-decision-pack.md`、`assets/data-model-relationships.md`

## 已备评审结论

- 双身份、locator 冲突/重命名/撤销/重放规则已形成候选。
- 集市单分类、APPLICATION 主题域必填、revision DAG、候选全有或全无、运行逐项失败与二次物化规则已形成候选。
- E2E-A～D 已绑定 Given/When/Then、owner、失败路径和下一 Sprint 的 UI/API/数据/审计证据类型。
- 批量、DAG、轮询、卡顿、取消与并发设计值已由 xiezm 批准；运行验证转 Sprint-87。

本 PASS 只证明关系与 E2E 用例设计经过具名评审，不代表执行过代码、迁移、浏览器或真实 E2E。
