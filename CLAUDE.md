# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ruoyi-langchain4j (若依AI智能体系统) is an enterprise management system built on RuoYi-Vue, extended with AI capabilities via Langchain4j. It adds AI model management, knowledge base (RAG), intelligent agent management, and AI chat to the standard RuoYi admin platform.

## Build & Run Commands

### Backend (Maven, Java 17+, Spring Boot 2.5.15)
```bash
# Full build (skip tests)
mvn clean package -DskipTests

# Run the backend (entry point: ruoyi-admin)
java -jar ruoyi-admin/target/ruoyi-admin.jar

# Windows quick start
ry.bat start

# Linux quick start
./ry.sh start
```

### Frontend (Vue 2 + Element UI, port 8282)
```bash
cd ruoyi-ui
npm install
npm run dev              # Development server on port 8282
npm run build:prod       # Production build
```

### Docker
```bash
# Full stack (Redis, MySQL, PostgreSQL+pgvector, UI, Admin)
docker-compose up -d

# Standalone pgvector for RAG
docker-compose -f docker-compose-pgvector.yml up -d
```

## Module Architecture

```
ruoyi-langchain4j (parent POM, version 3.9.0)
├── ruoyi-admin          # Spring Boot entry point, config files, runs on port 8280
├── ruoyi-framework      # Spring Security (JWT), WebSocket, MyBatis, Redis, Druid config
├── ruoyi-system         # System management (users, roles, menus, depts) - domain/mapper/service
├── ruoyi-common         # Shared utilities, annotations, constants, exceptions, base classes
├── ruoyi-ai             # AI module: models, knowledge base, agents, chat (Langchain4j)
├── ruoyi-quartz         # Scheduled tasks
└── ruoyi-generator      # Code generation
```

**Dependency flow:** admin → framework → system → common; admin → ruoyi-ai → common/system

## Key Configuration

- **Backend config:** `ruoyi-admin/src/main/resources/application.yml` (port 8280, Redis, JWT, pgvector)
- **Database config:** `ruoyi-admin/src/main/resources/application-druid.yml` (MySQL `ry-vue` database)
- **Frontend config:** `ruoyi-ui/vue.config.js` (dev server port 8282, API proxy)
- **MyBatis mappers:** scanned from `classpath*:mapper/**/*Mapper.xml`, type aliases from `com.ruoyi.**.domain`

## AI Module (ruoyi-ai) Architecture

**Package:** `com.ruoyi.ai`

The AI module integrates Langchain4j 1.3.0 and provides:

### Core Components
- **ModelBuilder** (`service/impl/ModelBuilder.java`) — Factory for creating LLM/Embedding model instances supporting Ollama, OpenAI-compatible, and local ONNX providers
- **AiChatServiceImpl** (`service/impl/AiChatServiceImpl.java`) — Chat orchestration with streaming, RAG retrieval, memory management, and rate limiting
- **KnowledgeBaseServiceImpl** (`service/impl/KnowledgeBaseServiceImpl.java`) — Document splitting, embedding, vector storage via PgVector
- **LangChain4jServiceImpl** (`service/impl/LangChain4jServiceImpl.java`) — Wrapper for Langchain4j operations

### Key Patterns
- **Model types:** LLM and EMBEDDING (enum `ModelType`), providers: OLLAMA, OPEN_AI, LOCAL (enum `ModelProvider`)
- **RAG pipeline:** Upload document → split (recursive with overlap) → embed → store in PgVector → similarity search at query time
- **Chat streaming:** Uses `StreamingChatModel` with WebSocket for real-time response delivery
- **Push notifications:** Async operations (model download, document vectorization) push results via WebSocket
- **Chat memory:** `MessageWindowChatMemory` with `InMemoryChatMemoryStore`, keyed by session ID
- **PgVector config** in `application.yml` under `ai.pgVector` (host, port, database, table, dimension)

### Domain Entities
- `Model` — AI model config (baseUrl, apiKey, temperature, maxOutputToken, type, provider)
- `AiAgent` — Agent config (system prompt, user prompt template, knowledge bases, memory, rate limiting)
- `KnowledgeBase` — Knowledge base metadata
- `ChatMessage` — Chat session and message history

## Frontend Structure (ruoyi-ui)

- **AI views:** `src/views/ai/` — agent management (`agent/`), knowledge base (`knowledgeBase/`), model management (`model/`)
- **AI APIs:** `src/api/ai/` — aiChat.js, agent.js, knowledgeBase.js, knowledgeBaseData.js, model.js
- **Chat UI:** `src/views/ai/agent/aiChat.vue` — Tab-based sessions, streaming responses, markdown rendering (markdown-it + highlight.js)
- **WebSocket:** Configured in `src/layout/components/AppMain.vue` for async push notifications
- **Client IDs:** Uses `nanoid` for unique client identification in chat

## Infrastructure Requirements

- **MySQL 8.0** — Primary database (`ry-vue`)
- **Redis** — Caching and sessions
- **PostgreSQL + pgvector** — Vector storage for RAG embeddings (required for knowledge base features)
- **SQL init scripts:** `sql/ry_20250522.sql`, `sql/quartz.sql`

## Code Conventions

- Standard RuoYi layered architecture: Controller → Service interface → ServiceImpl → Mapper (MyBatis XML)
- Controllers extend `BaseController`, use `AjaxResult` for responses, `startPage()`/`getDataTable()` for pagination
- Domain entities use MyBatis XML mappers (not annotations) for SQL
- AI controller request DTOs are in `com.ruoyi.ai.controller.model` package
- Security: `/ai-chat/**` endpoints allow anonymous access (configured in `SecurityConfig`)
