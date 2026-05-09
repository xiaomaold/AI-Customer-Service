# 业务中心前端

独立的业务中心后台管理前端，对接 `business-center` 后端模块。

## 当前页面

- 总览
- 产品管理
- 订单管理
- 工单管理

## 启动方式

在 `business-center-frontend` 目录执行：

```powershell
npm install
npm run dev
```

默认端口：

```text
http://localhost:3011
```

默认会把 `/api` 代理到：

```text
http://localhost:8091
```

也就是 `business-center` 后端。
