# 发布安全计划（Gate G3）

**当前状态**：PENDING；本文件固定策略，不代表已演练。

## 1. Expand/Contract 顺序

1. Expand：兼容扩展 assets-v2 DTO、增加 adapter/consumer/审计动作；旧字段、路由和现有直接目录登记仍保留。
2. Shadow：生产观察同时写现有目录事实和语义投影，对比 AssetKey、datasetId、状态及统计，不切换 UI。
3. Backfill：按 previewHash、≤500 条批次补齐可唯一解析的存量；无法解析的进入 issue。
4. Read switch：资产概览/目录/详情切到统一投影，保留旧查询指标和差异日志。
5. Contract：只有观测期无漂移且另行批准后，才允许删除旧直写路径；不属于本 Sprint。

## 2. 发布顺序

1. dts-platform 数据库 Expand migration（如有）与审计字典。
2. dts-platform adapter、quality port、outbox consumer，先关闭自动 backfill。
3. 运行 preview 和 shadow 对账；确认无歧义。
4. dts-platform-webapp 兼容 UI。
5. 分批 apply 存量并开启 consumer scheduler。
6. 完成 API、DB、真实浏览器和二次物化验收后扩大到全量。

## 3. 回滚

- 前端：回滚单一 webapp 镜像；旧 DTO 字段仍在。
- consumer：停 scheduler/feature flag，不删除 outbox 或 projection。
- 存量迁移：仅按 batchId 回滚本批创建的 projection；当前 version 漂移则拒绝回滚并人工处置。
- 发布/质量：不修改历史 candidate、run 或 command；新请求可重试或创建新 candidate。
- 血缘：自动写入按 evidence/valid_to 软失效，人工 VERIFIED 不变。

## 4. NO-GO 条件

- preview 无法解释的资产比例未记录或仍被自动回填。
- 任何同一 AssetKey 解析出多个 datasetId。
- 服务投影 consumer 会用旧 version 覆盖新 servingRef。
- 工程质量仍被 UI 标为治理数据质量通过。
- OM 故障导致资产目录返回 5xx 或资产消失。
- 无 Chrome 95、xiezm 真实操作或 rollback 演练证据。

## 5. 发布证据

证据统一登记在 `it/README.md` 对应 IT 编号；镜像、commit、迁移批次、feature flag、回滚时间和操作者必须可追溯。
