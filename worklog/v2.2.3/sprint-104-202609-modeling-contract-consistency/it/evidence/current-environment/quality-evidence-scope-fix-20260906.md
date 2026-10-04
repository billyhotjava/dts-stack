# 跨模型质量证据显示修复

## 根因与边界

依据 [23:04 重建复测](rebuild-retest-20260906-2304.md)。ModelPublishDialog 按 planId 获取规划当前候选；所选模型不属于候选时，原有 candidateScopeMatches 已将候选及发布动作隔离，但 ModelReleaseWorkflowPanel 的 evidence/governanceQuality 和发布页 primaryBlocker 仍直接取 workspace，导致“尚无候选”与另一模型质量通过详情同时显示。属于前端范围过滤遗漏，不据此推断数据库证据串写。

进入本轮时工作区已暂存该显示修复及两个测试。保留原改动，在其基础补齐通过/失败的四种匹配组合、质量重跑API归属以及同一弹窗切换模型的验证。代码提交 `1ca5a92d192dbd1401c5b53034e004706996dcc0`。

- 候选不匹配时：发布证据清空、治理质量为空、质量重跑回调不提供、其他候选阻断不展示。
- 候选匹配时：保留真实质量详情和既有重跑行为；调用仍携带该 scopedCandidate。未放宽后端发布/质量门禁，未改变规划或模型存量状态。
- 原有 scopedCandidate 的根模型集合规则复用，自动依赖不另建一套范围判定。

## 验证

开发目录只静态检查、格式、commit/push；部署目录 ff-only 到 1ca5a92d1 后执行：

```sh
pnpm exec vitest run src/pages/data-modeling/prototype/ModelWorkbenchDialog.release.test.tsx src/pages/data-modeling/prototype/ModelReleaseWorkflowPanel.governance-quality.test.tsx --reporter=dot
pnpm build
```

结果：2文件，43 PASS、5 SKIP（原 legacy advanced dbt draft lifecycle describe.skip，未计作通过）；生产类型检查与legacy构建PASS，退出0，Vite 1m31s。日志 `/tmp/s104-quality-scope-tests.log`、`/tmp/s104-quality-scope-build.log`。jsdom伪元素getComputedStyle提示不作为浏览器验收。

GitNexus旧索引从7ad35d0重建成功；impact和提交前detect_changes为LOW。React图未列直接调用，实际范围依源码核对。生成的AGENTS/CLAUDE统计改动已撤销。git diff --check PASS。commit hook仍提示缺少lefthook，未宣称hook通过。

## 尚未解决

本轮仅交付跨模型展示修复，尚未重建/部署新的运行镜像，尚未对新修复运行Chrome/Chrome95页面验收。用户此前重建的运行镜像不等于包含本提交。全Sprint保持IN_PROGRESS。

原复测另外两项保持未通过：目录同步 ANALYTICS_SEMANTIC_PUBLISH_HTTP_400（dataSourceName=null），以及 UNKNOWN 派发导致 MODEL_OPERATIONAL_RUN_CONCURRENT。未强制清理派发、未修改其并发保护、未将旧物化成功当本轮成功。
