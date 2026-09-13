# T03: 模板 CRUD API + Service

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
提供指标模板的完整 CRUD API，内置模板不可删除。

## 技术设计

### IndicatorTemplateService
```java
@Service
public class IndicatorTemplateService {
    List<GovIndicatorTemplate> list(String domain);
    GovIndicatorTemplate getById(UUID id);
    GovIndicatorTemplate create(TemplateCreateRequest req);
    GovIndicatorTemplate update(UUID id, TemplateUpdateRequest req);
    void delete(UUID id);  // builtin=true 时拒绝
}
```

### API 端点
```
GET    /api/governance/indicator-templates?domain=FINANCE
GET    /api/governance/indicator-templates/{id}
POST   /api/governance/indicator-templates
PUT    /api/governance/indicator-templates/{id}
DELETE /api/governance/indicator-templates/{id}
```

### Request DTO
```java
public record TemplateCreateRequest(
    String code, String name, String description, String domain,
    String indicatorBlueprints,   // JSON string
    String requiredSourceFields,  // JSON string
    String seedTables,            // JSON string
    Boolean recommendedSnapshot
) {}
```

## 影响范围
| 文件 | 改动 |
|------|------|
| 新增 `IndicatorTemplateService.java` | Service |
| 新增 `TemplateCreateRequest.java` / `TemplateUpdateRequest.java` | DTO |
| 修改 `GovernanceResource.java` | 新增端点 |

## 验证
- [ ] CRUD 操作正常
- [ ] 内置模板删除返回 403
- [ ] 按 domain 过滤正确
