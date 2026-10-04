# T01: 指标订阅表 + API

**优先级**: P2
**状态**: READY
**依赖**: F1/T02

## 目标
记录用户订阅了哪些指标，以及个人筛选配置。

## 技术设计

### 表结构
```sql
CREATE TABLE gov_indicator_subscription (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    indicator_id    UUID NOT NULL REFERENCES gov_indicator_definition(id),
    user_login      VARCHAR(64) NOT NULL,
    filter_config   JSONB,          -- 用户自定义的筛选条件
    display_order   INTEGER DEFAULT 0,
    created_date    TIMESTAMP DEFAULT now(),
    UNIQUE(indicator_id, user_login)
);
```

### API
```
GET    /api/governance/indicators/subscriptions      -- 当前用户的订阅列表
POST   /api/governance/indicators/subscriptions      -- 订阅
DELETE /api/governance/indicators/subscriptions/{id}  -- 取消订阅
PUT    /api/governance/indicators/subscriptions/{id}  -- 更新筛选/排序
```

## 影响范围
| 文件 | 改动 |
|------|------|
| 新增 Liquibase changelog | 建表 |
| 新增 `GovIndicatorSubscription.java` | 实体 |
| 新增 `GovIndicatorSubscriptionRepository.java` | Repository |
| 修改 `GovernanceResource.java` | 新增端点 |

## 验证
- [ ] 订阅/取消订阅正常
- [ ] 同一用户不能重复订阅同一指标
