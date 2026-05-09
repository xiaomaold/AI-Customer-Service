# AI-RAG

一个面向企业客服与知识库场景的示例项目，基于 `Spring Boot 3.x + LangChain4j + MyBatis-Plus + MySQL + PostgreSQL(pgvector)` 构建，包含：

- 主系统 `AI-RAG`
  - 用户登录与角色权限
  - 聊天会话与消息持久化
  - SSE 流式问答
  - 知识库、文档上传、文档切分、向量化、RAG 检索
  - 管理后台与知识库权限控制
- 子系统 `business-center`
  - 产品、订单、工单等业务能力
  - 作为主系统后续业务动作调用的独立业务中心

## 项目结构

```text
AI-RAG
├─ src/                        主系统源码
├─ requests/                   HTTP 调试示例
├─ data/                       示例文档与本地上传目录
├─ business-center/            业务中心子系统
├─ frontend/                   主系统前端
├─ business-center-frontend/   业务中心前端
└─ scripts/                    本地辅助脚本
```

## 核心能力

### 主系统

- 用户登录、JWT 鉴权、角色控制
- 会话管理、聊天消息记录
- SSE 流式回答
- 知识库创建、文档上传、分块预览
- 文档重建索引
- 基于 pgvector 的向量检索
- 与业务中心联动处理下单、退款、转人工等动作

### 子系统

- 用户类型区分：员工、客户
- 产品目录管理
- 订单创建、状态查询、取消、标记已支付
- 工单创建、状态流转
- 作为主系统的业务执行后端

## 技术栈

- Java 17
- Spring Boot 3.4.x
- MyBatis-Plus
- LangChain4j
- MySQL 8.x
- PostgreSQL 14+ 与 `pgvector`
- RabbitMQ
- Maven 3.9+

## 运行依赖

启动主系统前，需要准备：

- MySQL
- PostgreSQL（已安装 `pgvector` 扩展）
- RabbitMQ
- 可用的 DashScope / 百炼 API Key

主系统使用两套数据库：

- `ai_rag`：主业务库
- `ai_rag_vector`：向量库

业务中心使用：

- `business_center`

## 数据库初始化

### 1. 主业务库 MySQL

先创建数据库：

```sql
CREATE DATABASE ai_rag DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

再执行建表脚本：

- [src/main/resources/sql/schema-mysql.sql](C:/Users/xiongjiahao/Desktop/AI-RAG/src/main/resources/sql/schema-mysql.sql)

### 2. 向量库 PostgreSQL

先创建数据库：

```sql
CREATE DATABASE ai_rag_vector;
```

连接到该库后执行：

- [src/main/resources/sql/schema-pgvector.sql](C:/Users/xiongjiahao/Desktop/AI-RAG/src/main/resources/sql/schema-pgvector.sql)

如果尚未安装 `pgvector`，先执行：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. 业务中心 MySQL

先创建数据库：

```sql
CREATE DATABASE business_center DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

再执行建表脚本：

- [business-center/src/main/resources/sql/schema-business-center.sql](C:/Users/xiongjiahao/Desktop/AI-RAG/business-center/src/main/resources/sql/schema-business-center.sql)

如需导入演示订单与工单数据：

- [business-center/requests/seed-demo-data.sql](C:/Users/xiongjiahao/Desktop/AI-RAG/business-center/requests/seed-demo-data.sql)

## 模型与向量配置说明

当前项目默认对接阿里百炼 DashScope。

聊天模型与向量模型不是同一个概念：

- 聊天模型
  - `DASHSCOPE_CHAT_MODEL`
  - `DASHSCOPE_STREAMING_MODEL`
- 向量模型
  - `DASHSCOPE_EMBEDDING_MODEL`

默认推荐组合：

```text
DASHSCOPE_CHAT_MODEL=qwen-plus
DASHSCOPE_STREAMING_MODEL=qwen-plus
DASHSCOPE_EMBEDDING_MODEL=text-embedding-v4
RAG_EMBEDDING_DIMENSION=1024
```

注意：

- 不要把聊天模型直接当成 embedding 模型使用
- 如果更换 embedding 模型，必须同步调整 `RAG_EMBEDDING_DIMENSION`
- PostgreSQL 向量列维度必须与 embedding 维度一致

## 配置方式

项目配置文件在：

- [src/main/resources/application.yml](C:/Users/xiongjiahao/Desktop/AI-RAG/src/main/resources/application.yml)

推荐通过环境变量覆盖。

### 主系统常用环境变量

