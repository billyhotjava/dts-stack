# Sprint-104 集成验收

**状态**：IN_PROGRESS。独立验收样例已初始化；IT-04 真实 dbt 专项通过，IT-07 运营再次运行已通过，F2 仅有分项 Chrome/正式测试证据，未宣称整体验收通过。
**注意**：本文件是可执行走查要求，不是验收通过证据。

最新编码与构建结果见 [编码收尾记录](evidence/current-environment/coding-completion-20260907.md)；用户操作见 [手工验收用例](手工验收用例-20260907.md)。源码 `32b7e1309` 已通过本轮定向测试和前端构建，当前运行容器仍为 `296`，不将源码修复预填为页面通过。

## 场景矩阵

| ID | Task | 操作及正反分支 | 通过标准 | 状态 |
|---|---|---|---|---|
| IT-01 | T01/T02/T08 | 四类模型在草稿、设计、物化、发布阶段分别输入完整/未完整/非法字段 | 同阶段 Java/TS/Schema 一致；非法字段不被静默忽略 | 未执行 |
| IT-02 | T05/T08 | 修改历史绑定和层级→暂存→关闭→恢复→提交，重复两轮；另测 TYPE2→NONE 与版本冲突 | 配置完整，冲突不覆写，专属字段正确清理 | 两轮纯profile恢复、NONE清理、版本冲突、提交修订PASS；普通FULL构建另行失败 |
| IT-03 | T06/T08 | 旧增量和分区模型→当前 FULL/[]→保存→重开→编译；再测无键增量 | FULL/[] 不回退；无键增量明确拒绝 | 未执行 |
| IT-04 | T04/T08 | 项目/月组合键，跨月合法/同月重复/键空值/交换顺序；执行真实 dbt tests | 完整组合唯一性正确，各必需键非空，无假首列唯一 | dbt专项PASS；模型端到端待验 |
| IT-05 | T03/T08 | 主键度量已绑、属性未绑；三种覆盖策略；标准版本失效/服务不可用 | 必绑范围与证据检查一致，失效/未知有明确提示 | 未执行 |
| IT-06 | T07/T08 | 遍历能力矩阵，尝试不支持的来源/加载/历史执行 | 页面与后端一致，物化前明确阻断，不伪装支持 | 未执行 |
| IT-07 | T02–T08 | 两字段无时间明细、无键全量应用、无分组 COUNT/SUM，连续两次物化并查看质量/版本证据 | 不补造字段；执行行为遵循既有幂等/并发协议；结果绑定当前版本 | 新独立无时间明细首次物化/质量/发布登记PASS；旧取消候选重新构建/关系核验PASS；已发布模型再次运行：binding v2 canonical DAG 下新 dispatch COMPLETED、四个 Airflow 任务成功；旧失败 dispatch 保留未重置 |

## F2 新增验收（规划，尚未执行）

共同输入/期望来自 [统一契约 S01–S10、W1–W4、N01–N08、I01–I06](../assets/delivery-workflow-contract.md)。每例同时核对界面允许动作、服务端实际校验与持久化结果；同名状态标签不足以判定一致。

