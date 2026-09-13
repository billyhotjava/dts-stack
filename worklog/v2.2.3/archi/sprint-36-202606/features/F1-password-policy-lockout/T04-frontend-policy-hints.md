# T04: 前端登录/改密页展示口令要求与锁定提示

**优先级**: P1
**状态**: READY
**依赖**: T01, T02

## 目标
在登录、改密/重置口令界面展示口令规则与失败锁定提示，错误信息本地化，且不泄露账号是否存在。

## TDD 测试先行（RED）
- 新增 source-contract 测试（沿用现有 `*.source-contract.test.ts` 模式，参考 `dts-platform-webapp/src/pages/sys/login/classified-login-badge.source-contract.test.ts`）：
  - 断言改密/注册表单渲染口令规则清单（长度≥12、四类字符、不含用户名、历史口令）。
  - 断言锁定提示文案为通用「账号或口令错误，或已被临时锁定，请稍后再试」，**不区分账号是否存在、不暴露剩余次数**。
  - 断言文案走 i18n key，而非硬编码中文串。

## 技术设计（GREEN）
- 在登录/改密/重置表单加入口令规则提示组件与锁定提示：
  - admin：`source/dts-admin-webapp/src/pages/sys/login/{login-form.tsx,reset-form.tsx,register-form.tsx}`、`src/admin/views/reset-password-modal.tsx`。
  - platform：`source/dts-platform-webapp/src/pages/sys/login/{login-form.tsx,reset-form.tsx}`。
- 口令规则文案与策略阈值集中到单一常量/i18n 资源，避免与 realm 配置漂移。
- 客户端仅做提示与浅校验，最终判定以 Keycloak 服务端策略为准；错误信息一律泛化，不回显后端原始 message。

## 影响范围
- `source/dts-admin-webapp/src/pages/sys/login/{login-form,reset-form,register-form}.tsx`
- `source/dts-admin-webapp/src/admin/views/reset-password-modal.tsx`
- `source/dts-platform-webapp/src/pages/sys/login/{login-form,reset-form}.tsx`
- 对应 `*.source-contract.test.ts`（新增，置于各 login 目录）

## 验证
- [ ] 改密/注册页展示完整口令规则清单。
- [ ] 锁定/失败提示泛化，不泄露账号是否存在、不暴露剩余次数。
- [ ] 文案走 i18n，与 realm 策略阈值同源不漂移。

## 完成标准
- [ ] 前端口令引导与锁定提示落地并被 source-contract 测试守护。
