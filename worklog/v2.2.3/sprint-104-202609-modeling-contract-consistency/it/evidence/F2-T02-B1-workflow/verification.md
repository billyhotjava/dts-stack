# F2-T02-B1 单主动作与创作门禁验证

## 实施范围

- 代码提交 `72a03ab60`；测试对齐提交 `5319373ab`。开发区 commit/push 后，deploy `git pull --ff-only` 同一 SHA 才运行测试/构建。
- 当前工作台一个主按钮（保存/校验/提交实现/打开构建交付/创建新草稿）；工具栏位于表单之后，上方物化卡片保留证据并移除重复按钮。概念维度的保存/确认流程原有行为保留。
- 权限来自当前 model id/revision/checksum 对应 authoring-context.allowedActions；COMMIT 同时需要匹配 openDraft 的 draftId/etag、有效期限与无阻断诊断的本次验证凭据。点击时再次检查过期，失效后显示校验，不发送旧提交。
- 次级构建入口同样要求 implementation、无 pending 草稿、无 dirty 且当前上下文已读取。打开面板本身不构建/发布；实际命令继续由原服务门禁裁决。
- modelIssues 继续由父编辑器逐字段显示；新增实现/投影诊断展示。ERROR/FATAL 阻止 UI 提交，warning 不被当错误。

## 既有服务边界（后续四步复用）

只读核验：ModelAuthoringDraftService.context 复用 ModelVisualizationCapabilityEvaluator.authoringActions；能力不等于可向空 draftId 发命令，无草稿时现有 ensureSession 先创建。authoring save 只保存快照/files/activeView 与 ETag，validate/commit 独立；首建/前端 persist 尚耦合完整实现校验，W1 不能仅拆布局。W3/W4 精确命令仍见 F2-T01 冻结记录。本次未新增后端状态机、接口、迁移或四步页面。

## 检查与证据

执行目录 `/opt/prod/s10/deploy/source/dts-platform-webapp`。

| 检查 | 结果 |
|---|---|
| 首轮 Vitest 6 文件 | 61通过/4失败；失败为旧说明文字3项及 AntD 二字按钮空格1项；同一旧说明测试中的后续汇总说明断言一并对齐 |
| 两个失败文件定向重测 | 39/39 PASS；与其余未改动4文件的26项合计65项通过 |
| 生产构建 `pnpm build` | PASS，退出0，5319373ab；类型检查及legacy生产包，Vite耗时1m43s；日志 `/tmp/sprint104-b1-build.log` |
| 静态差异/格式 | git diff --check PASS；9个文件用现有 Biome 配置格式化。复用上一轮临时 stdin 配置解决嵌套root冲突，未改仓库配置 |
| GitNexus | 索引 stale 后执行 analyze 成功（145.7s）；局部Python scope解析有警告。impact/detect_changes LOW；图未列出React调用，不把零索引调用当无影响。自动改写AGENTS/CLAUDE已撤销，仅保留本轮源码/任务改动 |
| 单次聚焦 review | 次入口绕过pending限制已修并有组件测试；modelIssues遗漏意见经对照父编辑器现存显示和测试排除，未重复渲染 |
| commit hook | `Can't find lefthook in PATH`，未执行，不声称hook通过 |
| 浏览器/Chrome95/正式镜像/离线安装 | 未执行；无容器改动，F2-T02不标DONE |

测试日志 `/tmp/sprint104-b1-tests.log`、`/tmp/sprint104-b1-retest.log`。jsdom 的 getComputedStyle 伪元素提示不代表 Chrome95 证据。

首轮命令：
```sh
pnpm exec vitest run src/pages/data-modeling/prototype/modelWorkflowAction.test.ts src/pages/data-modeling/prototype/ModelWorkflowToolbar.test.tsx src/pages/data-modeling/prototype/ModelingWorkbenchEditor.test.tsx src/pages/data-modeling/prototype/ModelMaterializationStatus.test.tsx src/pages/data-modeling/prototype/Sprint91DualMode.source-contract.test.ts src/pages/data-modeling/prototype/ModelQualityEntry.source-contract.test.ts --reporter=dot
```
定向重测仅包含 ModelWorkflowToolbar.test.tsx、ModelingWorkbenchEditor.test.tsx；本次只修正测试，未重复运行未改动的4文件。

后续仍需：W1纯定义保存接线、四个独立步骤页/URL与草稿离开保护、候选多输出统一视图、质量/发布/资产/分析交付贯通与真实验收。

构建提示 Browserslist 数据陈旧及大分包，未自动升级依赖或修改构建配置。构建通过不替代 Chrome95 实机或运行容器验收。
