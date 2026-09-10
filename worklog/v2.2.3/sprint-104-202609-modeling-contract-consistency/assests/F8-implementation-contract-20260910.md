# F8 实施冻结契约（2026-09-10）

来源：用户授权开始编码；基线 8c7023572，覆盖八条审核意见及模型发布门禁补充。本文优先于原 K67–K72 草案中的冲突描述，不自动改变其他 Feature 状态。

- T44 只读画像：3 规则、5 版本、0 statements、多语句占比 0%；历史 RESULT_ID_REQUIRED=4，DATASET_SCOPE_BLOCKED=16。5 个定义中 select * 无 FROM=1，函数越界=0、跨表=0；4 个有表定义在无 id 的目标上存在条件性统计故障。保留所有历史规则/运行/工单，不直接改库。
- K67：POST /api/governance/quality/rules/validate-sql；{datasetId, definition:{sql?,statements?}}。允许只读平台元数据和对象权限检查，禁止连接目标库。返回 valid、逐语句安全 diagnostics、SQL checksum、允许函数清单；非法/越权遵循 400/403，禁止泄露他表身份。
- K68：校验、保存、草稿试跑、正式执行共用有效语句解析。非空 statements 优先；每条语句校验，禁止合规 sql 掩盖非法 statements。新保存（含草稿）与重新发布均校验，不改写历史定义。
- 草稿试跑：POST /api/governance/quality/rules/dry-run；入参同 K67，直接执行编辑框快照，不接受 ruleId 代替内容，不创建规则/版本/正式运行/失败样本记录或工单。只读目标查询、已有超时、最多 1000 样本；返回 checksum，输入变化后界面丢弃旧结果。旧 runs/dry-run 保留兼容，但其 DRY_RUN 记录不得进入正式发布证据。
- K69：单语句复用违规计数，不强制 id；多语句可保留有 id 时的精确去重，无 id/统计故障降级为 UNAVAILABLE 或 UNDEDUPLICATED。非精确统计不进入通过率/评分，显示为空而非 0/100。先保存语句业务结论，再独立采样；采样失败保留违规结论并标记故障。
- K70：qualityOutcome=PASSED/VIOLATION/UNKNOWN；executionOutcome=OK/FAILED；statisticsStatus=EXACT/UNDEDUPLICATED/UNAVAILABLE。任意已确认违规优先保留 VIOLATION；全部必需语句完成且无违规才 PASSED；纯故障 UNKNOWN。兼容 status：只有 PASSED+OK 为 SUCCEEDED，违规/故障为 FAILED；缺失计数不当零。安全 summary 与 diagnostics 存入既有 metrics_json 的版本化质量结果对象；历史数组按旧字段保守读取，无 schema 迁移。
- K71：正式违规建业务工单（包括混合故障），纯故障不建；试跑永不建。历史工单保留，只出清单不自动处理。
- K72：执行器→版本化安全结果→安全 DTO→页面完整传递 reasonCode、受控 detail；不透传原始数据库错误/SQL。覆盖刷新详情、评分与导出。静态校验的 200ms 是观测目标，保留解析硬上限 2s，明确超时结果。
- 发布门禁：只消费正式、已发布规则的绑定运行；资产/规则版本/绑定/时效校验保持。双维度只允许 PASSED+OK，通过率为空不得作为通过；旧结果保持 SUCCEEDED 的兼容语义。试跑不能覆盖正式通过或失败；新语义须进入候选证据判定。
- 验收增加：未保存 SQL 与试跑 checksum 一致；sql/statements 同时存在；计数成功采样失败；多语句统计未知；纯故障/纯违规/混合三矩阵；详情刷新保留原因；DRY_RUN 隔离；正式质量通过→模型发布→BI 数据集版本生成。

## 验证边界

开发目录写测试后提交推送，部署目录 ff-only 同 SHA 后运行 RED/GREEN 和正式构建。代码、测试、包、部署、页面分开记录。当前画像与上一轮现场快照不同，实施期间不重放或改写用户业务规则。
