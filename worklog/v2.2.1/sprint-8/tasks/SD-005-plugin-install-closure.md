# SD-005

## 标题

打通插件安装、插件清单、组件库可见性的真实闭环。

## 范围

- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenPluginResource.java`
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/`
- `source/dts-analytics-webapp/modern/src/pages/screens/plugins/manifestLoader.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/componentLibrary.ts`

## 目标

- `screen-plugins` 不再只返回 demo plugin
- marketplace 安装结果能反映到插件清单与组件库

## 交付

- 安装后插件清单读取逻辑
- 前端组件库的已安装插件可见性
- 安装失败或版本冲突的错误回显

## 验收

- 安装一个 marketplace 组件后，组件库可以看到它
- `/analytics/api/screen-plugins` 返回安装后插件，而不只是 demo
- 原有 demo plugin 仍可作为 fallback 使用

## 当前进度

- 状态：DONE
- 备注：marketplace 安装结果已反映到 `/api/screen-plugins`，组件库对 `installed=false` 的远端插件组件默认隐藏，并支持安装后刷新

## 风险

- 插件清单来源若同时来自本地、市场、远端，会引入优先级冲突
