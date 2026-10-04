# T03: admin接入共享session-core并切换namespace

**优先级**: P0  
**状态**: READY  
**依赖**: F2/T03

## 目标

让 `dts-admin-webapp` 接入与 platform 同构的 session core，并切换到独立 `admin` 域 namespace，避免两个独立应用继续互相广播登出和顶号事件。

## 技术设计

- 将 admin 的 refresh、idle timeout、cross-tab conflict 迁移到共享 core。
- 把 `dts.session.*` 替换为 `dts.admin.session.*`。
- 保持 admin 与 platform 登录态独立，但状态机和协议定义保持一致。

## 影响范围

- `source/dts-admin-webapp/src/api/apiClient.ts`
- `source/dts-admin-webapp/src/components/auth/session-manager.tsx`
- `source/dts-admin-webapp/src/routes/components/login-auth-guard.tsx`
- `source/dts-admin-webapp/src/pages/sys/login/login-form.tsx`

## 验证

- [ ] 同一浏览器中同时打开 admin 与 platform，不会互相登出
- [ ] admin 端 refresh/idle/logout 与 platform 行为一致

## 完成标准

- [ ] admin 切换到独立 namespace
- [ ] admin/platform 仅共享技术栈，不共享浏览器 session key
