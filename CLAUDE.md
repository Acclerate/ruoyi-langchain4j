# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指导。

## 项目概述

ruoyi-langchain4j（若依AI智能体系统）是基于 RuoYi-Vue 3.9.0 开发的企业管理系统，通过 LangChain4j 扩展了 AI 能力。在标准若依管理平台上新增了 AI 模型管理、知识库（RAG）、智能体管理和 AI 对话功能。

### 技术栈
- **后端：** Spring Boot 2.5.15, Java 8+（source/target 1.8）, MyBatis（XML映射）, Spring Security 5.7.12（JWT认证）, Redis（Lettuce连接池）, Druid连接池, Spring WebFlux（Flux流式响应）
- **前端：** Vue 2.6.12 + Element UI 2.15.14, vue-router 3.4.9（history模式）, vuex 3.6.0, axios 0.28.1
- **AI框架：** LangChain4j 1.13.0（通过BOM统一管理，核心/open-ai为1.13.0，社区模块ollama/pgvector/easy-rag/embeddings为1.13.0-beta23），支持 Ollama、OpenAI兼容接口和本地ONNX推理
- **向量数据库：** PostgreSQL 15 + pgvector 扩展（默认维度768）
- **构建工具：** Maven, 父POM版本 3.9.0, 阿里云Maven镜像

### 前端关键依赖
- `markdown-it` 14.1.0 + `highlight.js` — AI回复的Markdown渲染和代码高亮
- `nanoid` 5.1.5 — 生成客户端唯一ID
- `echarts` 5.4.0 — 图表组件
- `quill` 2.0.2 — 富文本编辑器
- `compression-webpack-plugin` — Gzip压缩打包

## 构建与运行命令

### 后端（Maven）
```bash
# 完整构建（跳过测试）
mvn clean package -DskipTests

# 运行后端（入口：ruoyi-admin）
java -jar ruoyi-admin/target/ruoyi-admin.jar

# Windows 快速启动
ry.bat start

# Linux 快速启动
./ry.sh start
```

### 前端（端口8282）
```bash
cd ruoyi-ui
npm install
npm run dev              # 开发服务器，端口8282
npm run build:prod       # 生产环境构建
npm run build:stage      # 预发布环境构建
```

### Docker
```bash
# 完整部署（Redis + MySQL + PostgreSQL/pgvector + UI + Admin）
docker-compose up -d

# 仅启动 pgvector（用于RAG向量存储）
docker-compose -f docker-compose-pgvector.yml up -d
```

## 模块架构

```
ruoyi-langchain4j（父POM，版本3.9.0）
├── ruoyi-admin      # Spring Boot 启动模块，配置文件，运行端口8280
├── ruoyi-framework  # Spring Security（JWT）、WebSocket、MyBatis、Redis、Druid、AOP切面
├── ruoyi-system     # 系统管理（用户、角色、菜单、部门、字典、日志）- domain/mapper/service
├── ruoyi-common     # 公共工具、注解、常量、异常、基类、XSS过滤器
├── ruoyi-ai         # AI模块：模型管理、知识库、智能体、对话（LangChain4j）
├── ruoyi-quartz     # 定时任务（Quartz）
└── ruoyi-generator  # 代码生成器（Velocity模板）
```

**依赖关系：**
- `admin` → `framework` → `system` → `common`
- `admin` → `ruoyi-ai` → `common`, `system`, `framework`
- `ruoyi-ai` 额外依赖 `spring-webflux`（用于Flux流式响应）和 `lombok`

## 关键配置

### 后端配置文件
- **主配置：** `ruoyi-admin/src/main/resources/application.yml`
  - 服务端口：**8280**，上下文路径：`/`
  - Redis：`127.0.0.1:6379`，密码 `123456`，数据库0
  - JWT令牌：请求头 `Authorization`，密钥 `abcdefghijklmnopqrstuvwxyz`，有效期30分钟
  - MyBatis：`typeAliasesPackage: com.ruoyi.**.domain`，`mapperLocations: classpath*:mapper/**/*Mapper.xml`
  - 文件上传：单文件最大10MB，总大小20MB，上传目录 `D:/ruoyi/uploadPath`
  - Swagger：已启用，路径映射 `/dev-api`
  - XSS过滤：已启用，匹配路径 `/system/*,/monitor/*,/tool/*`
  - 验证码类型：`math`（数字计算）
  - AI配置段：`ai.pgVector`（host/port/database/user/password/table/dimension），`ai.modelScope`（downloadThreadNum/embeddingModelRepoId）

