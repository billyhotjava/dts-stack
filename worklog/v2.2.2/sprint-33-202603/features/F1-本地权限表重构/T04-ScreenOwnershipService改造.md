# T04: ScreenOwnershipService 改造

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

将 `ScreenOwnershipService` 的 `createGrant` / `revokeGrant` / `listGrants` 全部改为操作本地 `analytics_screen_access` 表，删除所有平台 REST API 调用。

## 技术设计

```java
@Service
@Transactional
public class ScreenOwnershipService {

    private final AnalyticsScreenAccessRepository accessRepo;

    public ScreenOwnershipService(AnalyticsScreenAccessRepository accessRepo) {
        this.accessRepo = accessRepo;
    }

    /**
     * 新增或更新授权记录。
     * granteeType: "USER" | "ROLE"
     * granteeId:   USER → String.valueOf(analyticsUserId); ROLE → 角色名
     * permission:  "OWNER" | "MANAGER" | "VIEWER"
     * 返回持久化后的记录（含 id），供前端显示。
     */
    public AnalyticsScreenAccess createGrant(Long screenId, String granteeType,
                                              String granteeId, String permission,
                                              Long grantedBy) {
        // UPSERT：已有记录则更新 permission
        AnalyticsScreenAccess existing = accessRepo
            .findByScreenIdAndGranteeTypeAndGranteeId(screenId, granteeType, granteeId)
            .orElse(null);
        if (existing != null) {
            existing.setPermission(permission);
            existing.setGrantedBy(grantedBy);
            return accessRepo.save(existing);
        }
        AnalyticsScreenAccess record = new AnalyticsScreenAccess();
        record.setScreenId(screenId);
        record.setGranteeType(granteeType.toUpperCase());
        record.setGranteeId(granteeId);
        record.setPermission(permission.toUpperCase());
        record.setGrantedBy(grantedBy);
        record.setCreatedAt(Instant.now());
        return accessRepo.save(record);
    }

    /** 按 id 撤销授权 */
    public void revokeGrant(Long grantId) {
        accessRepo.deleteById(grantId);
    }

    /** 列出大屏所有授权（用于权限管理 UI） */
    public List<AnalyticsScreenAccess> listGrants(Long screenId) {
        return accessRepo.findByScreenId(screenId);
    }

    /** 大屏删除/归档时清理所有授权 */
    public void removeAllGrants(Long screenId) {
        accessRepo.deleteByScreenId(screenId);
    }
}
```

**删除的方法**（平台 REST 调用路径）：
- `registerOwnership(screenId, ownerUsername, deptCode)` → 已无平台 ownership 概念，删除
- `transferOwnership(...)` → 删除（本次范围外）
- 所有 `RestTemplate` 依赖、`platformBaseUrl` 字段

`createGrant` 返回类型由 `Map<String, Object>` 改为 `AnalyticsScreenAccess`（强类型）。调用方 `ScreenResource` 需要同步更新序列化逻辑（见 T05）。

## 影响范围

- 重写: `service/ScreenOwnershipService.java`
- 删除: `RestTemplate`、平台 URL 依赖
- 修改调用方: `ScreenResource`（见 T05）
- 新增方法: `findByScreenIdAndGranteeTypeAndGranteeId`（需在 Repository T02 中添加）

## 验证

- [ ] `createGrant` 创建新记录
- [ ] `createGrant` 对已有记录执行 UPSERT（更新 permission）
- [ ] `revokeGrant` 正确删除
- [ ] `listGrants` 返回所有记录（USER + ROLE）
- [ ] `removeAllGrants` 清空大屏所有授权

## 完成标准

- [ ] 无 `RestTemplate` 引用
- [ ] 无平台 URL 相关字段
- [ ] 所有方法有集成测试
