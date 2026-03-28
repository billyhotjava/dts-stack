# T05: Asset Grant Management API

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

提供资产授权管理 API，支持本部门授权和跨部门授权两种场景。

## 技术设计

### 端点

```
GET    /api/asset-grants?assetType=&assetId=          -- 某资产的授权列表
POST   /api/asset-grants                              -- 新增授权
DELETE /api/asset-grants/{id}                          -- 撤销授权
GET    /api/asset-grants/my                            -- 我被授权的资产列表
GET    /api/asset-grants/granted-by-me                 -- 我授出的授权列表
```

### 权限控制

- POST (本部门用户): DEPT_DATA_OWNER 及以上
  - 校验: 被授权用户必须属于当前操作人同部门
  - 校验: 目标资产必须归属操作人所在部门
- POST (跨部门用户): INST_DATA_OWNER, SYS_ADMIN, OP_ADMIN
- DELETE: 原授权人或更高权限角色
- GET /my: 所有登录用户
- GET /granted-by-me: 所有登录用户

### 业务规则

- 授权创建时记录审计日志 (GRANT)
- 撤销时记录审计日志 (REVOKE)
- `grant_reason` 字段可填 OA 审批单号（跨部门场景追溯依据）
- 重复授权（同 asset + grantee + permission）返回 409 Conflict
- 有效期校验: valid_to 必须大于 valid_from

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/` — 新增 AssetGrantResource
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/` — 新增 AssetGrantService

## 验证

- [ ] 本部门授权: DEPT_DATA_OWNER 成功, EMPLOYEE 失败
- [ ] 跨部门授权: INST_DATA_OWNER 成功, DEPT_DATA_OWNER 失败
- [ ] 撤销授权: 原授权人成功, 无关用户失败
- [ ] 有效期授权到期后不再生效
- [ ] 审计日志完整记录

## 完成标准

- [ ] 两种授权场景均可用
- [ ] /my 和 /granted-by-me 分页查询正确