- **数据源配置：** `ruoyi-admin/src/main/resources/application-druid.yml`
  - MySQL `ry-vue` 数据库，地址 `127.0.0.1:3306`，账号 root/root
  - Druid连接池：initialSize=5, minIdle=10, maxActive=20
  - 慢SQL记录阈值：1000ms
  - Druid监控台：`/druid/*`，登录账号 ruoyi/123456

- **MyBatis配置：** `classpath:mybatis/mybatis-config.xml`
- **PageHelper：** 方言 `mysql`

### 前端配置
- **构建配置：** `ruoyi-ui/vue.config.js`
  - 开发服务器端口：8282，后端代理到 `http://localhost:8280`
  - 生产环境：开启Gzip压缩，代码分割（elementUI独立分包）
  - 网页标题：`若依AI智能体系统`
  - History模式路由

### Docker Compose 服务配置
| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| `redis` | redis:alpine | 6379 | 缓存、会话、限流 |
| `mysql` | mysql:8.0.39 | 3306 | 主数据库 `ry-vue`，字符集utf8mb4 |
| `postgres` | postgres:15-vectorchord0.4.2-pgvectors0.3.0 | 5432 | 向量数据库 `embedding`，用户root/root |
| `ruoyi-ui` | 自定义镜像 | 80 | 前端，挂载 `dist` 目录 |
| `ruoyi-admin` | 自定义镜像 | 8080 | 后端，挂载 `uploadPath` 目录 |

- PostgreSQL配置：max_connections=100, shared_buffers=256MB, effective_cache_size=768MB，资源限制1核/1G

## 数据库表结构（MySQL `ry-vue`）

### AI相关表

#### `model`（AI模型表）
| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| id | bigint unsigned auto_increment | - | 主键 |
| name | varchar(100) | - | 模型名称 |
| base_url | varchar(255) | - | API地址 |
| api_key | varchar(100) | null | API密钥 |
| temperature | double | 0.7 | 温度参数 |
| max_output_token | int unsigned | 2048 | 最大输出token数 |
| type | int unsigned | 0 | 模型类型（0=LLM, 1=EMBEDDING） |
| provider | varchar(100) | 'ollama' | 提供商（Ollama/Open AI/local） |
| save_dir | varchar(255) | null | 本地模型保存目录 |

#### `knowledge_base`（知识库表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint unsigned auto_increment | 主键 |
| name | varchar(100) | 知识库名称 |
| remark | varchar(255) | 备注 |

#### `ai_agent`（AI智能体表）
| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| id | bigint unsigned auto_increment | - | 主键 |
| name | varchar(100) | - | 智能体名称 |
| kb_ids | varchar(255) | null | 关联知识库ID（逗号分隔） |
| system_message | varchar(500) | - | 系统提示词 |
| memory_count | int unsigned | 3 | 记忆轮次 |
| model_id | bigint unsigned | - | 关联模型ID |
| status | int unsigned | 0 | 状态（0=停用, 1=启用） |
| visit_url | varchar(255) | null | 访问链接（如 `/ai-chat/xxx`） |
| day_lmt_per_client | int | -1 | 单客户端每日最大访问次数（-1=不限） |
| temperature | double | 0.7 | 温度参数（可覆盖模型默认值） |
| max_output_token | int unsigned | 2048 | 最大输出token数 |
| prompt_template | varchar(500) | '{question}' | 提示词模板 |
| min_score | double unsigned | null | 知识库查询最小相似度 |
| max_result | int unsigned | null | 知识库查询最多返回条数 |

#### `chat_message`（聊天消息表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint unsigned auto_increment | 主键 |
| role | varchar(100) | 角色（user/assistant/system） |
| content | longtext | 消息内容 |
| client_id | varchar(100) | 客户端ID（nanoid生成） |
| session_id | varchar(100) | 会话ID（UUID） |
| agent_id | bigint unsigned | 智能体ID |
| create_time | datetime | 创建时间 |

索引：`chat_message_client_id_idx(client_id)`

### PgVector表（PostgreSQL `embedding`数据库）
- 表名：`embedding`（由LangChain4j自动创建）
- 字段：`embedding_id(UUID)`, `embedding(vector)`, `text(text)`, `metadata(JSONB)`
- metadata中包含 `kb_id`（关联知识库）、`update_ts`、`update_by`

