# T03: 检测结果分页查询 API

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
提供分页查询 API，支持质量报告前端下钻到具体问题行。

## 技术设计

### API 设计
```
GET /api/governance/quality/runs/{runId}/failing-rows
  ?page=0&size=20&columnName=project_no
```

返回：
```json
{
  "content": [
    {
      "id": "uuid",
      "rowId": "123",
      "tableName": "ods_project_subject_domain",
      "columnName": "project_no",
      "actualValue": "",
      "failReason": "字段不能为空",
      "rowData": { ... }  // 该行的完整数据（实时从 ODS 表查询）
    }
  ],
  "totalElements": 150,
  "totalPages": 8
}
```

### 行数据实时查询
`rowData` 不从快照取，而是根据 `tableName` + `rowId` 实时查询 ODS 表，确保看到的是最新值（用户可能已在线修正）。

```sql
SELECT * FROM {{tableName}} WHERE id = {{rowId}}
```

## 影响范围
| 文件 | 改动 |
|------|------|
| `GovernanceResource.java` | 新增 API |
| `QualityRunService.java` | 新增查询方法 |
| `GovQualityFailingRowRepository.java` | 分页查询 |

## 验证
- [ ] 分页查询返回正确数据
- [ ] rowData 反映最新 ODS 数据
- [ ] 按 columnName 过滤可用

## 完成标准
- [ ] API 实现
- [ ] 分页+过滤可用
