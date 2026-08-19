# Sprint-95 集中验证与证据

本文件只登记真实执行结果，不预建截图或伪造 PASS。

## IT 槽位

| IT | 旅程 | 当前状态 | 证据 |
|---|---|---|---|
| IT-01 | Analysis spec/reducer/unit | PENDING | - |
| IT-02 | Analysis source-contract + backend export contract | PENDING | - |
| IT-03 | Webapp legacy build + Analytics tests/package | PENDING | - |
| IT-04 | Mock API 完整链：拖拽→图→样式→计算→联动→发布→导出 | PENDING | - |
| IT-05 | 真实账号/真实 QueryDataset 页面旅程 | BLOCKED_INPUT | 当前无 E2E 凭据 |
| IT-06 | Chrome 95 1366×768 + 窄屏 | BLOCKED_INPUT | 当前无 Chrome 95 executable |
| IT-07 | 容器、API、下载头、console/network、DB revision 对账 | PENDING | - |

## 当前基线

- 前端既有 Analysis/Dashboard 契约：9/9 PASS。
- Analytics 既有 AnalysisApplicationService/AnalysisQueryGateway：5/5 PASS。
- 当前三目标容器运行，UI 200；未登录 API 401。
- 运行数据：25 published datasets、2 governed analyses、2 dashboards。
