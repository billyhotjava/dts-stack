# T04：建立 typed-column 物理类型契约与核验

**优先级**：P0  
**状态**：READY  
**依赖**：T01～T03

## 目标

确保普通设计器模型只有在 current ModelSpec 字段类型、dbt runnable artifact 和
PostgreSQL 真实列类型一致时才能进入 BUILT，阻断 numeric→text 等“列名相同但类型
错误”的伪物化。

## 技术设计（Contract-first）

- **canonical 输入**：current `ModelSpecView.fields[]` 中
  `{name:string,dataType:string,nullable:boolean}`；字段类型必须覆盖 compiler 选出的每一列。
- **P0 类型语法**：只接受受控 logical type，不把用户文本直接拼入 SQL：
  `string|varchar|text|int|integer|bigint|decimal|numeric|date|timestamp|datetime|
  timestamptz|boolean|bool|uuid`，以及有边界校验的
  `varchar(n)`、`decimal(p,s)`、`numeric(p,s)`。
- **canonical PostgreSQL 映射**：
  - `string|varchar|text|varchar(n)` → `text`（P0 不保留展示长度）；
  - `int|integer` → `integer`；
  - `bigint` → `bigint`；
  - `decimal|numeric` → `numeric`；
  - `decimal(p,s)|numeric(p,s)` → `numeric(p,s)`；
  - `timestamp|datetime` → `timestamp without time zone`；
  - `timestamptz` → `timestamp with time zone`；
  - `date|boolean|bool|uuid` → 对应 PostgreSQL canonical type。
- **编译输出**：
  - `ImplementationProjection` 携带 immutable typed fields，不再只携带字段名；
  - DESIGNER_GENERATED 最终 model SQL 对每个输出列生成受控 `cast(... as type)`；
  - schema.yml 每列写 `data_type` 与 DTS expected-type meta，供 manifest 保留；
  - 缺类型、类型不支持、字段覆盖不全返回稳定
    `MODEL_IMPLEMENTATION_FIELD_TYPE_*`，不得降级为 text。
- **核验输入**：`RelationLocator` 在 expected column name 之外携带 canonical expected
  physical type；expected checksum 同时覆盖 name+type。
- **核验规则**：PostgreSQL adapter 规范化 `pg_catalog.format_type` 后逐列比较；
  不一致写 append-only failed observation，error code=
  `MODEL_PHYSICAL_RELATION_COLUMN_TYPE_MISMATCH`，Candidate 进入 BUILD_FAILED。
- **nullable 边界**：本 Task 记录逻辑/物理 nullable，但不把 CTAS/view 的物理
  `attnotnull` 当发布硬门禁；逻辑 nullable 继续由 dbt not_null tests/质量门禁负责，
  避免把质量语义错误等同为数据库 DDL constraint。
- **高级 dbt 兼容**：DBT_MANAGED manifest 声明 `data_type` 时同样核验；未声明时真实
  observation 是物理 schema 事实，projection 明示 `TYPE_EXPECTATION_UNDECLARED`，
  不伪造 expected type，也不走 DESIGNER_GENERATED 的 silent fallback。
- **未来 adapter**：核心聚合只消费 inspector 的 canonical type match 能力；MySQL/达梦
  未注册 normalizer 时 fail-closed，不在 PostgreSQL switch 中硬塞分支。

## 数据流

```text
ModelSpecView.fields
  → ModelSpecCompilerProjection typed fields
  → ModelingDbtCompiler cast + schema.yml data_type
  → dbt manifest columns.data_type
  → RelationLocator expected name/type
  → PostgreSQL pg_catalog.format_type
  → adapter-aware compare
  → verified observation / BUILD_FAILED
```

无新表、无新页面、无第二套 schema 台账；复用 T01～T03 的 artifact、inspector、
observation 与 Candidate gate。

## 影响范围

- `ModelSpecCompilerProjection`
- `ModelingDbtCompiler`
- `PhysicalRelationInspector` / PostgreSQL adapter
- `ModelMaterializationBuildRepository.CandidateBuildEntry`
- `ModelMaterializationRunArtifactService`
- compiler/service/PostgreSQL focused tests

## 验证（RED→GREEN）

- [ ] ModelSpec `amount:numeric(18,2)` 生成受控 cast 与 schema `data_type`。
- [ ] 类型 SQL 注入、未知类型、字段覆盖不全在 compile 前 fail-closed。
- [ ] manifest expected numeric、真实 text → failed observation，不能 BUILT。
- [ ] PostgreSQL canonical aliases/precision 正确比较。
- [ ] expected checksum 随字段类型改变而改变，旧 observation 不能复用。
- [ ] DBT_MANAGED 有声明则核验、无声明则明确降级且不影响 generic asset sync。
- [ ] 真实 PostgreSQL IT 证明 uuid/numeric 类型匹配后才允许 BUILT。

## Definition of Ready

- [x] canonical 输入、P0 类型语法与映射已固定。
- [x] compiler artifact、manifest、locator 与 observation 链已画通。
- [x] DESIGNER_GENERATED 与 DBT_MANAGED 不同的 expected-type 来源已固定。
- [x] nullable 非物理硬约束边界已固定。
- [x] 测试和稳定错误码已命名。

## Definition of Done

- [ ] DESIGNER_GENERATED 不再丢失 ModelSpec field type。
- [ ] 类型漂移不能进入 BUILT。
- [ ] 类型值不可形成任意 SQL 片段。
- [ ] PostgreSQL 真实 IT 与兼容回归通过。
- [ ] F3 状态恢复 DONE 后，F4 才可进入 READY。