### 系统表（标准若依）
`sys_dept`, `sys_user`, `sys_role`, `sys_menu`, `sys_user_role`, `sys_role_menu`, `sys_role_dept`, `sys_user_post`, `sys_oper_log`, `sys_dict_type`, `sys_dict_data`, `sys_config`, `sys_logininfor`, `sys_job`, `sys_job_log`, `sys_notice`

### 重要 sys_config 配置项
| 配置键 | 说明 |
|--------|------|
| `ai.model.saveDir` | 本地模型保存目录 |
| `ai.model.embedding` | 默认Embedding模型ID |
| `ai.embedding.batchSize` | Embedding批处理大小 |
| `ai.agent.sessionNum` | 每个客户端每个智能体的最大会话数 |

### 字典类型
- `ai_model_type` — 映射到LLM类型值（0）

## AI模块（ruoyi-ai）架构

**包路径：** `com.ruoyi.ai`

### 枚举类
- **ModelType** (`enums/ModelType.java`): `LLM(0)`, `EMBEDDING(1)`
- **ModelProvider** (`enums/ModelProvider.java`): `OLLAMA("Ollama")`, `OPEN_AI("Open AI")`, `LOCAL("local")`

### 配置类
- **AiConfig** (`config/AiConfig.java`): `@ConfigurationProperties(prefix = "ai")`，包含：
  - `PgVector`内部类：host, port, database, user, password, table, dimension（默认768）
  - `ModelScope`内部类：downloadThreadNum（默认10），embeddingModelRepoId（默认 `zjwan461/shibing624_text2vec-base-chinese`）
- **Langchain4jConfig** (`config/Langchain4jConfig.java`): 创建 `PgVectorEmbeddingStore` Bean，从AiConfig读取PgVector连接参数

### 领域实体
所有实体继承 `BaseEntity`（来自ruoyi-common，提供createBy/createTime/updateBy/updateTime/remark）：
- **Model** (`domain/Model.java`): `id, name, baseUrl, apiKey, temperature, maxOutputToken, type, provider, saveDir`
- **AiAgent** (`domain/AiAgent.java`): `id, name, kbIds, kbNames, systemMessage, memoryCount, modelId, modelName, status, visitUrl, dayLmtPerClient, temperature, maxOutputToken, promptTemplate, minScore, maxResult`
- **KnowledgeBase** (`domain/KnowledgeBase.java`): `id, name`
- **ChatMessage** (`domain/ChatMessage.java`): `id, role, content, clientId, sessionId, agentId`

### 请求DTO（controller/model/包下）
| DTO | 字段 | 校验 | 说明 |
|-----|------|------|------|
| ChatReq | agentId, prompt, clientId, sessionId | @NotNull/@NotBlank | 对话请求 |
| DocSplitReq | maxSegmentSize, maxOverlapSize, fileList | - | 文档分段请求 |
| EmbeddingReq | kbId, embeddingModelId + 继承DocSplitReq | @NotNull | 文档向量化请求 |
| KbMatchReq | kbId, content, minScore, maxResult | @NotNull/@NotBlank | 知识库匹配查询 |
| TextEmbeddingReq | kbId, embeddingModelId, text, embeddingId | @NotNull/@NotBlank | 文本向量更新请求 |

### 常量定义（util/Constants.java）
| 常量 | 值 | 用途 |
|------|------|------|
| `KB_ID` | `"kb_id"` | PgVector metadata中的知识库ID键 |
| `KNOWLEDGE_BASE_TEMPLATE` | `"{data}"` | 提示词模板中RAG内容的占位符 |
| `USER_MSG_TEMPLATE` | `"{question}"` | 提示词模板中用户消息的占位符 |
| `THINK_PREFIX_TAG` | `"<think..."` | 深度思考开始标签 |
| `THINK_SUFFIX_TAG` | `"</think..."` | 深度思考结束标签 |
| `EMBEDDING_DIMENSION` | `768` | 默认Embedding向量维度 |
| `AI_CHAT_CLIENT_SESSION` | `"ai:chat:client:"` | Redis中客户端-会话映射的键前缀 |
| `AI_AGENT_CHAT_LMT` | `"ai:agent:limit:"` | Redis中客户端限流的键前缀 |
| `MEMORY_CACHE_KEY_PREFIX` | `"ai:agent:memory:"` | 内存中聊天记忆缓存的键前缀 |
| `TEST_EMBEDDING_TEXT` | `"test"` | 测试Embedding模型的文本 |
| `TEST_CHAT_TEXT` | `"hello"` | 测试Chat模型的文本 |
| `LOCAL_EMBEDDING_MODEL_FILE` | `"/onnx/model.onnx"` | 本地ONNX模型文件路径 |
| `LOCAL_EMBEDDING_TOKENIZER_FILE` | `"/onnx/tokenizer.json"` | 本地ONNX分词器文件路径 |

