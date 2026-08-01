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

高级建模应成为模型工作台的一种模式，而不是恢复旧的平行页面：

```text
模型上下文
├─ 业务定义（ModelSpec）
├─ 可视化结构（版本固定 read model）
├─ SQL/dbt（DBT_MANAGED 技术源）
├─ 依赖与测试（manifest/source facts）
└─ 运行结果（candidate + relation evidence）
```

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
- source-only 项目不能可靠恢复字段、测试和业务语义；动态 Jinja/ref 必须阻断。
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
| 逻辑表结构 | ModelSpec revision | 否 | 按 ownership mode 控制 |
| dbt 依赖结构 | source project/manifest snapshot | 否 | DBT_MANAGED 只读，改 SQL 后重新解析 |
| 物理表与样例数据 | successful candidate + relation observation | 是 | 只读，受权限/密级/脱敏控制 |

把三者混成一个“表预览”会造成错误承诺：设计字段不等于运行字段，manifest 字段也不等于当前数据库字段。

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

## 5. 未选择的方案

| 方案 | 不选择理由 |
|---|---|
| 新建 `SqlModel`/`DbtProject` 平行模型台账 | 与 ModelSpec/Implementation 重复，违反 Sprint-81 与 DTS A4 |
| 任意 SQL → 可编辑关系运算画布 | 无法对 Jinja、macro、dispatch、方言 SQL 保证无损往返 |
| 高级页直接操作共享 dbt projectDir 并 run | 缺少 tenant/model/revision pin，可绕过 Candidate/StageGate |
| 导入后自动覆盖当前模型 | 会丢失 DTS 内部语义补充和审计证据 |
| import 时在线下载 packages 并执行 dbt build | 引入供应链、凭据和非受信代码执行风险 |
| 导入成功即登记可消费资产 | DRAFT 未通过治理与发布门禁，不能冒充已交付资产 |
