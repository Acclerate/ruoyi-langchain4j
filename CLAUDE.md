# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

ruoyi-langchain4j（若依AI智能体系统）— 基于 RuoYi-Vue 3.9.0 + LangChain4j 的企业管理系统，在标准若依平台上新增 AI 模型管理、知识库（RAG）、智能体管理和 AI 对话功能。

### 技术栈
- **后端：** Spring Boot 2.5.15, Java 21, MyBatis（XML映射）, Spring Security 5.7.12（JWT）, Redis（Lettuce）, Druid, Spring WebFlux（Flux流式）, Lombok 1.18.36
- **前端：** Vue 2.6.12 + Element UI 2.15.14, vue-router 3.4.9（history）, vuex 3.6.0, axios 0.28.1
- **AI框架：** LangChain4j 1.13.0（BOM统一管理所有模块版本，含langchain4j/open-ai/ollama/pgvector/easy-rag/embeddings-all-minilm-l6-v2）
- **向量数据库：** PostgreSQL 15 + pgvector 扩展（默认维度768）
- **构建：** Maven, 父POM 3.9.0, 阿里云Maven镜像

## 构建与运行

```bash
# 后端构建
mvn clean package -DskipTests
java -jar ruoyi-admin/target/ruoyi-admin.jar   # 端口8280

# 前端开发
cd ruoyi-ui && npm install && npm run dev       # 端口8282，代理到8280

# Docker全栈
docker-compose up -d                            # Redis+MySQL+PgVector+UI+Admin
docker-compose -f docker-compose-pgvector.yml up -d  # 仅PgVector

# VS Code启动
# 已配置 .vscode/launch.json，F5 选择 "RuoYi Application"
# 入口类: com.ruoyi.RuoYiApplication (ruoyi-admin模块)
```

**前置服务：** MySQL 8.0（`ry-vue`库，root/root）, Redis（6379，密码123456）, PostgreSQL+pgvector（5432，root/root，`embedding`库）

## 模块架构与依赖

```
ruoyi-langchain4j (parent POM 3.9.0)
├── ruoyi-admin      → ruoyi-framework, ruoyi-ai, ruoyi-quartz, ruoyi-generator
├── ruoyi-framework  → ruoyi-system
├── ruoyi-system     → ruoyi-common
├── ruoyi-common     (基础层，无项目依赖)
├── ruoyi-ai         → ruoyi-common, ruoyi-system, ruoyi-framework (+ spring-webflux, langchain4j-bom, lombok)
├── ruoyi-quartz     → ruoyi-common
└── ruoyi-generator  → ruoyi-common
```

## AI模块核心架构（ruoyi-ai）

**包路径：** `com.ruoyi.ai` | **这是本项目区别于标准若依的核心模块**

### 模型体系
- **两种模型类型：** LLM(type=0) 对话生成 | Embedding(type=1) 文本向量化
- **三种提供商：** Ollama（无需API Key）| Open AI（需baseUrl+apiKey）| Local（ONNX本地推理，仅Embedding）
- **模型工厂：** `ModelBuilder.java` — 根据 `Model.provider` 创建对应 LangChain4j 模型实例
  - Ollama: `OllamaStreamingChatModel` / `OllamaEmbeddingModel`
  - OpenAI: `OpenAiStreamingChatModel` / `OpenAiEmbeddingModel`
  - Local: `OnnxEmbeddingModel(saveDir + "/onnx/model.onnx", saveDir + "/onnx/tokenizer.json", MEAN)`
- **模型验证：** `ModelServiceImpl` 新增/修改时自动调用 `checkModelConfig()` 发送测试请求验证连通性
- **已知bug：** `ModelBuilder.getParameters()` 第97-107行 OLLAMA/OpenAI 的 `ChatRequestParameters` 类型互换了

### RAG管道（知识库）
```
文档上传 → FileSystemDocumentLoader加载
        → DocumentSplitters.recursive(maxSegmentSize, maxOverlapSize)分段
        → 为每段添加kb_id metadata → 批量Embedding（batchSize可配）
        → PgVectorEmbeddingStore存储
        → 查询时: EmbeddingModel.embed(query) → PgVector相似度搜索 → 注入提示词{data}
```
- **向量存储：** PgVector表 `embedding`（embedding_id UUID, embedding vector, text, metadata JSONB）
- **默认Embedding：** `shibing624/text2vec-base-chinese`（768维，从ModelScope多线程下载）
- **下载器：** `ModelScopeUtil` — CountDownLatch + 线程池，支持断点续传，完成后WebSocket通知

### 对话流程
```
AiChatServiceImpl.chat():
1. 加载agent关联的LLM → ModelBuilder创建StreamingChatModel
2. 处理promptTemplate: {question}→用户输入, {data}→RAG检索结果
3. RAG: IsIn("kb_id", ids)过滤PgVector, 默认minScore=0.7, maxResult=3
4. ChatMemory: MessageWindowChatMemory(InMemoryChatMemoryStore, TTL=1天, key=sessionId)
5. 构建ChatRequest(systemMsg + userMsg + memory + parameters)
6. 返回Flux<String>流式响应, 每chunk为 {"msg":"..."}
   - 思考链: <think.../</think... 标签包裹, 与正文分开处理
7. 完成后异步保存AI回复(含thinking)到chat_message表
```