### 控制器与API端点

#### ModelController（`/ai/model`）
| 方法 | 路径 | 权限标识 | 说明 |
|------|------|----------|------|
| GET | `/list` | `ai:model:list` | 查询模型列表（分页） |
| GET | `/{id}` | `ai:model:query` | 获取模型详情 |
| POST | `/` | `ai:model:add` | 新增模型（自动验证连通性） |
| PUT | `/` | `ai:model:edit` | 修改模型（自动验证连通性） |
| DELETE | `/{ids}` | `ai:model:remove` | 删除模型 |
| GET | `/checkEmbeddingModel` | `ai:model:list` | 检查是否存在Embedding模型 |
| GET | `/download-default-embedding` | `ai:model:list` | 从ModelScope下载默认ONNX Embedding模型（多线程异步） |
| GET | `/local-embedding-model` | `ai:model:list` | 获取本地Embedding模型信息 |
| POST | `/set-default-embedding/{id}` | `ai:model:list` | 设置默认Embedding模型（切换时WebSocket警告需重新向量化） |
| GET | `/get-dimension/{id}` | `ai:model:list` | 获取模型的Embedding维度 |
| POST | `/export` | `ai:model:export` | 导出模型列表到Excel |

#### AiAgentController（`/ai/agent`）
| 方法 | 路径 | 权限标识 | 说明 |
|------|------|----------|------|
| GET | `/list` | `ai:agent:list` | 查询智能体列表（自动填充modelName和kbNames） |
| GET | `/{id}` | `ai:agent:query` | 获取智能体详情 |
| POST | `/` | `ai:agent:add` | 新增智能体（自动生成visitUrl为`/ai-chat/{uuid}`） |
| PUT | `/` | `ai:agent:edit` | 修改智能体 |
| DELETE | `/{ids}` | `ai:agent:remove` | 删除智能体 |
| POST | `/export` | `ai:agent:export` | 导出智能体列表到Excel |

