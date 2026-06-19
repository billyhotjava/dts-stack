# T01: 转换→dbt model 生成映射

**优先级**: P1
**状态**: READY
**依赖**: F1-T02（节点 config 提供转换语义）

## 目标

实现"转换作业 → dbt model"的 mock 生成映射：从节点 `config` 推导出一组 dbt model 数据结构（mock），作为 S4→S6 的数据契约，供 S6"查看生成的 dbt"抽屉只读消费。

## 技术设计

- 库/栈：纯 TS 映射函数 + mock fixtures；无 UI（本 sprint 不渲染抽屉）。
- 文件：
  - `app/src/canvas/dbt/generateDbtModels.ts`（纯函数：转换作业 → `DbtModel[]`，不可变，无副作用）。
  - `app/src/canvas/dbt/types.ts`（契约类型：`DbtModel = { name; sql; refs: string[]; sources: string[]; materialization: 'view'|'table'|'incremental'; columns: ColumnSchema[] }`）。
  - mock：`transformService.getGeneratedDbt(jobId): Promise<Result<DbtModel[]>>`（S6 消费入口）。
- 关键实现点：
  - 映射规则（mock）：源表节点→dbt `source()` 引用；清洗/连接/聚合/过滤→中间 model（SQL 由 config 模板化生成）；输出节点→最终 model；节点连线→`ref()` 上下游引用，物化方式由节点类型推导（输出=table，中间=view）。
  - SQL 文本为**可读 mock**（模板拼接，体现 dedupe/join/group by/where 语义），无需真实执行。
  - 纯函数 + 不可变；每次作业变更可重新生成，结果稳定可快照。
  - **不**在主流程任何用户界面暴露 dbt；仅 `getGeneratedDbt` 经 mock service 暴露。
- mock service：`transformService.getGeneratedDbt` 复刻 `Result<T>` 契约；`VITE_USE_MOCK` 默认开。

## 影响范围

- 新增 `app/src/canvas/dbt/generateDbtModels.ts`、`types.ts`。
- 扩展 `mock/services/transformService.ts`（getGeneratedDbt + 映射调用）。
- 输出 S4→S6 契约（`DbtModel[]` 形状），S6"查看生成的 dbt"抽屉消费。

## 验证

- [ ] 给定样例作业（销售准备项目：PLM 订单+ERP 客户→去重/连接→ODS 宽表），生成自洽 `DbtModel[]`。
- [ ] model 含 name / sql / refs / sources / materialization / columns，引用关系与画布连线一致。
- [ ] `generateDbtModels` 为纯函数，相同输入产相同输出（可快照测试）。
- [ ] 用户界面无 dbt 暴露；仅 `getGeneratedDbt` 可取映射。

## 完成标准

- [ ] 映射数据结构稳定、自洽，足以供 S6 只读渲染。
- [ ] 纯函数 + 不可变，走 `transformService.getGeneratedDbt` mock。
- [ ] S4→S6 契约（类型形状）清晰，dbt 对普通用户隐藏。
