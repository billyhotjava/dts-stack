# Sprint-104 集成验收

**状态**：独立验收样例已初始化；IT-04 真实 dbt 专项通过；其余模型页面场景正在执行，未宣称整体验收通过。
**注意**：本文件是可执行走查要求，不是验收通过证据。

## 场景矩阵

| ID | Task | 操作及正反分支 | 通过标准 | 状态 |
|---|---|---|---|---|
| IT-01 | T01/T02/T08 | 四类模型在草稿、设计、物化、发布阶段分别输入完整/未完整/非法字段 | 同阶段 Java/TS/Schema 一致；非法字段不被静默忽略 | 未执行 |
| IT-02 | T05/T08 | 修改历史绑定和层级→暂存→关闭→恢复→提交，重复两轮；另测 TYPE2→NONE 与版本冲突 | 配置完整，冲突不覆写，专属字段正确清理 | 两轮纯profile恢复、NONE清理、版本冲突、提交修订PASS；普通FULL构建另行失败 |
| IT-03 | T06/T08 | 旧增量和分区模型→当前 FULL/[]→保存→重开→编译；再测无键增量 | FULL/[] 不回退；无键增量明确拒绝 | 未执行 |
| IT-04 | T04/T08 | 项目/月组合键，跨月合法/同月重复/键空值/交换顺序；执行真实 dbt tests | 完整组合唯一性正确，各必需键非空，无假首列唯一 | dbt专项PASS；模型端到端待验 |
| IT-05 | T03/T08 | 主键度量已绑、属性未绑；三种覆盖策略；标准版本失效/服务不可用 | 必绑范围与证据检查一致，失效/未知有明确提示 | 未执行 |
| IT-06 | T07/T08 | 遍历能力矩阵，尝试不支持的来源/加载/历史执行 | 页面与后端一致，物化前明确阻断，不伪装支持 | 未执行 |
| IT-07 | T02–T08 | 两字段无时间明细、无键全量应用、无分组 COUNT/SUM，连续两次物化并查看质量/版本证据 | 不补造字段；执行行为遵循既有幂等/并发协议；结果绑定当前版本 | 新独立无时间明细首次物化/质量/发布登记PASS；再次运行及旧取消候选重新构建受阻 |

## 执行入口（实施时记录实际命令和退出码）

编译前必须先在 `/opt/prod/s10/v2.2.3` commit/push；在 `/opt/prod/s10/deploy` 确认分支及工作区，执行 `git pull --ff-only` 并核对 SHA。禁止复制未提交源码/产物。Node 原生无编译契约检查可在开发目录执行；以下构建与转换测试入口固定在部署目录。

前端目录 `/opt/prod/s10/deploy/source/dts-platform-webapp`：

```sh
node --experimental-strip-types --test src/features/modeling/contracts/modelSpecV2Contract.test.ts
pnpm exec vitest run src/pages/data-modeling/prototype/services/modelWorkbenchService.test.ts --reporter=dot
pnpm build
```

后端目录 `/opt/prod/s10/deploy/source`，沿用仓库正式 Maven/JDK 21 入口；target 权限或工具链不匹配时记录为部署目录基线缺口，不移到开发目录或任意临时目录编译：

```sh
mvn -pl dts-platform -am -Dtest=ModelSpecContractTest,ModelSpecStageGateServiceTest,ModelLifecycleContractTest,ModelSpecCompilerProjectionTest,ModelingDbtCompilerTest,ModelImplementationExecutionPlannerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

新增 adapter/草稿测试按 T03/T05 落点加入；Node 原生测试不得交给 Vitest。实际执行及通过结果见 [源码专项证据](evidence/source-test-summary-20260906.md)。

## 证据要求

每项记录：时间、提交/镜像、环境、租户脱敏标识、模型 revision/checksum、实现 revision/checksum、草稿或候选/运行 ID、幂等键、请求返回码、实际 SQL/检测结果和浏览器截图。
将真实输出归档为 `it/evidence/IT-xx/`，目录在实际执行时创建。分开报告 source/test/build/deployed/browser/runtime。
同键重复、不同键再次执行、运行中再次点击分别遵循现有接口协议；T01 固定基线，T08 复验，不能硬编码“任何两次点击只产生一个任务”。

## 停止条件与交付

认证/密级/跨租户错误、未知执行目标、数据覆盖风险或新迁移需求出现时停止对应操作并报告。
目标环境与离线交付未授权前，不传包、不重建容器。T08 补充实际 release-plan 和 runbook；本计划不构成部署授权。


## 首批实现检查

见 [T02 验证记录](evidence/T02-contract/verification.md)及[源码专项证据](evidence/source-test-summary-20260906.md)：源码契约40/40、部署目录前端92/92、后端99/99。IT-04 见 [dbt真实证据](evidence/IT-04/verification.md)；不能替代其他模型页面场景。正式交付构建使用部署目录的 builds/dts-build.sh / builds/dts-platform-webapp/Dockerfile，不把单独 pnpm build 等同于完成交付包。

最新页面证据见 [新独立明细验收](evidence/current-environment/fresh-detail-acceptance.md)。旧草稿校验/提交修复已在2161b2cda真实页面通过；目录同步、再次运行、取消候选重新构建仍有未通过分支。
