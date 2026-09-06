# Sprint-104 源码专项测试归档（2026-09-06）

- 被测源码提交：`bd0670acc89e7dd1be82e0d12961ba7744f63ca2`。
- 证据边界：以下是已执行的源码测试摘要；不证明镜像交付、容器部署、真实租户页面、真实物化或质量运行。

## Java 专项

部署目录 `/opt/prod/s10/deploy/source` 使用正式 Docker Maven runner 执行：

```bash
docker run --rm --memory=4g -e JAVA_HOME=/opt/java/openjdk -e PATH=/opt/java/openjdk/bin:/usr/share/maven/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin -v /opt/prod/s10/deploy/source:/workspace -v /home/billy/.m2:/home/billy/.m2 -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -B -e -Dmaven.repo.local=/home/billy/.m2/repository -s /home/billy/.m2/settings.xml -pl dts-platform -am -Dtest=ModelSpecBusinessContextContractTest,ModelSpecStageGateServiceTest,ModelImplementationExecutionPlannerTest,ModelSpecCompilerProjectionTest,ModelingDbtCompilerTest,GovernanceModelSpecStandardEvidenceAdapterTest,DbtWorkspaceBootstrapTest,DbtImplementationDraftServiceSecurityTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Surefire 汇总为 **99 tests, 0 failures, 0 errors, 0 skipped**，Maven `BUILD SUCCESS`；日志完成时间为 `2026-09-06T08:10:54Z`。该轮覆盖阶段门禁、标准证据、编译投影、执行计划、草稿安全和工作台相关后端契约。

首轮的 4 个失败均是新增完整 grain/KEY 契约后暴露的测试夹具或断言滞后，不是生产实现回归；将夹具补为完整 canonical grain 集合并对齐组合唯一性断言后，才执行上述一次成功复测。

## 前端工作台专项

`/tmp/sprint104-workbench-final.log` 记录 `/opt/prod/s10/deploy/source/dts-platform-webapp` 的 Vitest 4.1.0 运行：**2 test files passed，92 tests passed**，耗时 `11.25s`。日志中的 jsdom `getComputedStyle(..., pseudo-element)` 提示不影响该次通过结果。

这 92 项只构成前端源码测试证据；Chrome 95、真实页面旅程和 API/运行时集成仍待 T08。

## 页面验收发现问题后的针对性回归

- `eeabf4f602fd2230c88acebaf15d7ce1ee4fe1d7`：AirflowDbtExecutionGatewayTest 10/10、DbtModelFieldProjectorTest 3/3；编译器旧标准追溯断言失败后，保留真实标准绑定，改为列 meta.dts_standard_element_id。
- `3fa21140e2a38eaf27c5ad797cfafb6fe2b4aff2`：部署目录 Docker Maven 的 ModelingDbtCompilerTest 19/19；modelWorkbenchPresentation.test.ts 6/6。包含历史 profile 单独变更检测及生成 YAML 标准元数据断言。
- 同提交正式双镜像/交付包构建退出0，日志 `/tmp/sprint104-final-build.log`。上述测试通过不替代修复版真实页面复测。

- `66fcd49dd8c1db3484e2358eecf1a3ae5f08df34`：DbtImplementationDraftServiceSecurityTest 27/27（0 failure/error/skip）。此前649e02400两个新增用例的Mockito嵌套stubbing错误已修复。受限旧schema升级和用户内容保护均通过；测试日志 `/tmp/sprint104-legacy-test.log`。正式构建及运行复测另行记录。

- `2161b2cda7b2782a0beed29158b9ca681c1a9d36`：补齐实际DBT_MANAGED+GENERATED+FROZEN源及实现pin约束，SecurityTest 27/27（包括错误pin/非GENERATED/用户内容保护），正式双镜像构建通过；日志 `/tmp/sprint104-frozen-test.log`、`/tmp/sprint104-frozen-build.log`。