```bash
export SERVER_PORT=8080

export MYSQL_DRIVER=com.mysql.cj.jdbc.Driver
export MYSQL_URL='jdbc:mysql://127.0.0.1:3306/ai_rag?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=123456

export PGVECTOR_DRIVER=org.postgresql.Driver
export PGVECTOR_URL='jdbc:postgresql://127.0.0.1:5432/ai_rag_vector'
export PGVECTOR_USERNAME=postgres
export PGVECTOR_PASSWORD=123456

export RABBITMQ_HOST=127.0.0.1
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=guest
export RABBITMQ_PASSWORD=guest
export RABBITMQ_VHOST=/

export DASHSCOPE_API_KEY='your-api-key'
export DASHSCOPE_CHAT_MODEL='qwen-plus'
export DASHSCOPE_STREAMING_MODEL='qwen-plus'
export DASHSCOPE_EMBEDDING_MODEL='text-embedding-v4'

export RAG_EMBEDDING_DIMENSION=1024
export RAG_UPLOAD_DIR='data/uploads'

export JWT_SECRET='your-jwt-secret'
export JWT_EXPIRATION_SECONDS=7200

export BUSINESS_CENTER_BASE_URL='http://127.0.0.1:8091/api/business'
```

### 可选参数

```bash
export RAG_HISTORY_LIMIT=10
export RAG_RETRIEVAL_TOP_K=5
export RAG_MAX_SEGMENT_SIZE=500
export RAG_MAX_OVERLAP_SIZE=100
export RAG_RETRIEVAL_MAX_RESULTS=5
export RAG_MIN_SCORE=0.3

export RAG_KNOWLEDGE_ASYNC_EXCHANGE=rag.knowledge.exchange
export RAG_KNOWLEDGE_ASYNC_QUEUE=rag.knowledge.document.process
export RAG_KNOWLEDGE_ASYNC_ROUTING_KEY=rag.knowledge.document.process
```

## 启动方式

### 1. 启动业务中心

在 `business-center` 目录执行：

```bash
mvn spring-boot:run
```

默认端口：

```text
http://localhost:8091
```

### 2. 启动主系统

在项目根目录执行：

```bash
mvn spring-boot:run
```

默认端口：

```text
http://localhost:8080
```

## 建议联调顺序

1. 启动 MySQL、PostgreSQL、RabbitMQ
2. 启动 `business-center`
3. 启动主系统 `AI-RAG`
4. 登录系统
5. 创建知识库
6. 上传文档
7. 等待异步解析与向量化完成
8. 创建聊天会话并发起提问

## 角色说明

系统中常见角色：

- `USER`
  - 普通用户
  - 只能使用被授权的知识库与自己的聊天能力
- `KB_ADMIN`
  - 知识库管理员
  - 可管理知识库、文档、重建索引
- `ADMIN`
  - 平台管理员
  - 可管理用户、角色、系统配置

业务中心中的用户类型：

- `EMPLOYEE`
- `CUSTOMER`

注意：角色与业务用户类型是两个维度，不是同一个概念。

## 主要接口

### 认证

- `POST /api/auth/login`
- `GET /api/auth/me`

### 知识库

- `POST /api/knowledge/bases`
- `GET /api/knowledge/bases`
- `DELETE /api/knowledge/bases/{knowledgeBaseId}`

### 文档

- `POST /api/knowledge/documents/upload?knowledgeBaseId=xxx`
- `GET /api/knowledge/documents?knowledgeBaseId=xxx`
- `GET /api/knowledge/documents/{documentId}/chunks`
- `POST /api/knowledge/documents/{documentId}/rebuild-index`
- `DELETE /api/knowledge/documents/{documentId}`

### 聊天

- `POST /api/chat/sessions`
- `GET /api/chat/sessions?userId=1`
- `GET /api/chat/sessions/{sessionId}/messages?userId=1`
- `DELETE /api/chat/sessions/{sessionId}?userId=1`
- `POST /api/chat/stream`

## HTTP 调试示例

仓库内提供了请求示例：

- [requests/ai-rag.http](C:/Users/xiongjiahao/Desktop/AI-RAG/requests/ai-rag.http)

如果你使用 IntelliJ IDEA，可以直接打开并逐条执行。

## 示例知识库文档

项目里自带了一批演示文档，可用于快速重建知识库：

- [data/sample-kb-docs](C:/Users/xiongjiahao/Desktop/AI-RAG/data/sample-kb-docs)

上传目录默认为：

- `data/uploads`

## 常见问题

### 1. PostgreSQL 提示找不到 `vector`

先安装扩展，再执行：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### 2. 上传文档后无法检索

重点检查：

- 文档是否上传到正确的 `knowledgeBaseId`
- `parse_status` 是否为 `SUCCESS`
- PostgreSQL `kb_document_chunk` 是否已有分块数据
- `DASHSCOPE_EMBEDDING_MODEL` 与 `RAG_EMBEDDING_DIMENSION` 是否匹配

### 3. 调模型时报 403

如果错误包含：

- `AllocationQuota.FreeTierOnly`

说明百炼账号免费额度已用尽，需要在控制台关闭“仅使用免费额度”或切换到可用模型。

### 4. 业务动作提示未登录或登录失效

通常是主项目和 `business-center` 端口或 `BUSINESS_CENTER_BASE_URL` 配错，导致请求打回了主项目自身。

## 当前定位

这个项目更适合作为：

- 本地开发与联调示例
- RAG 链路验证项目
- 业务动作编排验证项目

如果用于更正式场景，建议继续补充：

- 更完整的异常码体系
- 更严格的权限模型
- 文档异步处理监控
- 审计日志
- Docker / Compose 标准化部署文档
- 自动化测试与 CI
