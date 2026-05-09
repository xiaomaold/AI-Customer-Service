# Business Center Design Plan

## 1. 目标

构建一个独立于 AI 客服系统的业务中心模块，先完成业务执行能力，再由 AI 客服通过 API 对接。

第一期目标：

- 独立部署
- 领域模型稳定
- API 面向未来 AI 调用
- 保守建模，避免过度设计

## 2. 业务边界

### 属于 Business Center

- 用户类型管理
- 产品信息管理
- 订单创建与状态变更
- 工单创建与处理
- 后台人工处理入口

### 不属于 Business Center

- AI 对话
- RAG 检索
- 意图识别
- 对话式缺参收集

## 3. 领域模型

### User

- 最小改动方案：只区分用户类型
- `userType`
  - `EMPLOYEE`
  - `CUSTOMER`

### Product

- 产品号
- 产品名
- 价格
- 简述

### Order

- 独立订单对象
- 状态：
  - `UNPAID`
  - `PAID`
  - `CANCELLED`

关键规则：

- 仅客户可下单
- 下单后默认 `UNPAID`
- 仅 `UNPAID` 可取消
- `PAID` 订单不能取消，只能申请退款工单

### WorkOrder

- 统一工单对象
- 类型：
  - `LEAVE`
  - `REFUND`
  - `HUMAN_SERVICE`
- 状态：
  - `PENDING`
  - `PROCESSING`
  - `RESOLVED`
  - `REJECTED`

扩展字段采用 `extData`，为后续功能扩展留接口。

## 4. AI 前置补参规则

### 请假工单

必填：

- `leaveDays`
- `leaveType`

### 退款工单

必填：

- `orderNo`

### 转人工工单

无强制参数，但建议 AI 提供：

- `sessionId`
- `lastQuestion`
- `aiSummary`

### 订单创建

必填：

- `productNo`

可选：

- `quantity`

## 5. API 设计原则

- 面向业务动作，而不是只面向后台页面
- 返回统一响应结构
- 错误信息可直接供 AI 判断
- 保留未来数据库持久化与权限扩展空间

## 6. 第一期开发表

### P0

- 用户类型模型
- 产品 API
- 订单 API
- 工单 API
- 状态流转校验

### P1

- MySQL 持久化
- 后台处理前端
- 用户登录与角色

### P2

- AI-RAG 对接
- 更细粒度工单类型
- 审批流

