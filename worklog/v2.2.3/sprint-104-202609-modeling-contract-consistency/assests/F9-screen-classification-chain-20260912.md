# F9 大屏消费密级链核对（2026-09-12）

状态：只读源码核对完成；S12/S26 真实账号请求与页面验收 NOT_RUN。本轮没有修改 analytics 大屏的 ACL、人员密级或越级模型。

依据为 [权限链契约 C89/K83](F9-permission-chain-contract.md) 和 [实施基线的大屏章节](F9-implementation-baseline-20260912.md)。模型发布的目录资产/分析数据集映射继续沿用产品既有链；ADS EDITOR 只改变模型维护能力，不成为数据读取凭据。

ScreenPermissionService 优先使用平台 asset_grant，analytics_screen_access 为迁移期只读回退。OWNER/MANAGER 管理权不绕过人员密级；仅 VIEWER 的 level_override 参与既有越级查看，并携带审计标记。AnalyticsConsumerClassificationService.requireCurrentScreen 对未解析上游及失效密级证据拒绝消费。

仍需正式制品部署后，以低密级员工、低密级 EDITOR、普通查看者分别验证：先建屏再提高上游密级；越级仅限原大屏；另屏、直接目录/查询/导出仍拒绝；确认最终派生密级和审计事实。当前没有真实 HTTP/截图证据，IT-62 不记 PASS，缺口归 F9-G03。
