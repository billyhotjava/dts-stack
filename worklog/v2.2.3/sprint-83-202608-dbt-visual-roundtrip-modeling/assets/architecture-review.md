# 高级建模与 dbt 逆向导入架构 Review

**日期**：2026-08-01  
**结论**：两者不是两套建模系统，而是同一 canonical 模型的两个入口。

## 1. 高级建模 Review

### 已有能力

- `ModelSpec` 已区分 `DESIGNER_GENERATED` 与 `DBT_MANAGED`。
- Implementation Revision 已保存 `projectKey`、`dbtUniqueId`、输入、字段映射、物化方式及 checksum。
- 现有 ETL dbt 服务能够列模型、输出 diagnostics，并预览已经物化的 relation。
- dbt 执行已经由 Sprint-81 收敛到 ReleaseCandidate/Materialization/DbtExecutionGateway。

### 当前断点

- 旧 SQL 建模路由已重定向；新模型工作台没有真实 SQL 编辑器。
- 当前“代码模式”是 FML 静态展示，不是 dbt SQL。
- 通用 dbt 文件 API 操作共享 projectDir，没有模型/实施修订 pin，不能作为发布事实源。
- 结构视图、依赖图和物理表预览没有绑定同一模型修订与候选证据。

### 产品结论

普通业务可视化和高级 dbt 实现必须分层，而不是恢复旧的平行页面或把 SQL 混入可视化：

```text
模型上下文
├─ 业务定义（ModelSpec）
├─ 普通业务可视化（默认；隐藏全部 SQL/dbt 技术正文）
│  ├─ 逻辑表、字段、业务/模型依赖
│  └─ 物理结构与受控样例数据
├─ 数据实现
│  ├─ DESIGNER_GENERATED：系统生成隐藏 dbt 制品
│  └─ DBT_MANAGED：显式高级 dbt 技术视图
└─ 运行结果（candidate + relation evidence）
```

高级 dbt 技术视图位于现有模型详情“数据实现”阶段，只对具备权限的技术维护者显式开放；它与普通可视化互斥显示，不新增菜单或独立模型清单。

### dbt 物化不等于 DBT_MANAGED

- `DESIGNER_GENERATED`：业务可视化结构是事实源，DTS 编译出隐藏的 dbt SQL/YAML，再经 `DbtExecutionGateway` 物化。
- `DBT_MANAGED`：外部导入或高级维护的 SQL/Jinja 是实施版本的技术事实源，普通可视化只消费其结构投影。
- 两者都可走同一 StageGate → ReleaseCandidate → Materialization → dbt 链。物化时只能选择已固定的实现修订和策略，不能静默切换所有权。
- 当前只有 dbt runtime 时，UI 不应伪造“多执行引擎”选择；应表达为“实现来源/修订”和 `table/view/incremental` 等物化策略。

## 2. dbt 逆向建模 Review

### 已有能力

- ZIP 安全解压与大小/复杂度限制。
- manifest/catalog 转换、source-only 静态解析、legacy models.tsv 兼容。
- `dts.model-package/v1` 规范化中间契约。
- preview 的 CREATE/UPDATE/SKIP/CONFLICT/BLOCKED 分类。
- apply 的依赖闭包、幂等键、逐项事务、结果查询和失败重试。
- canonical ModelSpec、Implementation Revision 与 dbt artifact 落库。

### 当前断点

- 正式向导没有消费 `modelSpecImportApi.ts`，仍显示硬编码数据库表。
- source-only 当前不能可靠恢复完整字段、测试和业务语义；D10 只接受 enforced schema contract 且所有字段具备唯一 `name`/显式 `data_type` 的声明结构，其他结构最多只读且 apply BLOCKED；动态 Jinja/ref、macro 隐藏依赖和缺失 package 必须阻断受影响闭包。
- 现有导入可以部分成功，但 UI 没有进度恢复、逐项结果和前向撤销说明。
- inspect/apply/retry 等正式审计动作尚未完整登记。
- 外部变更与 DTS 内部语义补充的重新导入冲突策略没有产品化。

### 产品结论

“逆向建模”应先选择来源类型：

1. 数据库表/视图；
2. dbt 项目包。

Sprint-83 只落地第二条。导入结果是 `ModelSpec DRAFT + DBT_MANAGED Implementation Revision`，不能直接成为已发布模型。

## 3. 三类“可视化”必须分开

| 视图 | 事实来源 | 是否要求物化 | 是否可编辑 |
|---|---|---:|---|
| 普通业务可视化 | ModelSpec revision + 受控结构投影 | 否 | 逻辑语义按权限编辑；隐藏 SQL/Jinja、macro、compiled SQL 和完整 dbt DAG |
| 业务/模型依赖 | ModelSpec/source project/manifest 的受控投影 | 否 | 普通界面只读；完整技术 DAG 仅在显式高级 dbt 实现中显示 |
| 物理结构 | serving 或显式成功 candidate + relation evidence | 是 | 普通视图只读 serving；高级维护者可读成功 candidate 并标记非正式；历史结构固定可查 |
| 样例数据 | 用户点击时对 evidence relation 的实时受控查询 | 是 | 默认100/最大500；历史无样例行；表级密级和列策略 fail-closed；no-store/无导出 |

