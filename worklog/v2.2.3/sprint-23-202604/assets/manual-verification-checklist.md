# Sprint-23 手工验证清单

## 使用方式
- 这份清单给开发、测试、实施同学直接执行，不依赖本地源码环境。
- 每个场景至少保留 1 份证据: 截图、HAR、日志摘录、录像四选一。
- 建议先测 `platform-webapp` 常规页，再测大屏预览页。

## 场景 A: 同浏览器双 tab 共存
- 浏览器: 同一 Chrome 普通窗口
- 步骤:
  1. 登录 `platform-webapp`
  2. 打开一个业务页，例如工作台
  3. 新开 tab 打开 `/bi/screens/:id/preview`
  4. 两个 tab 同时保持 10 分钟
  5. 期间各刷新 2 次
- 预期:
  - 两个 tab 都保持已登录
  - 不出现“账号已在其他位置登录”
  - 不出现闪屏、循环跳登录页
  - `/api/session/current` 返回 200
  - `/bi/api/screens/:id` 不出现连续 401

## 场景 B: 同浏览器双窗口共存
- 浏览器: 同一 Chrome，两个普通窗口
- 步骤:
  1. 第一个窗口打开 `platform-webapp`
  2. 第二个窗口打开大屏预览
  3. 保持 10 分钟并分别刷新
- 预期:
  - 行为与双 tab 一致
  - 会话不互踢

## 场景 C: 同浏览器普通页 + 大屏长稳测试
- 浏览器: 同一 Chrome 普通窗口
- 步骤:
  1. 一个 tab 打开普通业务页
  2. 一个 tab 打开 GPMC 大屏预览
  3. 保持 30 分钟
  4. 记录第 5、10、30 分钟的 CPU、内存、Network 状态
- 预期:
  - 不出现 401 风暴
  - 不出现慢刷、明显卡顿、白屏闪烁
  - 页面刷新后仍然可用

## 场景 D: 跨浏览器接管
- 浏览器: Chrome + Edge
- 步骤:
  1. 在 Chrome 登录并打开普通页、大屏页
  2. 在 Edge 用同账号登录
  3. 回到 Chrome 观察 2 分钟
- 预期:
  - Edge 成为新主会话
  - Chrome 整组会话被明确接管
  - Chrome 提示应为接管/会话失效语义，不是随机网络错误
  - Chrome 不应出现无限闪屏

## 场景 E: 跨机器接管
- 环境: 机器 A + 机器 B
- 步骤:
  1. 机器 A 登录并打开两个 tab
  2. 机器 B 用同账号登录
  3. 返回机器 A 检查页面状态
- 预期:
  - 机器 A 整组会话被接管
  - 机器 B 使用正常

## 场景 F: 登出一致性
- 步骤:
  1. 同浏览器打开两个 tab
  2. 在任意一个 tab 主动登出
  3. 观察另一个 tab
- 预期:
  - 两个 tab 都失去会话
  - 另一个 tab 不应卡死或无限重定向

## 场景 G: 空闲超时
- 步骤:
  1. 登录后不操作，等待超时阈值
  2. 再访问普通页和大屏页
- 预期:
  - 服务端 reason code 明确
  - 前端只出现一次失效处理
  - 不出现 `/auth/login` 与业务页来回跳

## 采集建议
- Network:
  - 过滤 `/api/session/current`
  - 过滤 `/bi/api/screens/`
  - 过滤 `/api/forward-auth`
- 日志:
  - `platform`: `invalid or expired token`, `session`, `forward-auth`
  - `analytics`: `401`, `trusted user`, `bearer fallback`
  - `proxy`: `/api/forward-auth`, `/bi/api/screens`

## 验证记录模板
- 验证时间:
- 验证环境:
- 浏览器/设备:
- 场景编号:
- 实际结果:
- 证据路径:
- 结论:
