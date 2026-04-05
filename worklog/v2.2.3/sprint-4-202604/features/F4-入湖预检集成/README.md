# F4: 入湖预检集成（后端）

**优先级**: P0
**状态**: READY

## 目标
在 Excel 入湖流程中增加质量预检环节：上传 → 全量解析写入暂存表 → 绑定规则预检 → 行列级错误反馈 → 修复后提交入湖。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | ExcelParseService — 全量解析服务 | P0 | READY | - |
| T02 | StagingTableService — 暂存表管理 | P0 | READY | T01 |
| T03 | IngestionQualityBridge — 预检桥接服务 | P0 | READY | T02, F3 |
| T04 | 内置自动检测规则 | P0 | READY | T02 |
| T05 | 入湖任务流程改造（API + 前端步骤） | P1 | READY | T03 |

## 完成标准
- [ ] Excel 全量解析（POI DOM ≤5000行，流式 >5000行）
- [ ] 暂存表 TEXT 存储 + _errors JSONB + 24h TTL 自动清理
- [ ] 预检复用已发布规则（SqlTemplateRenderer 渲染 SQL，执行器切 PG）
- [ ] 内置检测：公式单元格、空行、重复行、列类型混乱
- [ ] 入湖任务创建页增加"质量预检"步骤（可选）
