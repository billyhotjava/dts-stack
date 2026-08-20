# G0 交付基线（2026-08-21）

- `dts-platform`、`dts-platform-webapp`、PostgreSQL 及认证相关容器运行。
- `https://bi.yuzhicloud.com/` 返回 200；未登录访问受保护 health 返回 401。
- 本机 Chrome 150 可用；F5 已完成源码兼容约束、生产构建和 Chrome 150 真实 E2E，未伪装 Chrome 95 实测。
- 真实账号 `xiezm` 的浏览器登录和业务链路已按约定在全部 Feature 完成后执行。
- 运行库资产数据与标签空值画像见 Sprint README。
- 共享工作区存在其他未提交修改，本 Sprint 只触碰登记文件与明确的 catalog 目标文件。

状态：`PASS`；最终结果见 `evidence/README.md`。
