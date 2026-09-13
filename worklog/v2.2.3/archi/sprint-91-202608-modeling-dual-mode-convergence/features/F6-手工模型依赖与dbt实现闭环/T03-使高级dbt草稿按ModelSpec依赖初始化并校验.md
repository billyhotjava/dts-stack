# T03: 使高级 dbt 草稿按 ModelSpec 依赖初始化并校验

**优先级**: P0
**状态**: CODE_COMPLETE / E2E_PENDING
**依赖**: F6/T01、F4

## 目标

新建或继续编辑手工 dbt 实现时，由服务端根据 dependency snapshot 生成受管 source/proxy 和目标节点，提交前将 SQL 中的 `source()/ref()` 与 ModelSpec 声明逐项对账，彻底消除“模型有关系、代码不认”或“代码私接依赖”的断链。

## 技术设计 (Contract-first)

### 创建/恢复草稿

- `create`/`sourceBundle` 接收 immutable model/implementation pins，服务端读取 F6/T01 snapshot。
- ODS 来源生成受管 `sources.yml`；上游/维度模型生成只读 proxy/manifest 条目；目标 `dbtUniqueId` 仍由服务端派生。
- 首个草稿的目标模型按拥有节点 uniqueId 选定；系统 proxy 与 STG 不参与“恰好一个 owned MODEL”计数，避免 `DBT_DRAFT_MODEL_SELECTION_UNSUPPORTED`。
- 受管文件在 UI 标记「系统依赖」，不可删除或改名；用户 SQL/SCHEMA/CONFIG 仍走现有保存、校验、提交。

### 校验/提交

- 复用 `AdvancedDbtDraftStaticValidator` 的解析结果与 F6/T01 resolver，不再写第二个 parser。
- 校验响应新增 declared/parsed 对照投影：`MATCHED / MISSING / UNDECLARED / STALE`。
- commit 必须重新读取 current pins 并校验 `validatedChecksum + dependencyChecksum`，防止校验后依赖漂移。
- 成功提交继续生成同一 implementation revision 和 `{SQL,SCHEMA,CONFIG}` 制品；CONFIG bundle 保存受管依赖清单，不另建实现记录。

### 稳定错误码

- `DBT_DRAFT_DEPENDENCY_UNDECLARED`
- `DBT_DRAFT_DEPENDENCY_MISSING`
- `DBT_DRAFT_DEPENDENCY_PIN_STALE`
- `DBT_DRAFT_SOURCE_BINDING_STALE`
- `DBT_DRAFT_DEPENDENCY_CYCLE`

## Feature 关联

- 上游 F6/T01 提供唯一 snapshot，F6/T02 提供 UI 声明。
- F7/T03 的系统生成 bundle 与本 Task 使用相同校验/commit seam。
- F8/T01/T03 直接复用提交时固定的 dependency checksum，禁止物化时重新猜依赖。
- F5 候选必须钉住本 Task 输出的 implementation checksum + dependency checksum。

## Definition of Ready

- [x] F6/T01 snapshot 与 stale/cycle 错误契约已实现。
- [x] owned target、受管 source/proxy 与首个草稿计数规则已冻结。
- [x] 继续复用既有 static validator，禁止新 parser 的边界已确认。

## 验证 (RED→GREEN)

- [x] physical source、上游 ref 与维度 ref 可重建为受管 source/proxy bundle；真实四层链留待 IT-11。
- [x] 受管 proxy 不计入 owned target，首次草稿和后续草稿均可创建。
- [x] 未声明 ref、遗漏必需 ref、stale pin、source binding 漂移返回稳定错误码。
- [x] commit 携带 validation 与 dependency checksum，依赖漂移不能提交旧图。
- [ ] 手工提交与 ZIP apply 对相同 revision 生成等价 dependency checksum。

## Definition of Done

- [ ] IT-11 可从空实现开始逐表提交整个手工链，且 declared/parsed 全为 MATCHED。
- [x] 候选与物化从提交制品/固定 pins 还原同一依赖快照；治理真实对账待 Sprint-93 IT。
- [x] 没有新增 parser、target 选择器、草稿状态机或依赖台账。
