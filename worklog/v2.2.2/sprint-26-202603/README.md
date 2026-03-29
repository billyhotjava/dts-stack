# Sprint-26: 大屏权限系统

**时间**: 2026-03
**状态**: DONE
**目标**: 大屏默认仅创建者可见，支持 OWNER/MANAGE/READ 三级权限，公开链接需登录+授权

## 改动清单

### 后端
- ScreenAclService.java: Permission 枚举 OWNER(40)/MANAGE(30)/READ(10)；删除 ensureDefaultReadRoles；新增 ensureCreatorOwner
- ScreenResource.java: 创建用 ensureCreatorOwner；删除需 OWNER；ACL 更新传 isOwner
- PublicResource.java: 公开链接需登录(401)+权限(403)
- Liquibase 0039: MANAGE→OWNER(创建者)、EDIT/PUBLISH→MANAGE、删除默认角色READ

### 前端
- ScreenAclPanel.tsx: OWNER 只读行 + 拥有者 badge；下拉只 MANAGE/READ
- PublicScreenPage.tsx: 401 跳登录、403 无权限页面
- ScreenHeader.tsx: 删除按钮受 canDelete 控制
- analyticsApi.ts: ScreenAclEntry.perm 类型更新
