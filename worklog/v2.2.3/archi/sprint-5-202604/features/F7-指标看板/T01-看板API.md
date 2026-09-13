# T01: 看板 API 端点

**优先级**: P1
**状态**: READY
**依赖**: F6/T02

## 目标
提供指标看板所需的聚合数据 API。

## 技术设计

### API 端点

```
GET /api/governance/indicators/dashboard?domain=FINANCE&days=30
```
返回：
```json
{
  "indicators": [
    {
      "id": "uuid",
      "code": "budget_execution_rate",
      "name": "项目经费执行率",
      "domain": "FINANCE",
      "unit": "%",
      "direction": "HIGHER_BETTER",
      "currentValue": 85.3,
      "previousValue": 82.1,
      "changeRate": 0.039,
      "alertLevel": "GREEN",
      "thresholdMin": 80,
      "thresholdMax": null,
      "trend": [
        {"date": "2026-03-07", "value": 78.5},
        {"date": "2026-03-14", "value": 80.2},
        ...
      ]
    }
  ],
  "alerts": {
    "red": 1,
    "yellow": 3,
    "green": 12
  }
}
```

```
GET /api/governance/indicators/{id}/detail?days=30
```
返回指标详情 + 完整运行历史 + 维度下钻数据。

```
GET /api/governance/indicators/{id}/drilldown?dimension=department&period=2026-03
```
返回按指定维度展开的明细数据（从 ADS 表实时查询）。

### Service
```java
@Service
public class IndicatorDashboardService {
    Map<String, Object> getDashboard(String domain, int days);
    Map<String, Object> getDetail(UUID indicatorId, int days);
    List<Map<String, Object>> drilldown(UUID indicatorId, String dimension, String period);
}
```

## 影响范围
| 文件 | 改动 |
|------|------|
| 新增 `IndicatorDashboardService.java` | 看板数据聚合 |
| 修改 `GovernanceResource.java` | 新增 3 个端点 |

## 验证
- [ ] dashboard 返回所有已发布指标的最新值和趋势
- [ ] detail 返回 30 天运行历史
- [ ] drilldown 按维度展开正确
