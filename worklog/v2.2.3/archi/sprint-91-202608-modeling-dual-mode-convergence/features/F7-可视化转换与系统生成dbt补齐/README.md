# F7: 可视化转换与系统生成 dbt 补齐

**优先级**: P0
**状态**: CODE_COMPLETE / E2E_PENDING

## 目标

在 F6 的统一依赖上补齐可视化设计器所需的字段映射、过滤、关联、聚合和去重，使用户手工创建 ModelSpec 后既可维护 dbt 代码，也可由系统生成等价 dbt，实现两种维护方式共享同一依赖、制品和发布链。

## Feature 关联

| 上游 | 本 Feature | 下游 |
|---|---|---|
| F6/T01 快照、F6/T02 关系编辑、既有 `ModelingDbtCompiler` | 可视化转换契约与确定性生成 | F8 依赖感知物化；F5 双模式发布；Sprint-93 血缘/资产证据 |

## 范围分层

- T01 收敛单来源映射：字段、cast、filter、dedup。
- T02 增加多依赖转换：join、groupBy、aggregation、dimensionRefs。
- T03 统一预览、提交制品与手工/ZIP 语义对账。

可视化配置只允许白名单操作，不接受自由 SQL 表达式；超出表达能力的模型应显式选择代码维护，不伪装成可视化可编辑。

## Task 列表

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 补齐字段映射、类型转换、过滤与去重设计 | CODE_COMPLETE | F6/T01、F6/T02 |
| T02 | 补齐关联、维度引用、分组与聚合编译 | CODE_COMPLETE | T01 |
| T03 | 统一生成制品预览、提交与三路径等价性 | CODE_COMPLETE | T02、F6/T03 |

## Definition of Ready

- [x] 转换白名单、结构化 settings 与禁止自由 SQL 边界已定义。
- [x] 唯一 compiler/validator/freeze/import owner 已指定。
- [x] F6/T01 dependency snapshot 与 F6/T02 关系编辑已完成源码实现与聚焦自动化。
- [ ] F0/T03 的 FACT/DWS/ADS 粒度、关联键和期望结果已归档。

## 完成标准

- [x] DIM、FACT、DWS、ADS 的基础转换均能由 UI 表达并确定性编译。
- [x] 生成 SQL 的 source/ref 图与 F6 snapshot 完全一致，不能暗加或遗漏依赖。
- [x] DESIGNER 与 DBT_MANAGED 仅在实现所有权上不同，候选/物化/治理不分叉。
- [x] 超出白名单的转换 fail closed，并引导显式接管代码，不丢 ModelSpec。

## 代码证据（2026-08-18）

- `ModelVisualTransformationFields.test.tsx` 与 `modelWorkbenchService.test.ts` 覆盖空配置编辑、结构化保存/回放和只读接管。
- `ModelingDbtCompilerTest` 与 `CanonicalModelLifecycleCompilerAdapterTest` 覆盖 cast/filter/dedup/join/group/aggregation、依赖 checksum 与确定性制品。
- 未执行：真实 DIM/FACT/DWS/ADS Chrome 创建及物理结果核验；统一留到 F5 集中 E2E。
