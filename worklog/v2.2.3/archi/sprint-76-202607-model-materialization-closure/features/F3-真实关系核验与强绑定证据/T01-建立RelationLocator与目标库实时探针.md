# T01：建立 RelationLocator 与目标库实时探针

**优先级**：P0
**状态**：DONE
**依赖**：F2/T02

## 目标

从 current manifest 解析目标关系，并使用本次运行目标的凭据直接查询数据库元数据，拒绝仅凭 run_results 推断存在。

## 技术设计（Contract-first）

- **输入契约**：manifest node（database/schema/alias/resource_type/materialized）、expected targetIdentifier、runtime target context。
- **输出契约**：Sprint §6.2 `RelationObservation`，初始未落库。
- **locator 校验**：manifest identifier 必须等于 targetPhysicalName；node meta 必须匹配 current model/implementation checksums。
- **port**：`PhysicalRelationInspector.observe(TargetContext, RelationLocator)`。
- **P0 adapter**：PostgreSQL；使用 catalog/system metadata 判断 TABLE/VIEW/MATERIALIZED_VIEW，并读取列名、顺序、类型形成 checksum。
- **安全**：identifier 严格 canonical validation；metadata 查询参数化/受控引用；凭据只由 runtime target context 解析。
- **错误路径**：
  - relation missing；
  - identifier/type mismatch；
  - probe timeout；
  - target credential unavailable；
  - adapter unsupported。
  全部返回结构化 code，禁止 exists=true fallback。
- **性能**：单 relation ≤10s，不执行 `count(*)`。

## 影响范围

- 新 `PhysicalRelationInspector` port/contract
- PostgreSQL inspector adapter
- dbt manifest mapping
- unit + PostgreSQL integration tests

## 验证（RED→GREEN）

- [x] run_results success + relation absent → exists=false。
- [x] table/type/columns 在真实 PostgreSQL 目标正常观察。
- [x] alias/revision meta mismatch 与非法 locator 均 fail-closed。
- [x] probe 只持久化 credential version ref，不输出 JDBC 凭据。

## Definition of Done

- [x] PostgreSQL 真实 target 通过。
- [x] inspector 无 Catalog 写入副作用。
- [x] adapter-specific 分支不渗入 ReleaseCandidate 状态机。

证据：`../../it/evidence/f3-real-physical-relation/README.md`。
