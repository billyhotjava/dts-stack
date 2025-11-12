<div align="center"> 
<br> 
<br>
<img src="./src/assets/icons/ic-logo-badge.svg" color="green" height="140" />
<h3> Slash Admin </h3>
  <p>
    <p style="font-size: 14px">
      Slash Admin 是一款现代化的后台管理模板，基于 React 19、Vite、shadcn/ui 和 TypeScript 构建。它旨在帮助开发人员快速搭建功能强大的后台管理系统。
    </p>
    <br />
    <br />
    <a href="https://admin.slashspaces.com/">Preview</a>
    ·
    <a href="https://discord.gg/fXemAXVNDa">Discord</a>
    ·
    <a href="https://docs-admin.slashspaces.com/">Document</a>
    <br />
    <br />
    <a href="https://trendshift.io/repositories/6387" target="_blank"><img src="https://trendshift.io/api/badge/repositories/6387" alt="d3george%2Fslash-admin | Trendshift" style="width: 250px; height: 55px;" width="250" height="55"/></a>
</div>

**中文** | [English](./README.md)

## 赞助 
<div style="display: flex; gap: 50px"> 
  <img style="width:300px" src="https://d3george.github.io/github-static/pay/weixin.jpg" >
  <img style="width:300px" src="https://d3george.github.io/github-static/pay/buymeacoffee.png" />
</div>


## 预览
+ https://admin.slashspaces.com/

|![login.png](https://d3george.github.io/github-static/slash-admin/sa-web-light.jpeg)|![login_dark.png](https://d3george.github.io/github-static/slash-admin/sa-web-dark.jpeg)
| ----------------------------------------------------------------- | ------------------------------------------------------------------- |
|![analysis.png](https://d3george.github.io/github-static/slash-admin/sa-mobile-light.jpeg)|![workbench.png](https://d3george.github.io/github-static/slash-admin/sa-mobile-dark.jpeg)
| | 
## 特性

- 使用 React 19 hooks 进行构建。
- 基于 Vite 进行快速开发和热模块替换。
- 集成 shadcn/ui，提供丰富的 UI 组件和设计模式。
- 使用 TypeScript 编写，提供类型安全性和更好的开发体验。
- 响应式设计，适应各种屏幕尺寸和设备。
- 灵活的路由配置，支持多级嵌套路由。
- 集成权限管理，根据用户角色控制页面访问权限。
- 集成国际化支持，轻松切换多语言。
<!-- 平台端不再包含用户/角色/部门等后台管理功能（已迁移至 admin） -->
- 可定制的主题和样式，以满足您的品牌需求。
- 基于 MSW 和 Faker.js 的Mock方案
- 使用 Zustand 进行状态管理
- 使用 React-Query 进行数据获取

## 快速开始

### 获取项目代码

```bash
git clone https://github.com/d3george/slash-admin.git
```

### 安装依赖

在项目根目录下运行以下命令安装项目依赖：

```bash
pnpm install
```

### 启动开发服务器

运行以下命令以启动开发服务器：

```bash
pnpm dev
```

访问 [http://localhost:3001](http://localhost:3001) 查看您的应用程序。

### 构建生产版本

运行以下命令以构建生产版本：

```bash
pnpm build
```

> 说明：`pnpm build` 现在默认以 `LEGACY_BROWSER_BUILD=1` 运行，输出兼容 Chrome 95 的产物。若仅在现代浏览器中调试，可执行 `pnpm build:modern` 获取更小的 bundle。

### Chrome 95 兼容说明

- Vite 构建目标、Browserslist 以及 Legacy 插件均已下调至 Chrome 95，避免老旧内核解析现代语法时报错。
- 入口在加载应用前会注入 `src/polyfills/legacy-browser.ts`，补齐 `structuredClone`、`URL.canParse`、`crypto.randomUUID` 等缺失特性，确保证书登录流程可用。
- 如需进一步扩展旧浏览器支持，可在同一 polyfill 文件中追加自定义兼容逻辑。

## 项目定制说明（DTS 业务端）

- 业务端（客户端）菜单使用前端静态导航，无需菜单创建 API。
  - 通过环境变量切换：`VITE_APP_ROUTER_MODE=frontend`（默认）。
- 若不需要在客户端暴露“业务端菜单管理”界面（即不通过 API 创建/维护菜单），可关闭：
  - 设置 `VITE_ENABLE_PORTAL_MENU_MGMT=false`（默认关闭）。
  - 关闭后，该入口与路由不再展示，也不会调用相关后端接口。

### 生产环境 API 代理说明

生产镜像默认使用 Nginx 提供静态文件。为使前端相对路径 `/api/**` 正确转发到后端，本仓库已新增 `nginx/default.conf` 并在 `Dockerfile` 中启用（复制到 `/etc/nginx/conf.d/default.conf`）。

- 配置位置：`nginx/default.conf:1`
- 默认上游：`proxy_pass http://dts-platform:8080;`（保留 `/api` 前缀）
- 如后端地址不同，请修改上述上游主机端口后重新构建镜像。

如果不希望由 Nginx 代理 `/api`，可以在构建时设置绝对地址绕过代理：

```bash
# 例如后端在宿主 8080 端口
VITE_API_BASE_URL=http://localhost:8080/api pnpm build
```

开发模式下，Vite 会将 `/api` 代理到本机 `8080`（见 `vite.config.ts:33`）。

## Git贡献提交规范

- `feat` 新功能
- `fix` 修复bug
- `docs` 文档注释
- `style` 代码格式(不影响代码运行的变动)
- `refactor` 重构
- `perf` 性能优化
- `revert` 回滚commit
- `test` 测试相关
- `chore` 构建过程或辅助工具的变动
- `ci` 修改CI配置、脚本
- `types` 类型定义文件修改
- `wip` 开发中
