# DTS 架构与工作流程参考

日期：2026-09-12。源码基线：`2e91f12f5b3709f9892dff4d88d4689b1dabfc02`。

本目录供团队理解服务职责、数据流转、审批行为和交付边界。图 01—04 是当前源码／配置的高层梳理，图 05 是项目交付约定。实际部署拓扑与业务成功情况需另外验证。

## 阅读入口

**统一入口：[打开架构与流程门户](index.html)。** 保留整个 `ref` 目录，用浏览器打开 `index.html` 即可离线阅读，无需安装 Archify。门户提供五个主题的导航、前后切换、图源入口和独立打开原图；也可以使用下表直接阅读单张图。JSON 用于后续维护；图示 HTML 自带中文界面、缩放、主题切换和导出功能。

门户支持主题定位，例如 `index.html#model-to-bi` 直达模型发布到分析。浏览器前进／后退可恢复主题。配套 Markdown 与 JSON 链接在新标签页打开，浏览器也可能将其下载后交由本地编辑器阅读。

| 顺序 | 图与说明 | 可维护源文件 | 阅读重点 |
|---|---|---|---|
| 01 | [架构总览](01-architecture.html) | [architecture JSON](01-architecture.architecture.json) | 管理、治理、接入、分析与基础设施的职责 |
| 02 | [数据业务主流程](02-data-mainline.html) | [dataflow JSON](02-data-mainline.dataflow.json) | 接入目标、模型输入、物化产物与发布契约的区别 |
| 03 | [权限与通用审批](03-approval.html) | [workflow JSON](03-approval.workflow.json) | 常规审批路径、拒绝、执行失败和审计 |
| 04 | [模型发布到分析](04-model-to-bi.html) | [sequence JSON](04-model-to-bi.sequence.json) | 后台语义同步与后续分析读取契约的时序 |
| 05 | [团队交付流程](05-delivery.html) | [workflow JSON](05-delivery.workflow.json) | 开发、构建测试、正式发布与真实业务验收的交接 |

配套材料：[源码依据与限制](evidence.md)、[源码文件校验和](source-baseline.json)、[校验与交接记录](handoff.json)。每张图另有 `.receipt.json` 生成回执、`.validation.json` 校验结果、`.visual-check.json` 浏览器检查和 `.visual-check.html` 截图索引。

## 三个需要共同理解的边界

1. **物理表、模型发布、BI 数据集是不同对象和阶段。** 接入完成不会自动证明模型发布成功；模型物理产物也不自动等于 BI 中可消费的契约。图 02 表示能力衔接，具体任务、资产、修订和版本仍需实例对照。
2. **认证角色不等于动作权限已经完善。** 图 03 展示通用变更接口的当前实现。管理 API 按三员角色集合放行，不代表每种资源动作都已满足职责分离；自审、非法前态和并发决定仍需针对性验证。
3. **源码、配置声明、运行事实和验收结果分别记录。** 图 01 省略次要调用与存储连线；不能从 Compose 声明、目录存在或一张通过图形校验的图推断在线服务状态。

## 团队如何协作

以下是建议的责任角色，尚未分配具体同事。每条跨服务关系由两端模块维护者核对，保持接口、错误处理与数据所有权的描述一致。

| 责任范围 | 建议核对内容 | 主要材料 |
|---|---|---|
| 前端维护者 | 管理／平台入口、分析路由、界面权限与实际 API 的对应 | 01、03 |
| 管理与安全维护者 | 组织角色、三员动作、申请审批、内部凭据、审计记录 | 01、03 |
| 平台治理与建模维护者 | 资产身份、来源绑定、质量与密级、模型修订、物化及发布 | 01、02、04 |
| 接入与数据执行维护者 | reader/writer、API/文件分支、Airflow、Addax、元数据条件 | 01、02 |
| 分析维护者 | 语义发布入口、数据集版本引用、权限与查询消费 | 02、04 |
| 测试与发布维护者 | 正反例、失败恢复、同 SHA 制品、正式部署、真实业务验收 | 03、04、05 |

第一次分享建议先讲图 01，再用图 02 与图 04 解释一条业务链，最后对照图 03 与图 05 确认协作边界。会上记录具体负责人、待确认关系和决策，不把建议分工写成已经接受的任务。

后续变更流程：修改对应 JSON → 更新 `evidence.md` 的源码依据与基线 → 校验及生成 HTML → 阅读浏览器截图 → 一次完整的文档评审。JSON、HTML、说明和回执一起纳入常规 Git 变更。提交前仍遵循仓库的 `gitnexus_detect_changes` 要求。

## 用 Archify 继续维护

本次使用 Archify 稳定版 `2.16.0`，本机路径为 `/home/billy/.agents/skills/archify`。同事只阅读 HTML 时不需要安装；生成或修改图时需要 Node.js 与该 skill。参见[上游项目](https://github.com/tt-a1i/archify)和[本次版本](https://github.com/tt-a1i/archify/releases/tag/v2.16.0)。

可直接在 DTS 会话中使用：

> 使用 $archify，核对当前 DTS 源码与指定图的 evidence.md 依据，更新 worklog/v2.2.3/ref 中对应主题的 JSON 和 HTML。沿用稳定节点 ID，区分源码事实、配置声明、运行事实和方案建议。只调整有证据变化的关系，完成 validate、deliver、浏览器检查并更新交接记录。

需要深化业务流程时：

> 使用 $archify，将 worklog/v2.2.3/ref 中的数据主流程细化为一条具体任务与模型的链路。明确输入资产、模型修订、物化产物、QueryDataset 版本和分析消费者。核对失败、重试、撤销与权限边界，未知事实写入待确认清单，输出到同目录。

以下命令从仓库根目录进入参考目录，复现其中一张图；其余图替换类型和文件名即可。这些命令生成图文材料，不执行 DTS 编译或部署。

```bash
cd worklog/v2.2.3/ref
archify_cli="${HOME}/.agents/skills/archify/bin/archify.mjs"
ARCHIFY_UPDATE_CHECK_DISABLED=1 node "$archify_cli" validate architecture 01-architecture.architecture.json --quality showcase --json > 01-architecture.validation.json
ARCHIFY_UPDATE_CHECK_DISABLED=1 node "$archify_cli" deliver architecture 01-architecture.architecture.json 01-architecture.html --quality showcase --json > 01-architecture.receipt.json
ARCHIFY_UPDATE_CHECK_DISABLED=1 node "$archify_cli" visual-check 01-architecture.html --json
```

按顺序执行，每一步先确认退出码为 0 再继续。重新生成后更新截图和 `handoff.json` 中的 SHA-256 与人工阅读结论，不沿用旧回执。

## 共享与本轮状态

推荐复制或打包整个 `ref` 目录，同事解压后打开 `index.html`；门户与原图使用相对路径，请保留文件名与相对位置。也可以直接分享单张图的 HTML，或分享 JSON、说明和回执供同事协作维护。内网静态文档站点可承载这些文件；Archify 本地产物不承担多人同时编辑、账号或评论流，这些由 Git 或团队文档平台负责。

本轮完成五张图、源码依据和文档校验。引用的 28 个源码／配置文件已与上述提交逐一比对一致；工作区其他并行源码改动未纳入本轮基线。未执行正式 DTS 构建测试、产品交付包制作、容器发布或真实登录业务验收；这些阶段不因文档完成而视为完成。资料尚未提交、推送或发送给同事。目录中原有参考文档保持原样，本次未将其内容作为当前源码事实。
