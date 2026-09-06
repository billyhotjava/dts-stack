# T10-B2 首次模型设计保存验证

## 范围与提交

- 源码 `6380718d1`；测试修正与 Maven 测试白名单 `242009b0f`，测试 mock/错误断言修正 `64fca8f66`。开发目录 `/opt/prod/s10/v2.2.3` 仅编辑、格式、静态检查、提交推送；部署目录 `/opt/prod/s10/deploy` 拉取同 SHA 后执行测试和构建。
- 新建模型通过同一原子 draft-operations 接口的 DEFINITION_ONLY 模式保存逻辑定义，不创建实现、不推进发布。原完整保存模式不变。
- 汇总/应用缺上游允许首次保存，其他字段/引用校验保留；内部幂等键为 `dm:v2:definition:<sha256>`，外部不能提交保留键。逻辑保存与完整保存不会共享重放键。
- 单次聚焦 review：保留命名空间问题已修，最终无剩余实质发现。未运行第二轮全量 review。
- 编辑器/页面抽出概念维度表单、导航纯函数；行数降至 747/795。前端按既有 Biome 配置格式化；未改仓库格式配置。git diff --check PASS，提交前 GitNexus detect_changes LOW；共享 Contract 的预先 impact HIGH 已告知。

## 验证结果

| 检查 | 结果 |
|---|---|
| 前端 W1 保存、已有保存、编辑器、双模式契约 | 4 文件 113/113 PASS，6380718d1 |
| 时间字段组件专项 | 3/3 PASS，6380718d1 |
| 抽出的导航纯函数 | 1/1 PASS，242009b0f（同文件其他8项本次定向重跑跳过） |
| 前端生产构建与类型检查 | pnpm build PASS，6380718d1，Vite 1m41s；后续提交仅测试及测试白名单，无应用源码变更 |
| 后端首轮 | 141项，140通过/1断言失败；ApplicationService 76/76、StageGate 42/42通过；Contract空字段断言不符合已有可编辑草稿约束，改用重复字段验证 FIELD_INVALID |
| 后端补跑 | 40项中37通过，2个Mockito嵌套stubbing错误及1个API错误码字段断言失败；Contract 23/23、RequestDecoder 4/4通过；测试自身问题已于64fca8f66修正 |
| 后端保存入口定向重测 | 14/14 PASS（保存服务9项、REST5项），64fca8f66，BUILD SUCCESS 1m11s |
| 旧前端总览源契约 | 原整文件9项中3失败：导航因导入页面触发localStorage已改为导入纯函数并通过；另外2项为已有偏差，见下文，未改动对应业务 |
| 正式镜像/运行容器/Chrome95/离线安装/E2E | 本轮未执行，不据源码或构建宣称验收完成 |

合并未发生后续变更的定向结果：后端159项通过（76+42+23+4+14），前端相关专项117项通过（113+3+1）；这不表示旧前端总览整文件通过。

旧前端总览的两项偏差已用 `git show 7ad35d085:<path>` 对照改动前源码：编辑器已使用 ModelImplementationExecutionFields、不含断言要求的“存储策略”；MetricsPage 原有 expressionSql 与旧的禁止该字段断言冲突。保留测试失败证据，未为本切片修改指标模块或放宽旧总览测试。

首轮后端未实际执行 ModelDraftSaveApplicationServiceTest、ModelDraftOperationResourceTest，因为 pom 的 testIncludes 缺少这两项。现已将两项正式加入构建配置。原命令中的 ModelSpecUpdateRequestDecoderTest 不存在，实际解码测试为 ModelSpecRequestDecoderTest；补跑使用正确名称。不能把测试选择参数当执行证据。

## 命令与日志

全部正式执行位于 deploy：

```sh
# frontend webapp directory
pnpm exec vitest run src/pages/data-modeling/prototype/services/modelDefinitionCreation.test.ts src/pages/data-modeling/prototype/services/modelWorkbenchService.test.ts src/pages/data-modeling/prototype/ModelingWorkbenchEditor.test.tsx src/pages/data-modeling/prototype/Sprint91DualMode.source-contract.test.ts --reporter=dot
pnpm exec vitest run src/pages/data-modeling/prototypeReplacement.source-contract.test.ts src/pages/data-modeling/prototype/ModelImplementationBindingFields.test.ts --reporter=dot
pnpm exec vitest run src/pages/data-modeling/prototypeReplacement.source-contract.test.ts -t 'keeps workbench route selection and navigation guards deterministic' --reporter=dot
pnpm build
```

前端首轮另带了两个未匹配到文件的过滤路径（BindingFields扩展名、prototypeReplacement目录），以实际4文件113项计；第二条命令已按正确路径补跑。

日志：`/tmp/sprint104-b2-frontend-tests.log`、`/tmp/sprint104-b2-remaining-tests.log`、`/tmp/sprint104-b2-navigation-retest.log`、`/tmp/sprint104-b2-build.log`。

后端使用既有 `maven:3.9.9-eclipse-temurin-21` 工具镜像，仅挂载 deploy/source 和 Maven 缓存，`--rm` 执行，不修改运行应用容器。补跑选择：ModelDraftSaveApplicationServiceTest、ModelSpecContractTest、ModelDraftOperationResourceTest、ModelSpecRequestDecoderTest；命令 `mvn -B -Dmaven.repo.local=/home/billy/.m2/repository -s /home/billy/.m2/settings.xml -pl dts-platform -am -Dtest=<上述列表> -Dsurefire.failIfNoSpecifiedTests=false test`。日志 `/tmp/sprint104-b2-backend-tests.log`、`/tmp/sprint104-b2-backend-retest.log`。最终仅重跑保存应用服务和REST入口两类，日志 `/tmp/sprint104-b2-boundary-retest.log`。

commit hook 提示 `Can't find lefthook in PATH`，未声称 hook 已执行；生产包出现已有 Browserslist 数据陈旧、大分包提示，无依赖升级。

## 剩余边界

首次 W1 保存入口已编码；完整四阶段页面、已有草稿按步骤编辑/跨步离开保护、多输出统一交付视图、质量/发布/资产/分析贯通及真实验收仍未完成。现有模型继续使用原实现编辑器，不把 URL step 解释为生命周期状态。T10/T14 与 sprint 整体保持未完成。
