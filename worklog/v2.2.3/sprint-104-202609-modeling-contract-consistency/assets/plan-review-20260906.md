# Sprint-104 方案复审处置

一次独立方案复审（单 reviewer）及同次范围澄清，未进行重复代码 review。

| 问题 | 处置 |
|---|---|
| T09 未全冻结，下游不应整体开工 | 分任务保留 DoR；T10-A 仅消费已冻结帮助协议 |
| DTO 缺 outputs/部分失败/完整 wizard 字段 | T10-B 保持 DRAFT，冻结完整结构后实现，不用单 resourceId 冒充多输出 |
| 资产全量 PUT 无安全局部更新协议 | T12 保持 DRAFT，不先做会覆盖其他字段的简化表单 |
| S07 旧 publish-intents 混入新向导 | 修正为只读兼容；W3 quality、W4 publish 明确分离 |
| T10 范围过大 | 拆 T10-A 帮助/说明迁移与 T10-B 聚合/向导；不新增顶级任务数 |

复审结论：T10-A 与上述业务写入契约没有依赖，可独立编码。原无 step 页面仍解析 model-center，并需在抽屉内可发现 model-implementation 的时间说明。T10-A 不代表四步页面、资产写入或发布重构已实现。

本轮浏览器只读基线：已登录 bi.yuzhicloud.com 的模型工作台正常呈现，右上角 aria-label=打开帮助 入口存在。Chrome95 与改后行为需要在部署目录构建后专项验证；当前基线不是新功能通过证据。
