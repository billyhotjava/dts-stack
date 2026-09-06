# 立项复审证据

**记录日期**：2026-09-06。以下是本对话此前复审的结果汇总，本次创建 sprint 未重跑构建、测试或远程物化。

## 已观察结果

1. 当前 Node 契约测试在复审时运行：37 项，24 通过、13 失败。
   命令（目录 source/dts-platform-webapp）：
   `node --experimental-strip-types --test src/features/modeling/contracts/modelSpecV2Contract.test.ts`。
   原始临时日志为 `/tmp/dts-model-review-contract.log`；它不是长期归档，T01 需重新运行并保存到本 sprint 的 IT 证据目录。
2. Schema 的 interactiveCreateModelSpecRequest/createModelSpecCommand/updateModelSpecCommand/modelSpecView 均未声明 businessProcessId 和 subjectDomainId，同时禁止额外字段。
3. 使用已有编译类执行复合键模型，grain.keys 为 project_id、month_id，实际 tests.yml 关键输出如下；证明检查只使用首列，未执行真实数据库质量任务。

```yaml
tests:
  - unique:
      column_name: project_id
```

## 13 项失败归因要求

测试名、期望、实际错误码、实现/Schema/样例/环境分类、责任 task、修复后结果逐项登记。已见 businessProcessId 必填与旧样例冲突，以及 Schema 拒绝 subjectDomainId。
不得将 13 个失败直接宣称为 13 个独立产品缺陷，也不能全部归为旧测试。

## 证据边界

- 源码复审支持 C01–C05、C10；没有当前远程部署、Chrome 95、完整物化/质量证明。
- 历史提交 a33ba6b0a 的专项测试/生产构建不计为本 sprint 的新通过证据。
- 计划阶段未新增/修改业务数据，未启动构建、部署或运行任务。


## 2026-09-06 源码归因与首批修复

Node v24.14.1，无构建的原生契约验证在开发目录执行。原始结果 37/24/13 已重现；新增错误 UUID、错误模型归属、Schema 四种 wire shape 及交互创建反例后 40/40 通过。原始、RED、GREEN 输出见 `../it/evidence/T02-contract/`。

| 序号 | 原失败测试 | 归因 | 责任 | 首批结果 |
|---|---|---|---|---|
| 1 | canonical create and update accept the additive warehouse-layer selection | TS 更新套用稳定上下文必填，与 Java 更新不一致 | T02 | 已通过 |
| 2 | canonical update is a full replacement without create-only or retired metadata | TS 更新套用稳定上下文必填，与 Java 更新不一致 | T02 | 已通过 |
| 3 | all four model types satisfy their deterministic save boundary | TS 更新套用稳定上下文必填，与 Java 更新不一致 | T02 | 已通过 |
| 4 | direct physical inputs are limited to ODS, STG or DWD | TS 更新套用稳定上下文必填，与 Java 更新不一致 | T02 | 已通过 |
| 5 | all four model types accept their minimal request with unrelated collections absent | TS 更新套用稳定上下文必填，与 Java 更新不一致 | T02 | 已通过 |
| 6 | FACT draft requires grain but may defer both physical sources and upstream models | TS 更新套用稳定上下文必填，与 Java 更新不一致 | T02 | 已通过 |
| 7 | standard bindings must resolve to declared fields | TS 更新套用稳定上下文必填，与 Java 更新不一致 | T02 | 已通过 |
| 8 | a field accepts only one standard binding | TS 更新套用稳定上下文必填，与 Java 更新不一致 | T02 | 已通过 |
| 9 | APPLICATION requires a consumption scenario and other model types reject one | TS 更新套用稳定上下文必填，与 Java 更新不一致 | T02 | 已通过 |
| 10 | only FACT accepts optional business activity | TS 更新套用稳定上下文必填，与 Java 更新不一致 | T02 | 已通过 |
| 11 | shared fixtures keep Java and TypeScript validation issue codes aligned | TS 更新套用稳定上下文必填，与 Java 更新不一致 | T02 | 已通过 |
| 12 | model views are a strict contract-version and compatibility-mode discriminated union | Schema 漏声明合法字段 | T02 | 已通过 |
| 13 | JSON Schema enforces all four model-type save boundaries | Schema 漏声明合法字段 | T02 | 已通过 |

原 APPLICATION 必填测试通过的是测试文件内部的“create”包装器，实际调用 update。该用例已改为直接断言可编辑更新允许 null/缺失，依据 Java validateUpdate 的 STABLE_CONTEXT_REQUIRED_ISSUE_CODES 过滤；完整交付仍由 ModelSpecBusinessContextContractTest 的 validateDeliverableCreate 必填用例约束，未放宽后端完整交付。Java 测试本次尚未运行。

首批仅修复 TS/Schema 结构与编辑上下文边界。阶段全矩阵、草稿 CAS/存储、键集合治理、T03–T07 及浏览器/物化仍待后续实现验收。