#### AiChatController（`/ai-chat`）— **免认证访问**
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/`（SSE, `text/event-stream`） | 发送对话消息，返回流式 `Flux<String>` |
| GET | `/agent-info/{visitId}` | 通过visitUrl ID获取智能体信息 |
| POST | `/create-session/{clientId}` | 创建新会话，返回sessionId（UUID） |
| GET | `/list-chat-session/{clientId}/{agentId}` | 获取客户端的会话列表 |
| GET | `/list-chat-message/{sessionId}/{agentId}` | 获取会话聊天历史 |
| DELETE | `/delete-session/{sessionId}` | 删除会话及其所有消息 |

#### KnowledgeBaseController（`/ai/knowledgeBase`）
| 方法 | 路径 | 权限标识 | 说明 |
|------|------|----------|------|
| GET | `/list` | `ai:knowledgeBase:list` | 查询知识库列表 |
| GET | `/{id}` | `ai:knowledgeBase:query` | 获取知识库详情 |
| POST | `/` | `ai:knowledgeBase:add` | 新增知识库 |
| PUT | `/` | `ai:knowledgeBase:edit` | 修改知识库 |
| DELETE | `/{ids}` | `ai:knowledgeBase:remove` | 删除知识库 |
| POST | `/doc-split` | `ai:knowledgeBase:list` | 文档分段预览 |
| POST | `/embedding` | `ai:knowledgeBase:list` | 文档分段向量化（异步执行，完成后WebSocket通知） |
| GET | `/check-embedding-dimension` | `ai:knowledgeBase:list` | 验证Embedding维度是否与PgVector匹配 |
| GET | `/segment-query` | `ai:knowledgeBase:list` | 按知识库ID查询向量段（分页，从PgVector） |
| GET | `/segment-query/{id}` | `ai:knowledgeBase:list` | 按embeddingId查询单个向量段 |
| DELETE | `/segment-del/{ids}` | `ai:knowledgeBase:list` | 删除向量段 |
| PUT | `/segment-update` | `ai:knowledgeBase:list` | 更新向量段文本并重新向量化 |
| GET | `/match` | `ai:knowledgeBase:list` | 相似度搜索（kbId, content, minScore, maxResult） |
| POST | `/re-embedding/{kbId}` | `ai:knowledgeBase:list` | 重新向量化整个知识库（异步，完成后WebSocket通知） |
| POST | `/export` | `ai:knowledgeBase:export` | 导出知识库到Excel |

### 核心服务实现

#### ModelBuilder（`service/impl/ModelBuilder.java`）
模型实例工厂，负责创建LLM/Embedding模型实例：
- `getEmbeddingModel(Model)`: 根据provider创建 `OllamaEmbeddingModel`、`OpenAiEmbeddingModel` 或 `OnnxEmbeddingModel`（本地模型，使用 `model.onnx` + `tokenizer.json`，MEAN池化模式）
- `getStreamingLLM(Model)`: 创建 `OllamaStreamingChatModel` 或 `OpenAiStreamingChatModel`（开启 `think=true`, `returnThinking=true` 以支持深度思考）
- `getBlockingLLM(Model)`: 创建 `OllamaChatModel` 或 `OpenAiChatModel`（阻塞式，用于模型连通性验证）
- `getParameters(Model/AiAgent)`: 构建 `ChatRequestParameters`，设置温度和最大输出token。**注意：** 第97-107行存在bug — OLLAMA提供商使用了 `OpenAiChatRequestParameters`，而OpenAI提供商使用了 `OllamaChatRequestParameters`，二者互换了

#### AiChatServiceImpl（`service/impl/AiChatServiceImpl.java`）
对话编排核心逻辑：
1. 通过agentId加载智能体关联的模型，通过 `ModelBuilder` 创建 `StreamingChatModel`
2. 处理提示词模板：将 `{question}` 替换为用户输入，将 `{data}` 替换为RAG检索结果（仅当agent配置了kbIds时）
3. RAG检索：使用 `IsIn("kb_id", ids)` 过滤器查询PgVector，默认 minScore=0.7, maxResult=3
4. 通过 `AsyncManager` 异步保存用户消息到数据库
5. 创建/获取 `ChatMemory`（`MessageWindowChatMemory` + `InMemoryChatMemoryStore`，以sessionId为键，TTL 1天）
6. 构建 `ChatRequest`：系统消息 + 用户消息 + 历史记忆 + 模型参数
7. 返回 `Flux<String>` 流式响应，每个chunk为JSON格式 `{"msg": "..."}` — 深度思考内容（`<think...` / `</think...`）和正文分开处理
8. 完成后：将完整AI回复（含思考内容）异步保存到数据库

**限流机制：**
- `checkIfOverLmtRequest()`: 使用Redis Lua脚本，键模式 `ai:agent:limit:{agentId}:{clientId}`，滑动窗口1天
- 会话管理：clientId↔sessionId 映射存储在Redis中（`ai:chat:client:{clientId}`）

#### LangChain4jServiceImpl（`service/impl/LangChain4jServiceImpl.java`）
LangChain4j操作封装：
- `splitDocument()`: 通过 `FileSystemDocumentLoader` 加载文件，使用 `DocumentSplitters.recursive(maxSegmentSize, maxOverlapSize)` 递归分段
- `embedTextSegments()`: 批量向量化（批大小从 `ai.embedding.batchSize` 配置读取），存入 `PgVectorEmbeddingStore`
- `search()`: 将查询文本向量化后，在PgVector中执行相似度搜索，支持minScore/maxResult/metadata过滤
- `updateSegment()`: 重新向量化单个段（先删除旧向量再插入新向量）
- `removeSegment()`: 按ID删除向量
- `checkModelConfig()`: 验证模型连通性（发送测试chat/embed请求）
- `checkLocalEmbeddingModel()`: 验证本地ONNX模型可用性（嵌入测试文本）

#### ModelServiceImpl（`service/impl/ModelServiceImpl.java`）
模型管理服务：
- 新增/修改模型时自动调用 `checkModelConfig()` 验证连通性
- LOCAL类型模型调用 `checkLocalEmbeddingModel()` 验证ONNX文件
- 验证失败抛出 `ServiceException("模型验证失败")`

#### KnowledgeBaseServiceImpl / AiAgentServiceImpl / ChatMessageServiceImpl
标准CRUD服务，无特殊业务逻辑。

### 工具类
- **ModelScopeUtil** (`util/ModelScopeUtil.java`): 从ModelScope（modelscope.cn）多线程下载模型文件。使用 `CountDownLatch` + 线程池实现并发下载，支持文件列表查询、目录树浏览和断点续传（Range请求头）。下载完成后通过回调通知调用方。
- **PgVectorUtil** (`util/PgVectorUtil.java`): 直接JDBC访问PgVector，用于执行LangChain4j的 `PgVectorEmbeddingStore` 不支持的查询。使用 `PGSimpleDataSource` 创建连接，每次查询执行 `CREATE EXTENSION IF NOT EXISTS vector`。方法：`selectByMetadata()`（按metadata条件查询）、`selectById()`（按embedding_id查询）。
- **Constants** (`util/Constants.java`): AI模块全部常量定义（见上方常量表）

## 框架模块（ruoyi-framework）关键类

### 安全认证
- **SecurityConfig** (`config/SecurityConfig.java`): `SecurityFilterChain` 配置 — 禁用CSRF，无状态会话（JWT），`/ai-chat/**` 允许匿名访问，`/login`, `/register`, `/captchaImage` 公开访问。过滤器链：`CorsFilter` → `JwtAuthenticationTokenFilter` → `UsernamePasswordAuthenticationFilter`
- **JwtAuthenticationTokenFilter** (`security/filter/`): 从 `Authorization` 请求头提取JWT，验证令牌，设置SecurityContext
- **TokenService** (`web/service/TokenService.java`): JWT令牌创建/验证/刷新
- **PermissionService** (`web/service/PermissionService.java`): 提供 `@ss.hasPermi()` SpEL表达式供 `@PreAuthorize` 使用
- **PermitAllUrlProperties** (`config/properties/`): 自动收集标注了 `@Anonymous` 注解的URL，加入白名单

### WebSocket推送
- **WebSocketServer** (`websocket/WebSocketServer.java`): `@ServerEndpoint("/websocket/message")`，最大并发连接数100（Semaphore控制），由 `WebSocketUsers` 管理会话池（以session ID为键）
- **SocketMessage** (`websocket/SocketMessage.java`): 简单POJO，`content` + `type`（1=成功, 2=错误, 3=警告）
- **用途：** 异步操作通知 — 模型下载完成、文档向量化完成、Embedding模型切换时发出重新向量化警告
- **前端集成：** `AppMain.vue` 在 `mounted()` 时建立WebSocket连接（URL = `VUE_APP_WS_API + "?Authorization=" + token`），收到消息后根据type显示 Element UI Notification

### AOP切面
- **LogAspect** (`aspectj/LogAspect.java`): 处理 `@Log` 注解 → 记录操作日志到 `sys_oper_log` 表
- **RateLimiterAspect** (`aspectj/RateLimiterAspect.java`): 处理 `@RateLimiter` 注解 → Redis Lua脚本限流
- **DataScopeAspect** (`aspectj/DataScope.java`): 数据权限过滤，根据角色权限自动追加SQL条件
- **DataSourceAspect** (`aspectj/DataSourceAspect.java`): 动态数据源切换，处理 `@DataSource` 注解

### 其他框架类
- **AsyncManager** (`manager/AsyncManager.java`): 单例异步任务执行器（用于操作日志记录、聊天消息保存等）
- **AsyncFactory** (`manager/factory/AsyncFactory.java`): 异步任务工厂（记录登录日志、操作日志）
- **GlobalExceptionHandler** (`web/exception/`): 全局异常处理，统一返回 `AjaxResult.error()`
- **RepeatSubmitInterceptor** (`interceptor/`): 防重复提交拦截器
- **DruidConfig** (`config/DruidConfig.java`): 主从数据源配置
- **RedisConfig** (`config/RedisConfig.java`): Redis序列化配置（FastJson2）

## 公共模块（ruoyi-common）关键类

### 自定义注解
| 注解 | 说明 |
|------|------|
| `@Log` | 操作日志记录（title, businessType） |
| `@RateLimiter` | 接口限流（time, count, limitType） |
| `@DataScope` | 数据权限范围 |
| `@DataSource` | 动态数据源切换 |
| `@Excel` | Excel导入导出字段映射 |
| `@Anonymous` | 匿名访问标记（自动加入白名单） |
| `@RepeatSubmit` | 防重复提交 |

### 核心基类
- **BaseController**: 提供分页（`startPage()`/`getDataTable()`）、响应封装（`success()`/`error()`/`toAjax()`）、获取当前用户（`getUsername()`/`getUserId()`）等方法
- **BaseEntity**: 基础实体类，包含 `createBy`, `createTime`, `updateBy`, `updateTime`, `remark`, `params` 字段
- **AjaxResult**: 统一响应封装，继承 `HashMap`
- **TableDataInfo**: 分页数据封装（total, rows, code, msg）

### 响应与异常
- **AjaxResult**: 统一JSON响应，提供 `success()`, `error()`, `success(data)` 等静态方法
- **ServiceException**: 业务异常（带错误码和错误消息），由 `GlobalExceptionHandler` 统一处理
- **HttpStatus**: HTTP状态码常量

## 前端结构（ruoyi-ui）

### 路由配置（`src/router/index.js`）
- **路由模式：** `history`（无#号）
- **静态路由（constantRoutes）：**
  - `/redirect` — 重定向页面
  - `/ai-chat/:agentId(.*)` — AI对话页（隐藏，动态匹配agentId）
  - `/login`, `/register` — 登录/注册
  - `/404`, `/401` — 错误页
  - `/index` — 首页（Layout包裹）
  - `/user/profile` — 个人中心
- **动态路由（dynamicRoutes）：**
  - `/system/user-auth/role/:userId` — 分配角色（需 `system:user:edit`）
  - `/system/role-auth/user/:roleId` — 分配用户（需 `system:role:edit`）
  - `/system/dict-data/index/:dictId` — 字典数据（需 `system:dict:list`）
  - `/monitor/job-log/index/:jobId` — 调度日志（需 `monitor:job:list`）
  - `/tool/gen-edit/index/:tableId` — 代码生成配置（需 `tool:gen:edit`）
  - `/ai/knowledgeBase-data/index/:kbId` — 知识库数据管理（需 `ai:knowledgeBase:list`）
- **菜单路由：** 由后端 `sys_menu` 表动态加载

### AI视图（`src/views/ai/`）
```
ai/
├── agent/
│   ├── index.vue     # 智能体管理页（CRUD表格 + 对话框）
│   └── aiChat.vue    # AI对话页 — 公开访问，通过visitUrl路由进入
├── knowledgeBase/
│   ├── index.vue     # 知识库管理页
│   └── data.vue      # 知识库数据管理（向量段查看、向量化、相似度测试）
└── model/
    └── index.vue     # 模型管理页（CRUD、下载默认Embedding、设置默认Embedding）
```

### AI API模块（`src/api/ai/`）
| 文件 | 函数 |
|------|------|
| `model.js` | `listModel`, `getModel`, `addModel`, `updateModel`, `delModel`, `checkEmbeddingModel`, `downloadEmbeddingModel`, `localEmbeddingModel`, `setDefaultEmbeddingModel`, `getDimension` |
| `agent.js` | `listAgent`, `getAgent`, `addAgent`, `updateAgent`, `delAgent` |
| `aiChat.js` | `getAgent`(通过visitId), `createSession`, `listChatSession`, `listChatMessage`, `deleteSession` |
| `knowledgeBase.js` | `listKnowledgeBase`, `getKnowledgeBase`, `addKnowledgeBase`, `updateKnowledgeBase`, `delKnowledgeBase`, `match`(相似度测试), `reEmbedding`(重新向量化) |
| `knowledgeBaseData.js` | `documentSplit`, `embedding`, `segmentQuery`, `getSegment`, `delSegment`, `updateSegment`, `checkEmbeddingDimension` |

### AI对话页面（aiChat.vue）详细说明
- **路由：** 动态路由 `/ai-chat/:agentId`（匹配智能体的 `visitUrl`）
- **会话管理：** Tab标签页（el-tabs），会话列表通过API获取，clientId存储在 `localStorage`（`chat-clientId`），sessionId存储在 `localStorage`（`chat-sessionId`）
- **客户端ID：** 首次访问时通过 `nanoid()` 生成
- **流式响应：** 使用原生 `fetch()` + `ReadableStream` 读取器，解析SSE `data:` 行为JSON `{"msg": "..."}`，实时追加到AI消息气泡
- **Markdown渲染：** `markdown-it` 配合 `highlight.js` 代码高亮
- **思考过程显示：** `<think...` 和 `</think...` 之间的内容原样显示（灰色小字斜体），其余内容通过markdown-it渲染为HTML
- **智能体状态检查：** 页面加载时获取智能体信息，若status !== 1则提示并跳转 `/404`
- **AbortController：** 组件销毁时取消正在进行的流式请求

## 完整数据流

### RAG文档处理流程
1. 管理员通过知识库UI上传文档 → 文件保存到 `D:/ruoyi/uploadPath`
2. `POST /ai/knowledgeBase/doc-split` → `LangChain4jServiceImpl.splitDocument()` 加载文件，使用递归分段器分割（可配置分段大小和重叠大小）
3. `POST /ai/knowledgeBase/embedding` → Controller为每个段添加 `kb_id` metadata → `AsyncManager` 后台执行向量化 → `LangChain4jServiceImpl.embedTextSegments()` 批量嵌入 → 存入PgVector → WebSocket通知完成
4. 管理员可通过 `GET /ai/knowledgeBase/match` 测试检索效果 → 支持配置相似度阈值和返回条数

### AI对话流程
1. 用户通过visitUrl打开智能体（如 `/ai-chat/{uuid}`）→ `GET /ai-chat/agent-info/{visitId}` 获取智能体配置
2. 前端创建会话 → `POST /ai-chat/create-session/{clientId}` → 返回UUID格式的sessionId
3. 用户发送消息 → `POST /ai-chat`（SSE）携带 `{agentId, prompt, clientId, sessionId}`
4. 后端处理：检查限流 → 加载智能体配置 → 处理提示词模板 → RAG检索（如有kbIds） → 创建StreamingChatModel → 流式返回 `Flux<String>` 格式的 `{"msg": "chunk"}`
5. AI回复（含思考内容）通过异步任务保存到 `chat_message` 表
6. 前端：`fetch()` + `ReadableStream` 读取chunk → 实时更新消息气泡 → 渲染Markdown

### 提示词模板系统
- 默认模板：`{question}`（直接传入用户输入）
- RAG模板：包含 `{data}` 占位符 → 替换为检索到的知识库内容
- 系统消息：按智能体配置设置，作为 `SystemMessage` 前置
- 聊天记忆：`MessageWindowChatMemory`，可配置 `memoryCount`（最大消息轮次），存储在内存中，TTL 1天

## 代码规范

- 标准若依分层架构：Controller → Service接口 → ServiceImpl → Mapper（MyBatis XML）
- Controller继承 `BaseController`，使用 `AjaxResult` 封装响应，`startPage()`/`getDataTable()` 实现分页
- Domain实体继承 `BaseEntity`（提供createBy, createTime, updateBy, updateTime, remark）
- MyBatis使用XML映射（非注解）— 映射文件位于 `src/main/resources/mapper/ai/`
- AI Controller的请求DTO放在 `com.ruoyi.ai.controller.model` 包下
- 权限字符串：`@PreAuthorize("@ss.hasPermi('ai:model:list')")` — AI模块使用 `ai:` 前缀
- 异步操作使用 `AsyncManager.me().execute(TimerTask)` — 不是Spring的 `@Async`
- 长时间运行操作（模型下载、文档向量化）通过 `WebSocketUsers.sendMessageToUserByText(session, json)` 推送通知
- 聊天流式响应使用WebFlux `Flux<String>` 配合 `text/event-stream` 内容类型
- 聊天限流使用Redis Lua脚本，键模式 `ai:agent:limit:{agentId}:{clientId}`
- Lombok `@Data` 仅在 `ModelScopeUtil.ModelScopeFile` 中使用，其他Domain类手写getter/setter
- 日志使用SLF4J（`@Slf4j` 或 `LoggerFactory`）
- MyBatis XML中的动态SQL使用 `<if>` 条件判断和 `<trim>` 处理逗号

## 已知问题与注意事项

- **ModelBuilder参数bug**（第97-107行）：`getParameters()` 方法中OLLAMA提供商使用了 `OpenAiChatRequestParameters`，而非OLLAMA（OpenAI）提供商使用了 `OllamaChatRequestParameters`，二者互换了
- **聊天记忆仅存储在内存中**（`TimedCache`，TTL 1天）— 服务器重启后丢失；聊天历史持久化到MySQL但记忆上下文不恢复
- **对话端点无认证** — `/ai-chat/**` 完全公开访问（设计如此，用于匿名对话）
- **PgVectorUtil使用原始JDBC** — 每次查询创建新连接，无连接池；每次连接都执行 `CREATE EXTENSION IF NOT EXISTS vector`
- **Docker Compose中后端端口映射为8080** — 但 `application.yml` 配置的端口是8280，Docker部署时需注意端口映射一致性
- **前端AI聊天页面是独立路由** — 不使用Layout包裹，直接全屏显示对话界面

## SQL初始化脚本
- `sql/ry_20250522.sql` — 主数据库初始化脚本（含AI相关表：model, knowledge_base, ai_agent, chat_message）
- `sql/quartz.sql` — Quartz定时任务表结构
