# Sprint-1 集成测试

本目录存放 Sprint-1 各 Task 的集成测试用例、验证脚本和测试结果记录。

## 目录规划

```
it/
├── README.md                        ← 本文件
├── ingestion/                       ← 数据采集稳定性测试
│   ├── IT-001-resilience4j.md       ← 重试 & 断路器验证
│   ├── IT-002-zombie-recovery.md    ← 僵尸执行恢复验证
│   ├── IT-003-auto-retry-queue.md   ← 自动重试队列验证
│   └── IT-004-polling-optimize.md   ← 前端轮询优化验证
└── modeling/                        ← 建模工作流测试
    ├── IT-005-dbt-log-display.md    ← dbt 日志展示验证
    ├── IT-006-test-results.md       ← 测试结果详情验证
    ├── IT-007-git-workflow.md       ← Git 提交/回滚验证
    └── IT-008-data-preview.md       ← 数据预览验证
```

## 测试策略

### 采集稳定性

| 编号 | 场景 | 验证方式 |
|------|------|----------|
| IT-001 | dts-ingestion 宕机时 platform 代理请求自动重试并触发断路器 | 停止 ingestion 容器 → 发起请求 → 观察重试日志和 fallback 响应 |
| IT-002 | 服务重启后自动恢复 running 状态的僵尸执行 | 插入 running 执行记录 → 重启 ingestion → 验证状态被修正 |
| IT-003 | CONNECTION 类型失败自动进入重试队列并按退避间隔重跑 | 模拟连接失败 → 检查 retry_queue 表 → 验证定时重试触发 |
| IT-004 | 长时间任务轮询频率自适应降低且不硬超时 | 触发慢任务 → 观察浏览器网络请求间隔变化 |

### 建模工作流

| 编号 | 场景 | 验证方式 |
|------|------|----------|
| IT-005 | dbt run 完成后可查看完整 Airflow 日志 | 执行 dbt run → 打开日志 Tab → 验证日志内容完整 |
| IT-006 | dbt test 失败后展示每个测试的断言详情 | 编写会失败的 test → 执行 → 检查测试结果表格 |
| IT-007 | 编辑文件后可提交、查看历史、回滚 | 修改 SQL → 提交 → 再修改 → 回滚 → 验证内容恢复 |
| IT-008 | 模型运行成功后可预览 Top 100 数据 | 执行 dbt run → 打开预览 Tab → 验证数据正确展示 |
