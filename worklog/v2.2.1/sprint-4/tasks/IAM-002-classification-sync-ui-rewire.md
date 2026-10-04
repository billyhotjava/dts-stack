# IAM-002: 去掉 iamService 前端占位实现，补分类同步页面与操作流

## 范围

- `source/dts-platform-webapp/src/api/services/iamService.ts`
- `source/dts-platform-webapp/src/pages/security/`
- `source/dts-platform-webapp/src/api/platformApi.ts`

## 目标

- 去掉 `iamService` 中返回空数组和假状态的占位实现
- 提供最小可用的分类同步页面

## 交付

- 真实 API 调用版本的 `iamService`
- 分类同步页面或抽屉面板
- 用户搜索、同步执行、失败重试、状态展示

## 验收

- 页面加载不会再显示“前端只读占位”逻辑
- 用户可触发同步并看到结果回显
- 关键按钮和表格具备稳定 selector

## 当前进度

- 状态：TODO
- 备注：页面可先放在 `security` 视图下，一期不要求复杂权限编排
