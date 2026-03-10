# SVC-002: API 服务页补请求参数、请求头、响应体、策略命中展示

## 范围

- `source/dts-platform-webapp/src/pages/services/ApiServicesPage.tsx`
- `source/dts-platform-webapp/src/api/services/apiServicesService.ts`

## 目标

- 把当前“点一下测试，弹出简单结果”的体验升级为真正可联调的测试面板

## 交付

- 可输入 params / headers / body 的测试面板
- 响应 headers / body / status 展示
- 策略命中与字段脱敏说明

## 验收

- 页面能对真实 `tryInvoke` 返回进行结构化展示
- 用户能区分请求问题、权限问题、下游失败
- 关键交互具备稳定 selector

## 当前进度

- 状态：TODO
- 备注：一期不做复杂脚本化测试集，只做单次调用联调
