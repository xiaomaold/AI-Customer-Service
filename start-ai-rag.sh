#!/usr/bin/env bash

export SERVER_PORT=8080

export MYSQL_DRIVER=com.mysql.cj.jdbc.Driver
export MYSQL_URL='jdbc:mysql://127.0.0.1:3307/ai_rag?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=123456

export PGVECTOR_DRIVER=org.postgresql.Driver
export PGVECTOR_URL='jdbc:postgresql://127.0.0.1:5433/ai_rag_vector'
export PGVECTOR_USERNAME=postgres
export PGVECTOR_PASSWORD=123456

export RABBITMQ_HOST=127.0.0.1
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=admin
export RABBITMQ_PASSWORD=123456
export RABBITMQ_VHOST=/airag

export DASHSCOPE_API_KEY='sk-de85666bd13845b99a9eeca7db6cfce0'
export DASHSCOPE_CHAT_MODEL='qwen-plus-2025-07-28'
export DASHSCOPE_STREAMING_MODEL='qwen-plus-2025-07-28'
export DASHSCOPE_EMBEDDING_MODEL='text-embedding-v4'

export RAG_HISTORY_LIMIT=10
export RAG_RETRIEVAL_TOP_K=5
export RAG_MAX_SEGMENT_SIZE=500
export RAG_MAX_OVERLAP_SIZE=100
export RAG_RETRIEVAL_MAX_RESULTS=5
export RAG_MIN_SCORE=0.3
export RAG_EMBEDDING_DIMENSION=1024
export RAG_UPLOAD_DIR='data/uploads'

export RAG_KNOWLEDGE_ASYNC_EXCHANGE=rag.knowledge.exchange
export RAG_KNOWLEDGE_ASYNC_QUEUE=rag.knowledge.document.process
export RAG_KNOWLEDGE_ASYNC_ROUTING_KEY=rag.knowledge.document.process

export JWT_SECRET='9sK4vQ2mX7pL1aN8rT6wY3hC5eB0uF9jD2zM7qR4kV1xP8nG6tH3cL0sW5yA2eU'
export JWT_EXPIRATION_SECONDS=7200

export BUSINESS_CENTER_BASE_URL='http://127.0.0.1:8091/api/business'

mvn spring-boot:run
