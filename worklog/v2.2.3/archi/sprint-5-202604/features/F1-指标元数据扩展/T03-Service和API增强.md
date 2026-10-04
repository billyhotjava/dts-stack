# T03: IndicatorService 扩展 + API 增强

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标
扩展 IndicatorService 支持新字段的 CRUD，增强 API 查询能力。

## 技术设计

### IndicatorService 改造
- `create/update` 方法支持所有新字段的赋值
- 新增 `findByDomain(String domain)` 查询
- 新增 `findByTemplateId(UUID templateId)` 查询
- 新增 `findDerived(String indicatorCode)` 查询衍生指标链

### Repository 新增方法
```java
List<GovIndicatorDefinition> findByDomainAndEnabledTrue(String domain);
List<GovIndicatorDefinition> findByTemplateId(UUID templateId);
List<GovIndicatorDefinition> findByIsDerivedTrue();
```

### API 端点增强
现有端点保持不变，增强查询参数：
```
GET /api/governance/indicators?domain=FINANCE&category=BUDGET&status=PUBLISHED
```

## 影响范围
| 文件 | 改动 |
|------|------|
| 修改 `IndicatorService.java` | 扩展 CRUD + 新查询 |
| 修改 `GovIndicatorDefinitionRepository.java` | 新增查询方法 |
| 修改 `GovernanceResource.java` 或对应 Resource | 增强查询参数 |

## 验证
- [ ] 按 domain/category/status 过滤查询正常
- [ ] 衍生指标链查询返回正确
