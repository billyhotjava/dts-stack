# Sprint-74 架构复审

**复审日期**：2026-07-26  
**复审范围**：目标旅程、对象所有权、阶段门禁、DataWorks 参考边界、dbt 边界、存量兼容、发布治理
**结论**：PASS——二次复审移除 DIM/五层扩展并冻结兼容迁移边界；2026-07-27 G0、GitNexus、部署、真实 API/PG/dbt/Chrome95 均已闭环

## 1. 结论摘要

本 Sprint 不是给现有长表单增加解释，而是纠正顺序：

```text
业务目的/每行含义
  → 模型类型
  → 逻辑设计（可独立完成）
  → 实现方式（普通或 dbt）
  → 发布控制面
  → 发布结果/物理资产
```

该顺序与 DataWorks “规划/建模后再发布物化”、Kimball “业务过程→粒度→维度→事实”的原则一致，同时保留 DTS 已有四层 canonical 对象，不引入平行控制面。

## 2. 关键问题复审

| 问题 | 原设计 | Sprint-74 结论 | 判定 |
|---|---|---|---|
| 新建为什么容易选错 | 默认 FACT | 不设默认，先做业务目的决策 | PASS |
| 逻辑建模为何要求数据实现 | 无 DESIGNED，进入实现门禁混入逻辑页 | DESIGNED 可独立完成；只有要发布才进入实现 | PASS |
| 数据实现是什么 | 来源、上游、字段映射与逻辑字段混放 | 唯一 ModelImplementation revision，负责“如何生成” | PASS |
| 物理资产是什么 | 页面同时承担 dbt 入口 | 改为“发布结果”，只读展示真实对象和证据 | PASS |
| 物理资产是否挂钩 dbt | 被 UI 暗示为是 | 否；dbt 只是实现方式之一 | PASS |
| DataWorks DIM 如何处理 | DIMENSION 固定 DWD | DataWorks 仅作为产品顺序参考；本 Sprint 保持 DIMENSION→DWD，不扩 DIM/五层策略 | PASS |
| 九项缺口哪些必须修 | 所有 gate 一次显示 | 只显示当前下一道门禁；未来项不计数 | PASS |
| 财务项目误建如何恢复 | 只能继续填或重建 | DRAFT 无实现时 preview/apply 追加 revision 改型 | PASS |

## 3. 架构一致性检查

### 3.1 唯一所有者

- 逻辑正文仍归 `ModelSpecRevision`；
- 实现配置归既有 `ModelImplementationRevision`；
- dbt 继续使用同一 implementation ownership，不新建 dbt 模型台账；
- 发布继续由 Sprint-69 ReleaseCandidate 管理；
- 物理结果继续消费 lifecycle/artifact/catalog registration。

结论：通过 domain-dts A4“禁止平行实现”。

### 3.2 模型类型与目标层

模型类型和目标层必须在概念与 UI 上分开，但本 Sprint 不承担数仓分层体系迁移：

- v2.2.3 继续使用 `DIMENSION/FACT→DWD`、`SUMMARY→DWS`、`APPLICATION→ADS`；
- 目标 layer 由服务端唯一 resolver 解析，前端只展示；
- 不新增 `DIM` Layer、`DATAWORKS_5_LAYER_V1` 或计划策略切换 UI；
- 历史 plan/model revision 不静默改写。

DataWorks 的独立 DIM 层不是解决“默认 FACT、逻辑与实现混淆、dbt 入口错位”的必要条件。若未来有真实客户需求，另立 ADR/Sprint 评估 Layer 枚举、数据库约束、导入、dbt、发布和存量迁移。

结论：保留 Sprint-73 的兼容结果，把本 Sprint 收敛到创建旅程和阶段边界。

### 3.3 阶段门禁

`DESIGNED` 是本次架构中不可删除的核心。没有它，页面仍会把“逻辑完成”和“有实现输入”混为同一件事。主动作必须读取服务端 gate，而不是只依赖前端 dirty/configured 状态。

结论：通过，但 F2/T02/F2/T03 必须作为同一提交批次完成。

### 3.4 Sprint-73 已提交兼容迁移

Sprint-73 提交 `645ea2800` 已把 `implementationPolicy` 加入 ModelSpec 及逻辑页。它不再是并行 worktree 冲突，而是必须兼容的已提交源码契约。Sprint-74 不回退历史，但实施前必须：

1. 把 GitNexus 更新到当前 HEAD，再运行 owning symbols impact；
2. 核对哪些环境已经产生带 `implementationPolicy` 的 snapshot；
3. canonical 新写迁入 `ModelImplementation.settings`；
4. 历史 snapshot 可读、可原样 round-trip，并提供 dry-run/apply/rollback；
5. 已有 current implementation 时以后者为真值，冲突不得自动覆盖；
6. 禁止用 UI 文案掩盖所有权冲突。

结论：这是实施前阻断项，不影响本 Sprint 设计本身。

## 4. 风险与控制

| 风险 | 等级 | 控制 |
|---|---|---|
| 模型类型与目标层继续被 UI 混为一项 | 中 | 服务端经典 resolver + 页面分别展示；不新增 DIM/五层策略 |
| 字段 `displayName` 影响 checksum/snapshot | 高 | JSONB expand；旧 snapshot 原样可读；只在新 revision 写入 |
| 改型导致类型专属字段被静默清空 | 高 | preview 列出 `acceptedClearFields`；用户确认后才 apply |
| Sprint-73 已提交 implementationPolicy 与新 owner 冲突 | 高 | expand/migrate/contract；历史可读、canonical 新写唯一、current implementation 优先 |
| 当前镜像和数据库落后 Sprint-73 提交 | 高 | 已构建部署当前后端/webapp 并核对 20260727 changeset 01～07 |
| GitNexus 索引落后 HEAD | 高 | 2026-07-27 已对齐 `c7e085de3`；后续提交批次继续执行 symbol impact 和 detect-changes |
| 门禁减少被误解为降低治理 | 中 | 只调整执行阶段，不删除 RELEASE_READY 治理门禁 |
| 真实浏览器无法自动验收 | 高 | 已用显式 resolver、真实 Cookie 与 Chromium 95 完成 3/3 旅程 |

## 5. 复审决定

修订后的方向可继续，前提是再次确认以下四条：

1. 逻辑模型达到 DESIGNED 后可以结束本次工作，不强迫实现；
2. 物理名、装载、分区、保留期全部归数据实现；
3. 高级 dbt 入口从“物理资产”移到“数据实现”；
4. 模型类型与数仓层在概念/UI 上解耦，但本 Sprint 保持 v2.2.3 经典映射，不新增 DIM 或五层策略。

## 6. 三次复审决定（2026-07-27）

用户已确认继续执行 Sprint-74，并要求通过 goal 完成。上述四条冻结决定保持不变。

本次新增页面改动中，草稿编辑入口、数据集市调整、日期维度生成器约束和实现页业务化重排可以保留；“目标表摘要来自逻辑设计”、逻辑页继续写 `implementationPolicy`、物理结果页保留高级 dbt 入口，以及前后端 ARCHIVED 引用判定不一致，均不能作为新架构接受，必须按 README §3.1 修复后才能关闭 Sprint。

结论：架构 Gate 与实施复核均为 PASS；Sprint-74 已按该边界实现并通过真实验收。质量治理、`AGENTS.md`、`CLAUDE.md` 等并行修改仍不归本 Sprint 所有，也未被回退。
