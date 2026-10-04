# Sprint-10 集成测试与验证说明

## 核心验证目标

验证逻辑建模在空目录 `services/dts-dbt` 下具备完整的最小可运行能力，并确认批量导入与产出表维护不再依赖旧 rollback 链路。

## 验证场景

### 1. 空工作区自动初始化

前置：
- 清空或新建空目录 `services/dts-dbt`

验证：
- 打开逻辑建模页
- 查看 dbt 工作区状态与目录树

预期：
- 自动生成最小骨架
- 页面不再只显示“目录可写”，而是可继续导入/编译/测试

### 2. 单模型导入

前置：
- 空工作区

验证：
- 导入单个 SQL 模型

预期：
- 数据库记录创建成功
- `services/dts-dbt/models/.../*.sql` 真实存在
- 后续 `dbt compile` 可识别该模型

### 3. ZIP 批量导入

验证：
- 合法 ZIP 导入成功
- 缺失 `models.tsv`
- 缺失 SQL 文件
- 非法 `layer/materialized`

预期：
- 合法 ZIP 成功
- 非法 ZIP 返回明确失败类型：
  - `validation_failed`
  - `write_failed`

### 4. 清空产出表

验证：
- 对 table 模型执行清空产出表
- 对 view 模型执行清空产出表

预期：
- table 被 truncate
- view 被明确拒绝
- 不再调用 `/api/rollback/*`

### 5. 重建产出表

验证：
- 对当前模型执行重建产出表

预期：
- 先 drop 当前 relation
- 再触发当前 selector 的 `dbt build`
- 完成后同步最新 `manifest/run_results`

## 建议命令

```bash
cd source/dts-platform && mvn -Dtest=DbtWorkspaceBootstrapTest,ModelingSqlModelServiceTest,DbtOutputRelationServiceTest,EtlResourceTest test
cd source/dts-platform-webapp && pnpm build
```

## 人工核查点

- 逻辑建模页不再弹出 rollback modal 来处理产出表
- 空工作区的首次导入不再出现“导入成功但无文件”
- 批量导入结果页可区分验证失败与写入失败
