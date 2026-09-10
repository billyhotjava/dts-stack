# F6 编码与专项验证记录（2026-09-10）

结论：编码完成，专项测试通过；F6 全链验收尚未完成。

## 提交与环境

- 开发目录 `/opt/prod/s10/v2.2.3` 仅编辑、静态检查、提交和推送。
- 干净的 `/opt/prod/s10/deploy` 从 origin/v2.2.3 检出并 `git pull --ff-only`；使用本机 Git 对象缓存加速克隆，无未提交文件/构建产物复制。
- 功能源码：46db28be8；测试入口：d0ff03139；夹具更新：486e47cb11921bec5feb121c7b593dc64ed04843。后两次仅测试配置/夹具，无产品源码变化。
- 测试结束 deploy 工作区干净；未启动或替换 DTS 业务容器，未写当前业务数据库。

## 结果

| 验证 | 结果 | 提交/说明 |
|---|---|---|
| XML 语法、diff 空白、前端 Biome | 通过 | 部分存量告警保留，无关代码未修改 |
| GitNexus 变更影响 | 已完成 | 初次275符号/48索引文件，MEDIUM；新增文件用源码复核补充 |
| platform + analytics Maven compile | 通过 | d0ff03139，含 common |
| platform 指标专项 | 86项通过 | 首轮54通过、32项生命周期中7项夹具失败；修复固定版本夹具后32项全部通过，按唯一测试计86项 |
| analytics 语义发布专项 | 2项通过 | 486e47cb1 |
| 前端 Node 专项 | 31项通过 | 486e47cb1，未跳过 |
| 前端 Vitest 专项 | 9项通过 | 486e47cb1，未跳过 |
| 前端 `pnpm build` | 通过 | d0ff03139，包含 TypeScript 与 LEGACY_BROWSER_BUILD=1；存在既有 chunk 大小/Browserslist 告警 |
| 正式交付包/迁移/部署 | 未执行 | 不由编译通过推定 |
| 真实页面/Chrome95/角色撤权矩阵 | 未执行 | IT-36–IT-43 不能标通过 |

后端共88项、前端共40项，合计128项。首次失败均保留为实际过程，不将首轮报告改写为通过。

## 正式命令

工作目录 `/opt/prod/s10/deploy/source`：
```sh
/opt/apps/maven/bin/mvn -B -pl dts-platform,dts-analytics -am -DskipTests compile
/opt/apps/maven/bin/mvn -B -pl dts-platform,dts-analytics -am -Dtest=IndicatorQueryPlanTest,IndicatorQueryPlanPostgresTest,IndicatorCalculationServiceTest,ControlledIndicatorDerivationCompilerTest,IndicatorDerivationValidationServiceTest,IndicatorServiceLifecycleTest,DbtIndicatorGeneratorDerivationTest,CatalogModelSemanticPayloadFactoryTest,CatalogModelSemanticSyncServiceTest,SemanticPublishResourceTest -Dsurefire.failIfNoSpecifiedTests=false test
/opt/apps/maven/bin/mvn -B -pl dts-platform,dts-analytics -am -Dtest=IndicatorServiceLifecycleTest,SemanticPublishResourceTest -Dsurefire.failIfNoSpecifiedTests=false test
```
最后一条仅重跑修正的生命周期夹具及首轮因 platform 失败未执行的 analytics 测试。

工作目录 `/opt/prod/s10/deploy/source/dts-platform-webapp`：
```sh
pnpm install --frozen-lockfile --ignore-scripts
pnpm build
node --experimental-strip-types --test src/features/modeling/indicators/indicatorDefinitionContract.test.ts src/features/modeling/indicators/indicatorDefinitionWorkflow.test.ts src/pages/data-modeling/prototype/services/indicatorDefinitionBindingService.test.ts
pnpm exec vitest run src/pages/data-modeling/prototype/services/indicatorProjectionService.test.ts src/analytics/pages/semantic/SemanticCardEditorPage.source-contract.test.ts
```
Git hook 提示本机 PATH 无 lefthook，未执行 hook；以上正式验证独立执行，不把 hook 未执行当作通过。

## 数值与隔离证据

`IndicatorQueryPlanPostgresTest` 在 Testcontainers 隔离 PostgreSQL 16 中创建临时表。east 分子100/分母2=50，west分子900/分母100=9；总体1000/102≈9.8039215686，验证不是平均比率。另验证缺地区、零分母、带恶意文本的绑定参数空结果、预计算重复粒度标记。

测试镜像 `postgres:16-alpine`：imageId `sha256:75f5a96988cdf694a215073c3e9c001b706b371e2f94df3967f2efdec2787f6b`，digest `postgres@sha256:cf78e76683b9ca8c5733cbbdce6c9262b45b6767934dd0a95e671f9a0fc20685`。它是隔离测试镜像，不是 DTS 正式交付镜像，不能替代目标环境验收。

原始输出保留本机 `/tmp/f6-compile.log`、`/tmp/f6-web-build.log`、`/tmp/f6-java-tests.log`、`/tmp/f6-java-rerun.log`、`/tmp/f6-node-rerun.log`、`/tmp/f6-vitest-rerun.log`；Maven XML 报告在 deploy 对应模块 `target/surefire-reports`。

## 最终前端修正与复验

55084872b 修正从指标进入 BI 卡片时的预选时序：等待目标模型元数据就绪再选择固定版本，保留无变化的选择状态以避免重复更新。该提交仅修改卡片编辑器。

在 deploy ff-only 到55084872b 后，卡片编辑器专项1项通过（原9项中的1项，不重复计数），再次 `pnpm build` 通过（TypeScript及兼容构建，1m21s）。本次日志 `/tmp/f6-card-final-test.log`、`/tmp/f6-web-final-build.log`。后端没有新增改动，不重复构建或测试。最终专项唯一计数仍为128项。
