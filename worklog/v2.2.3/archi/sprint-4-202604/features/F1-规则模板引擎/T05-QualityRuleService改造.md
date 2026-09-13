# T05: QualityRuleService 改造

**优先级**: P0
**状态**: READY
**依赖**: T03, T04

## 目标
改造规则创建/更新流程，支持从模板创建规则（选模板 → 填参数 → 渲染 SQL → 快照保存）。

## 技术设计

### 创建规则流程

```
请求: { templateId, templateParams, name, severity, datasetId, actionOnFail, autoTrigger }
  │
  ▼
1. 查 gov_quality_template 获取模板
2. 用 SqlTemplateRenderer 渲染 SQL
3. 创建 GovRule:
   - template_id = 模板 ID
   - template_params = 参数 JSON（快照）
   - rendered_sql = 渲染后的 SQL（快照）
   - definition = 保持兼容（将 rendered_sql 写入现有 definition JSON）
4. 创建 GovRuleVersion（现有逻辑不变）
5. 返回
```

### 兼容性
- `templateId` 为空时走现有自定义 SQL 流程
- 现有规则不受影响
- `QualityRunService.doExecuteRun()` 优先使用 rendered_sql，fallback 到现有 definition 解析

### API 扩展

`POST /api/governance/quality/rules` 的 request body 新增可选字段：
- `templateId` (UUID)
- `templateParams` (Map)
- `actionOnFail` (String: ALERT/BLOCK)
- `autoTrigger` (boolean)

新增模板管理 API：
- `GET /api/governance/quality/templates` — 列表
- `POST /api/governance/quality/templates` — 新增
- `PUT /api/governance/quality/templates/{id}` — 更新
- `DELETE /api/governance/quality/templates/{id}` — 删除（builtin 不可删）
- `POST /api/governance/quality/templates/{id}/preview` — 预览渲染 SQL

## 影响范围
| 文件 | 改动 |
|------|------|
| `QualityRuleService.java` | 创建/更新逻辑 |
| `QualityRunService.java` | 执行时使用 rendered_sql |
| `GovernanceResource.java` | 模板管理 API |
| `QualityRuleUpsertRequest.java` | 新增字段 |

## 验证
- [ ] 从模板创建规则成功
- [ ] 修改模板后已有规则不受影响
- [ ] 自定义 SQL 规则仍正常工作
- [ ] 模板 CRUD API 可用
- [ ] 预览 API 返回渲染后的 SQL

## 完成标准
- [ ] 模板创建规则流程完整
- [ ] 向后兼容
- [ ] 模板管理 API 就绪
