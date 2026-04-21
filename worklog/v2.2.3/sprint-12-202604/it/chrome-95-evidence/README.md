# Chrome 95 Evidence

此目录存放 Sprint-12 IT 阶段 5 个 TC 的实机证据（截图 / 视频 / 控制台日志）。

## 目录约定

```
chrome-95-evidence/
  TC-01-new-screen/
    before-drag.png
    after-drag.png
    preview.png
    console.log
  TC-02-resize/
    1366x768.png
    1920x1080.png
    3840x2160.png
  TC-03-legacy-v1-readonly/
    banner.png
    after-convert.png
  TC-04-component-resize/
    capture.mp4
  TC-05-stress-resize/
    perf-timeline.png
    console-no-errors.log
```

## 采集流程（客户侧）

1. 运行 `scripts/chrome-95-smoke.sh`（开发机已有预置路径约定的脚本，若无见下方清单）
2. 在 Chrome 95 开 DevTools Performance / Console 录制
3. 按各 TC 的 "验证" 步骤操作，保存帧截图或 mp4
4. 把文件放进对应 `TC-XX-*/` 目录
5. 所有 TC 通过后，在 `worklog/v2.2.3/sprint-12-202604/it/README.md` 的 "退出标准" 勾选 Chrome 95

## 手动备选

没有真实 Chrome 95？使用 Chrome DevTools 的 "User Agent" override 对主要 UA 能捕获绝大部分前端问题，但 **不能** 代替 Chrome 95 引擎的真实行为（如 :has()、container queries、structuredClone 等 API 的 polyfill / 降级是否生效）。正式上线前必须实机验证。

## 状态

- 当前状态：**占位**（实机验证待客户侧执行）
- Sprint-12 阶段 1-4 的代码变更已通过 `npx tsc --noEmit` + 相关 vitest 用例
- `scripts/chrome-95-smoke.md` 记录了不依赖真机也能执行的 Chrome 95 兼容静态检查清单
