# T01: 修复 App.tsx QueryClient 每次渲染重建

**严重度**: Critical
**文件**: `source/dts-platform-webapp/src/App.tsx`

## 问题

`<QueryClientProvider client={new QueryClient()}>` 写在 JSX 内，每次 App 组件渲染都创建新 QueryClient 实例，导致：
- 所有 react-query 缓存丢失
- 进行中的请求被中断
- 潜在的无限 fetch 循环

## 修复方案

将 QueryClient 实例提取到模块级别：

```tsx
const queryClient = new QueryClient();

function App({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      ...
    </QueryClientProvider>
  );
}
```

## 验证

- App 重新渲染时 react-query 缓存保持
- 页面切换后返回不触发重复请求
