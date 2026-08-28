# IT-08：接入质量与数据资产证据

检查日期：2026-08-28。判定：`PASS_SOURCE_DEPLOYED_WITH_CANARY_GAP`。

## 已验证

- canonical `targetDatasetId` 已贯穿 task、revision、execution、DTO、mapper 和 lineage snapshot；旧数据保持 nullable/双读，不猜测回填。
- 接入成功仍由既有 after-commit `QualityWorkflowOrchestrator` 登记正式质量 workflow；`QualityRunService` 继续只做执行器，没有创建第二套质量流程。
- 平台批量投影 execution 对应 dataset、workflow/run、`MISSING/PENDING/CURRENT/STALE/TRIGGER_FAILED` 证据状态、质量状态和消费资格。
- “可信可用”仅由服务端 `CURRENT + PASSED + ELIGIBLE` 派生；质量失败不改写 ingestion SUCCESS，也不创建第二资产身份。
- 当前数据库 7 个 active 任务均为迁移前记录，`target_dataset_id` 为 null；页面必须显示目标未解析/证据缺失，不沿用数据集 latest PASS。

## 保留门禁

未找到明确可写且不会影响客户数据的金丝雀，因此没有执行“两批接入 → 旧 PASS 变 STALE → 新 CURRENT”的运行态业务测试。该项不得由源码测试或现代浏览器截图冒充通过。
