# T01：恢复登录与独立 UI 验收链

**优先级**：P0
**状态**：BLOCKED

## 目标

使用环境负责人提供的有效测试账号进入 `https://bi.yuzhicloud.com`，并以独立浏览器会话保存本 Sprint UI 证据。

## 契约

- 输入：`E2E_BASE_URL: URL`、`E2E_USERNAME: string`、`E2E_PASSWORD: secret`。
- 输出：有效 storageState、桌面/窄屏/Chrome95 截图。
- 错误路径：401、证书错误、浏览器锁均原样记录，不修改生产身份状态。

## Definition of Done

- [ ] 登录探针成功。
- [ ] 独立浏览器可进入 `/settings/help`。
- [ ] IT-02/03/04/07 证据落盘。