把三者混成一个“表预览”会造成错误承诺：设计字段不等于运行字段，manifest 字段也不等于当前数据库字段。

Catalog 同样不能用一个“已发布”状态同时表达治理发布和物理可用性：稳定逻辑资产分别维护 latestPublishedRef 与 servingRef；PUBLISHED 可发现，只有成功物化证据才切换 serving，失败/stale 保留旧 serving。

## 4. parser 收敛原则

当前存在多个 dbt artifact 消费者。本 Sprint 不立即发明新 parser，而是先冻结一个 normalized projection seam：

```text
archive/source files/manifest/catalog
              │
              ▼
      existing ModelPackage converter
              │
              ▼
     version-pinned representation view
      ├─ advanced modeling UI
      ├─ reverse import preview
      ├─ diagnostics/lineage
      └─ release/materialization evidence
```

任何消费者若需要额外字段，应扩展同一中间契约或 owner-side projection；不得复制 uniqueId、依赖、字段或 materialization 解析逻辑。

P0 先冻结 normalized projection seam、owner adapter 映射和 artifact-rich 消费者；parser 物理删除不是 P0 DoD。只有 GitNexus caller=0、非建模消费者已迁移或明确保留 owner、回滚/源契约证据成立后，才由 F5/T05 删除。这样既保持 A4 单一事实投影，也避免 diagnostics、lineage、asset sync 等消费者被清理工作拖入首个价值切片。

## 5. 交付切片与证据门

| 切片 | 用户可见结果 | 门禁 | 优先级 |
|---|---|---|---|
| S0 工程准入 | 当前切片所需工程 fixture、交付基线和共享契约可重复；运行时认证按 S3 独立消费 | F0/T01～T04、G0/G1 | P0 Gate |
| S1 统一表示 | 同一模型详情读取逻辑表、字段、依赖和显式高级技术只读信息；普通视图技术正文为 0 | F1、F2/T01～T02、F5/T02～T03 | P0 |
| S2 artifact-rich ZIP | ZIP inspect→mapping→preview→apply/retry→`ModelSpec DRAFT + DBT_MANAGED`，失败逐项可解释 | F3/T01～T04、F5/T01～T03、F6/T02 | P0 |
| S3 发布物化 | 固定 Implementation 经 StageGate/Candidate/Gateway 物化并切换合格 serving，提供受控物理预览 | H83-01、F0/T05、F2/T03～T04、F4、F6/T03～T04 | P1 |
| S4 生命周期增强 | source-only/complex、技术三方漂移、重新导入和前向撤销 | F1/T05、F3/T05～T06、F2/T05 | P1 |
| S5 物理退役 | 旧按标签 Bash DAG、建模共享文件写、旧脚本和重复 parser 在 caller=0 后删除 | F5/T05、F6/T05 | P2 |

Sprint-83a 对应 S0～S2，Sprint-83b 对应 S3～S4，Sprint-83c 对应 S5。当前只要求待拉取切片通过 DoR；后续切片保持 DRAFT 不构成前置依赖。客户脱敏包只阻断 CUSTOMER-VALIDATION，不阻断 S0～S2 的工程实现。

## 6. 具名残余风险

`R-DBT-LEGACY-DAG`：勘察发现 generator/ready 路由虽已删除，但仓库内仍有两份可由通用 Airflow trigger 触达的 mutable/privileged BashOperator DAG；其 sync 确实无 Token 并使用 `|| true`。2026-08-02 确认两者运行记录为 0 后，源码与部署副本已物理删除；canonical Release/Plan factory 保留。证据见 `it/legacy-retirement-evidence.md`。

`R-DBT-LEGACY-PREVIEW`：旧 `GET /api/etl/dbt/preview` 以当前 manifest relation 返回原始样例行，不具备 model/revision/candidate/evidence pin、表级密级和列 ALLOW/MASK/DENY。新建模链从 S3 起必须调用新的 physical-preview 契约；F5/T03 是 S3 的安全遏制门，先让普通已认证用户/建模角色无法取得旧原始行；F5/T04 观测剩余消费者。无非建模 owner 时由 F5/T05 删除，有 owner 时先迁移到同一 evidence/classification/masking 服务并封闭原端点，禁止两套预览安全边界长期并存。

## 7. 未选择的方案

| 方案 | 不选择理由 |
|---|---|
| 新建 `SqlModel`/`DbtProject` 平行模型台账 | 与 ModelSpec/Implementation 重复，违反 Sprint-81 与 DTS A4 |
| 任意 SQL → 可编辑关系运算画布 | 无法对 Jinja、macro、dispatch、方言 SQL 保证无损往返 |
| 高级页直接操作共享 dbt projectDir 并 run | 缺少 tenant/model/revision pin，可绕过 Candidate/StageGate |
| 导入后自动覆盖当前模型 | 会丢失 DTS 内部语义补充和审计证据 |
| import 时在线下载 packages 并执行 dbt build | 引入供应链、凭据和非受信代码执行风险 |
| 导入成功即登记可消费资产 | DRAFT 未通过治理与发布门禁，不能冒充已交付资产 |