| ID | Task | 操作及反向分支 | 通过标准 | 状态 |
|---|---|---|---|---|
| IT-08 | T09/T10/T13/T14 | 资产已登记、模型已发布，分析缺源/失败；分别打开列表/面板/目录并刷新 | 资产已登记可维护，分析失败原因准确；GET 无写副作用，旧 serving API 仍兼容 | 部分通过：W4 读取/保存/冲突与目录读取通过；目录维护 capability、分析失败状态仍待新版复验 |
| IT-09 | T09/T11/T14 | 物化后无规则→当前页绑定适用已发布规则→执行；另测未登记/错误目标/未发布规则/旧规则证据 | 待登记、待配置、运行中、数据失败、执行异常分开；只有当前目标/版本的有效证据放行 | 未执行 |
| IT-10 | T11/T14 | 从模型新建/复杂编辑规则→保存→返回→检查；加入 SQL 作用域、方言错误与连接异常 | 带正确输出表，不默认上游表；错误可定位；返回保留模型/候选/输入，绑定不冒充通过 | 未执行 |
| IT-11 | T12/T14 | 模型面板维护负责人→目录核对→目录修改→模型重读→重发布；另测并发窗口 | 同一 owner、未编辑字段不丢、人工字段不被重发覆盖；冲突拒绝；正确区分模型与物理资产编辑 | 部分通过：W4 描述保存、目录读取和陈旧窗口拒绝已测；目录入口维护 capability 与反向分支待新版复验 |
| IT-12 | T09/T10/T11/T12/T14 | 当前草稿与发布修订不同、旧候选返回、读取失败、未知/无权；打开 legacy 深链并刷新 | 同 S01–S10 fixtures 的动作/拒绝结果一致；旧证据不放行新版本；路由不丢 modelSpecId | 部分通过：单模型历史候选范围、dev/test 隔离已测；修订失配、读取失败、未知/无权及深链完整分支待复验 |
| IT-13 | T13/T14 | 分析源不存在/已有关联；临时故障后重试、并发点击、超时重放、旧版本完成、未启用分析 | 只恢复分析步骤，无重复源/语义/查询投影；不增加物化/发布次数；不扩张权限，目标真实可查询 | 未执行 |
| IT-14 | T10–T14 | 从物化至发布/治理/分析完整走查，含 Chrome95、1366×768、关闭重开；同一正式包离线安装 | 常见操作留在对应向导步骤页；复杂编辑明确返回；镜像/迁移/必要资源齐全，无热补丁/开发挂载/启动下载 | 未执行 |
| IT-15 | T09/T10/T11/T14 | 从新建 W1 保存→W2 提交→W3 构建/配置质量/检查→W4 确认发布；含暂存失败、提交部分成功、未有 ID 的首次保存 | 每页最多一个主操作，接口/生命周期对应；缺条件无法执行；第三步通过且未确认第四步时发布提交次数为 0；刷新不重复建模 | 未执行 |
| IT-16 | T09/T10/T12/T14 | N01–N08：已有证据跨步查看、上一步编辑、未来步骤深链、浏览器前进后退、执行中离页、外部返回和未保存保护 | 导航 GET 零写入；非法跨步命令被后端拒绝；身份/版本正确；轮询不抢当前页面；草稿失败保留输入 | 未执行 |
| IT-17 | T09–T14 | I01–I06：改字段/SQL/环境/规则/治理字段/分析连接；已发布再编辑、取消候选、旧任务晚到 | 只失效相关资格且前后端一致；旧发布不被覆写；不该重构建的修改不触发构建；取消候选不能发布，历史构建可单独查询 | 未执行 |
| IT-18 | T09/T10/T14 | 逐步打开 W1–W4 右上角帮助，包含时间字段样例、未知 step 回退、输入错误、帮助关闭与离线加载 | 页面无常驻长说明；规则完整进入匹配帮助；不误删校验/阻断、不改变业务规则；关闭后输入/步骤/焦点保持；Chrome95 可用 | 仅组件/契约分项通过；Chrome95 与离线加载待执行 |

F2 证据按本文件既有要求归档到实际创建的 `evidence/IT-08` 至 `evidence/IT-18`；本次不创建占位截图/通过记录。隔离夹具用于故障注入、并发和破坏性分支，原用户模型默认只读。

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

最新页面证据见 [新独立明细验收](evidence/current-environment/fresh-detail-acceptance.md)。旧草稿校验/提交修复已在2161b2cda真实页面通过；旧取消候选重新构建已在5e00e914f页面通过，见[关系核验误报复验](evidence/current-environment/blocked-relation-check.md)；已发布模型再次运行在 canonical DAG 下完成。296 authenticated Chrome 复测已确认 W4 保存、陈旧窗口冲突和单模型候选隔离，且保留目录维护能力与分析失败状态两个 FAIL；c275 定向后端35/35、前端6/6通过，c275 正式构建和包校验已完成但未部署；后续编码收尾另见当前记录，仍待新版部署和分析正常重试复验。详见[正式验证证据](evidence/current-environment/formal-validation-and-delivery-evidence-20260907.md)。

T10-A 首个帮助切片的源码、专项测试和构建记录见 [验证记录](evidence/T10-A-help/verification.md)。IT-18 仅当前组件/契约部分通过；四步页面、Chrome95 与离线验收仍待执行。

T10-B1 单主动作、当前校验凭据与诊断显示：6 个专项文件累计65项通过（首轮26项及修正后39项），见 [验证记录](evidence/T10-B1-workflow/verification.md)。真实页面/Chrome95/离线验收未执行。
