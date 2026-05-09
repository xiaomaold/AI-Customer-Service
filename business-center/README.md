# Business Center

`business-center` 是一个独立的业务子系统，用来承接主系统 `AI-RAG` 的业务动作调用。  
当前版本聚焦于客服场景中最常见的几类业务能力：

- 用户类型区分
- 产品管理
- 订单管理
- 工单管理

它的定位不是聊天入口，而是业务执行后端。

## 模块定位

### AI-RAG 主系统负责

- 用户登录与权限控制
- 意图识别、参数补全、对话编排
- 知识库检索与问答
- 决定何时调用业务动作

### Business Center 负责

- 业务数据落库
- 订单创建与状态管理
- 工单创建与状态流转
- 产品目录查询
- 向主系统提供标准 HTTP API

## 当前业务范围

### 用户

- 区分 `EMPLOYEE` 与 `CUSTOMER`

### 产品

- 查询产品列表
- 查询单个产品
- 新增产品

### 订单

- 提交订单
- 查询订单详情
- 查询订单列表
- 取消未支付订单
- 后台标记订单已支付

### 工单

- 提交请假工单
- 提交退款工单
- 提交转人工工单
- 查询工单详情
- 查询工单列表
- 更新工单状态

## 关键业务规则

- 用户通过 `userType` 区分为 `EMPLOYEE` / `CUSTOMER`
- 请假、退款、转人工统一归入工单体系
- 下单独立生成订单，不归工单
- 请假工单需要提供：
  - `leaveDays`
  - `leaveType`
- 退款工单需要提供：
  - `orderNo`
- 转人工工单不强制要求补充字段，但建议附带上下文摘要
- 下单需要提供：
  - `productNo`
  - `quantity`
- 只有未支付订单允许取消
- 第一阶段支付动作不开放给前台，只允许后台人工标记为已支付
- 退款工单只允许针对已支付订单创建

## 数据模型

当前子系统主要表：

- `bc_user`
  - 业务用户
- `bc_product`
  - 产品目录
- `bc_order`
  - 订单
- `bc_work_order`
  - 工单

建表脚本：

- [src/main/resources/sql/schema-business-center.sql](C:/Users/xiongjiahao/Desktop/AI-RAG/business-center/src/main/resources/sql/schema-business-center.sql)

演示数据脚本：

- [requests/seed-demo-data.sql](C:/Users/xiongjiahao/Desktop/AI-RAG/business-center/requests/seed-demo-data.sql)

## 启动依赖

需要准备：

- JDK 17
- Maven 3.9+
- MySQL 8.x

数据库默认使用：

- `business_center`

## 配置方式

配置文件：

- [src/main/resources/application.yml](C:/Users/xiongjiahao/Desktop/AI-RAG/business-center/src/main/resources/application.yml)

推荐通过环境变量覆盖。

### 常用环境变量

```bash
export BUSINESS_CENTER_PORT=8091
export BUSINESS_CENTER_MYSQL_DRIVER=com.mysql.cj.jdbc.Driver
export BUSINESS_CENTER_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/business_center?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export BUSINESS_CENTER_MYSQL_USERNAME=root
export BUSINESS_CENTER_MYSQL_PASSWORD=123456
```

## 启动方式

在 `business-center` 目录执行：

```bash
mvn spring-boot:run
```

默认访问地址：

```text
http://localhost:8091
```

## 演示数据

系统内置了演示用户和演示产品。

### 演示用户

- `employee.demo`
  - 员工演示账号
- `customer.demo`
  - 客户演示账号

### 演示产品

- `P-1001`
  - 商务笔记本电脑
- `P-1002`
  - 无线鼠标

如果你执行了演示 SQL，还会额外有：

- 演示订单
- 演示工单

## 主要接口

### 用户

- `GET /api/business/users`

### 产品

- `GET /api/business/products`
- `GET /api/business/products/{productNo}`
- `POST /api/business/products`

### 订单

- `POST /api/business/orders`
- `GET /api/business/orders/{orderNo}`
- `GET /api/business/orders`
- `POST /api/business/orders/{orderNo}/cancel`
- `POST /api/business/orders/{orderNo}/mark-paid`

### 工单

- `POST /api/business/work-orders`
- `GET /api/business/work-orders/{workOrderNo}`
- `GET /api/business/work-orders`
- `POST /api/business/work-orders/{workOrderNo}/status`

## 与主系统对接方式

主系统通过 HTTP 调用本子系统。

主系统中的配置项：

```bash
export BUSINESS_CENTER_BASE_URL='http://127.0.0.1:8091/api/business'
```

只要该地址配置正确，主系统就可以把下单、退款、转人工等动作转发到这里。

## 当前限制

当前版本暂未实现：

- 登录鉴权
- 后台管理前端权限体系
- 更细粒度的审计日志
- 完整的业务流程补偿机制

## 后续演进建议

1. 增加登录鉴权与角色权限
2. 增加后台管理前端
3. 增加接口鉴权与签名校验
4. 增加更完整的订单与工单状态机
5. 与 `AI-RAG` 做更稳定的联调用例和自动化测试
