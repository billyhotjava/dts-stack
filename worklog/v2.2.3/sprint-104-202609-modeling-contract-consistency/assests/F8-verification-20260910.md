# F8 编码与验证记录（2026-09-10）

编码已进入 Git。F8-T01 已冻结；F8-T02–T06 代码已实现，F8-T07 仍为 IN_PROGRESS，不以源码与夹具验证代替正式部署及真实业务闭环。

## 已实现

- 新增草稿校验和试跑接口，使用当前 definition，无 ruleId/旧发布版本依赖；权限复用既有质量维护角色和资产读取边界，审计使用独立事务。草稿试跑不创建规则、版本、运行、样本或工单。
- 保存、校验和执行共享 statements/sql 解析优先级，逐条检查；目标连接前完成只读与绑定表校验。
- 单语句取消 id 去重查询；计数成功先记录违规，采样失败保留结论；多语句去重不可用保留次数但精确行数为空。
- 双维度及安全原因保存于版本化 metrics_json，DTO 清除原始 JSON 并返回安全 outcome；旧数组兼容读取。无表结构迁移。
- 详情、评分、Excel 使用统计可用性；纯故障不建业务工单，正式违规含混合故障建单，试跑永不建单。
- 发布门禁在选择最新运行前排除 DRY_RUN，新格式只接受 schemaVersion=1、PASSED+OK；候选缺规则时显示待配置。

## 源码和测试

开发目录仅编辑、静态检查、commit/push。部署目录检查干净后 ff-only 同 SHA，使用 Maven 正式模块入口及仓库前端构建命令。

| 项目 | 结果 |
|---|---|
| RED | 0f3be1e87，3 项真实失败，复现 id 依赖、采样丢失及连接前校验缺失 |
| 后端首次专项 | 21bddd73c，109 项中 102 通过、7 失败；保留首轮日志，不改写为成功 |
| 后端定向复验 | 0a59109eb，49/49 通过，含新增 2 项审计/越权测试；已修正前述 7 项 |
| 后端唯一用例计数 | 111 项；执行器、运行、绑定、审计、门禁、评分、导出与模型状态 |
| 前端 Vitest | QualitySqlPreflight、RuleEditorPage、ModelTargetQualityPanel，共 8/8 通过 |
| 前端 Node | qualityDataSemantics，共 5/5 通过；使用 Node 原生测试，临时 alias loader 解析既有 @/utils 引用 |
| TypeScript / 正式前端构建 | tsc --noEmit 与 pnpm build 通过，LEGACY_BROWSER_BUILD=1；既有 chunk/Browserslist 告警 |
| 浏览器隔离夹具 | Chrome 152.0.7977.82；1366×768、390×844；校验拒绝点名 pg_sleep，改正后试跑 POST 与输入一致，无页面异常，按钮不溢出 |
| Chrome95 / 真实发布→BI | 未执行，不能标记通过 |
| 镜像/离线包/容器部署 | 未执行，未替换共享业务容器 |

首轮本机 Maven 被此前容器生成文件的权限阻止；改用同一正式目录的 Maven 容器后编译通过。测试编译曾发现新 mock 调用缺静态 import，已修正。Node 测试不能交给 Vitest；独立 Node 运行加 alias loader 后 5/5 通过。

PostgreSQL 门禁用例运行在 Testcontainers 隔离 postgres:17.6，验证旧正式成功不被更新的试跑覆盖、新对象违规不能冒充 SUCCEEDED、仅有试跑时无正式证据。测试数据库与业务数据库隔离。

浏览器结果、请求体和截图见 [证据目录](../it/evidence/F8-20260910/)。该脚本只拦截浏览器接口，不能替代真实后端或登录/撤权集成验证。

原始日志：`/tmp/f8-red.log`、`/tmp/f8-green-backend2.log`、`/tmp/f8-green-final.log`、`/tmp/f8-green-ui.log`、`/tmp/f8-node.log`、`/tmp/f8-web-build.log`、`/tmp/f8-browser.log`。Java XML 位于 deploy/source/dts-platform/target/surefire-reports。

## Review 和待验边界

完成一次成组源码自审：检查共享解析、目标连接边界、只读事务、计数/采样顺序、安全 DTO、审计事务、工单准入、报告评分和证据选择。发现 Excel 总览残留 0 分占位，已补“暂无有效评分”，导出专项随后复验。

GitNexus 修改前影响分析及提交前 detect_changes 已执行；共享 StatementExecutionResult 的 HIGH 风险面未修改。新符号曾未被图索引命中，以源码调用点补充。未批量改写历史规则/运行/工单。

F8-T07 待正式镜像/包、部署、Chrome95、真实角色/对象权限及质量正式运行→审批发布→BI 数据集投影闭环。物化只形成物理资产；BI 仍需 DWS/ADS 模型正式发布和语义/数据集版本投影，不能以草稿试跑代替发布证据。