### 限流与会话
- **限流：** Redis Lua脚本, key=`ai:agent:limit:{agentId}:{clientId}`, 滑动窗口1天
- **会话：** clientId↔sessionId映射存Redis(`ai:chat:client:{clientId}`)
- **匿名访问：** `/ai-chat/**` 在SecurityConfig中配置为permitAll

### 关键配置项（application.yml → `ai:` 前缀）
- `ai.pgVector.*` — PgVector连接参数（host/port/database/user/password/table/dimension=768）
- `ai.modelScope.downloadThreadNum` — 模型下载线程数（默认10）
- `ai.modelScope.embeddingModelRepoId` — 默认向量模型仓库ID
- sys_config: `ai.model.saveDir`, `ai.model.embedding`(默认模型ID), `ai.embedding.batchSize`, `ai.agent.sessionNum`

### 提示词模板
- `{question}` — 用户输入占位符（必须）
- `{data}` — RAG检索内容占位符（可选，有知识库时替换）

## 框架关键约定

### 安全（ruoyi-framework）
- JWT无状态认证，过滤器链: `CorsFilter` → `JwtAuthenticationTokenFilter` → `UsernamePasswordAuthenticationFilter`
- 权限: `@PreAuthorize("@ss.hasPermi('ai:model:list')")`, AI模块用 `ai:` 前缀
- `@Anonymous` 注解自动加入白名单, `/ai-chat/**` 公开访问

### 分层架构（若依标准）
```
Controller(extends BaseController) → Service接口 → ServiceImpl → Mapper(MyBatis XML)
```
- 响应: `AjaxResult`(单对象) / `TableDataInfo`(分页列表)
- 分页: Controller中 `startPage()` 开启 → `getDataTable(list)` 包装
- BaseEntity: 所有Domain继承, 提供 createBy/createTime/updateBy/updateTime/remark
- MyBatis XML位置: `classpath*:mapper/**/*Mapper.xml`, typeAliases: `com.ruoyi.**.domain`

### 异步与推送
- 异步: `AsyncManager.me().execute(TimerTask)` — 非Spring @Async
- WebSocket: `@ServerEndpoint("/websocket/message")`, 最大100连接(Semaphore)
- 推送: `WebSocketUsers.sendMessageToUserByText(session, json)` — 用于模型下载完成、向量化完成等通知
- 前端: `AppMain.vue` mounted时建立WS连接, 收到消息显示Element UI Notification

### 自定义注解
- `@Log` → 操作日志（LogAspect → sys_oper_log）
- `@RateLimiter` → 接口限流（RateLimiterAspect → Redis Lua）
- `@DataScope` → 数据权限过滤（DataScopeAspect）
- `@DataSource` → 动态数据源切换
- `@Excel` → Excel导入导出映射
- `@Anonymous` → 匿名访问白名单
- `@RepeatSubmit` → 防重复提交

## 前端架构（ruoyi-ui）

### 路由
- history模式, 静态路由在 `src/router/index.js`, 动态路由从后端 `sys_menu` 加载
- AI对话: `/ai-chat/:agentId(.*)` — 静态路由, 隐藏, 无Layout包裹, 全屏对话
- 知识库数据: `/ai/knowledgeBase-data/index/:kbId` — 动态路由, 需 `ai:knowledgeBase:list`

### AI相关文件
- **视图：** `src/views/ai/agent/`(管理+对话), `src/views/ai/knowledgeBase/`(管理+数据), `src/views/ai/model/`(管理)
- **API：** `src/api/ai/` — `model.js`, `agent.js`, `aiChat.js`, `knowledgeBase.js`, `knowledgeBaseData.js`
- **对话页(aiChat.vue):** fetch+ReadableStream读取SSE, markdown-it+highlight.js渲染, nanoid生成clientId, localStorage存clientId/sessionId

### 构建配置
- `vue.config.js`: 端口8282, 代理到 `http://localhost:8280`, Gzip压缩, elementUI独立分包

## 数据库

### AI表（MySQL `ry-vue`）
- `model` — AI模型配置（type: 0=LLM/1=EMBEDDING, provider: Ollama/Open AI/local）
- `knowledge_base` — 知识库元数据（仅id+name+remark，向量数据在PgVector）
- `ai_agent` — 智能体配置（关联model_id, kb_ids逗号分隔, prompt_template, memory_count, day_lmt_per_client等）
- `chat_message` — 聊天历史（role: user/assistant/system, 关联client_id+session_id+agent_id）

### 向量表（PostgreSQL `embedding`库）
- `embedding` — embedding_id(UUID), embedding(vector 768d), text, metadata(JSONB含kb_id)

### 初始化脚本
- `sql/ry_20250522.sql` — 含AI表 + 系统表 + 菜单权限数据
- `sql/quartz.sql` — Quartz定时任务表

## 已知问题

1. **ModelBuilder参数bug**: `getParameters()` 中OLLAMA/OpenAI的 `ChatRequestParameters` 类型互换
2. **聊天记忆非持久**: `TimedCache`(TTL 1天), 重启丢失; MySQL存历史但不恢复记忆上下文
3. **对话端点无认证**: `/ai-chat/**` 完全公开（设计如此）
4. **PgVectorUtil无连接池**: 每次查询新建JDBC连接, 每次执行 `CREATE EXTENSION IF NOT EXISTS vector`
5. **Docker端口不一致**: docker-compose映射8080, application.yml配置8280
