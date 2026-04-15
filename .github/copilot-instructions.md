# `ruoyi-langchain4j` 的 Copilot 说明

## 构建与运行命令

- 在仓库根目录使用 Maven 构建后端：

```bash
mvn clean package -DskipTests
mvn -pl ruoyi-admin -am package -DskipTests
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

- 在 `ruoyi-ui` 目录安装并运行前端：

```bash
npm install
npm run dev
npm run build:prod
npm run build:stage
```

- 针对 AI 模块的单项 Java 测试命令：

```bash
mvn -pl ruoyi-ai -Dtest=AiTest test
```

`ruoyi-ui/package.json` 里没有单独的前端 lint 命令，仓库中可见的 Java 测试覆盖也比较有限。

## 架构概览

- 这是一个模块化的 RuoYi-Vue 单体项目。`ruoyi-admin` 是 Spring Boot 启动入口，并聚合 `ruoyi-framework`、`ruoyi-system`、`ruoyi-common`、`ruoyi-quartz`、`ruoyi-generator` 和 `ruoyi-ai`。
- 前端位于 `ruoyi-ui`，技术栈是 Vue 2 + Element UI。
- 业务/系统数据使用 MySQL，认证与缓存相关能力依赖 Redis，AI 检索与向量存储使用 PostgreSQL + `vector` 扩展，并通过 LangChain4j 的 pgvector 集成接入。
- `ruoyi-ai` 不是独立服务。它的配置通过 `ruoyi-admin/src/main/resources/application.yml` 中的 `ai.*` 绑定到 `AiConfig`，并由 `Langchain4jConfig` 在启动时直接创建 `PgVectorEmbeddingStore`。
- 由于 pgvector 存储会在启动阶段初始化，PostgreSQL 凭据错误、缺少 `vector` 扩展或相关 schema 不匹配，都会导致整个后端启动失败，而不只是 AI 功能异常。

## 仓库特有约定

- 遵循标准 RuoYi 控制器风格：控制器继承 `BaseController`，列表接口使用 `startPage()`，返回 `getDataTable(...)` 或 `AjaxResult`，后台管理接口使用 `@PreAuthorize("@ss.hasPermi('...')")` 做权限校验，写操作使用 `@Log` 记录审计日志。
- 持久层使用 MyBatis XML Mapper。修改数据库字段时，需要同时更新 domain 类、mapper 接口，以及 `src/main/resources/mapper/**/*Mapper.xml` 中对应的 XML。
- AI 管理端接口仍然遵循 RuoYi 常规 CRUD 风格。`AiAgentController`、`ModelController`、`KnowledgeBaseController` 都是标准后台控制器，不是单独定义的一套 API 风格。
- 公共 AI 聊天链路是特殊路径。修改聊天路由或匿名访问规则时，要同时保持下面四处一致：
  - `ruoyi-ai/.../AiChatController.java` 暴露 `/ai-chat/**`
  - `ruoyi-framework/.../SecurityConfig.java` 允许 `/ai-chat/**` 匿名访问
  - `ruoyi-ui/src/permission.js` 将 `/ai-chat/**` 加入白名单
  - `ruoyi-ui/src/router/index.js` 将 `/ai-chat/:agentId(.*)` 映射到聊天页面
- 公开访问的智能体链接由 `AiAgentController` 在服务端生成，路径格式是 `/ai-chat/{uuid-like-seed}`。不要只改其中一层的路径格式。
- 前端 HTTP 行为集中在 `ruoyi-ui/src/utils/request.js`。新增请求逻辑时优先复用其中的 token 注入、重复提交拦截和统一错误处理，不要绕开这层自己写一套。
- 前端 API 转发同时依赖 `ruoyi-ui/vue.config.js` 和各个 `.env.*` 文件。修改后端端口或 websocket 地址时，要同步更新代理目标和 websocket 配置。
- 当前仓库里的实际配置是后端端口 `8280`、前端开发端口 `8282`。把它们视为仓库当前状态，而不是仅仅当作本地临时覆盖。
- 知识库相关功能通常同时涉及 MySQL 中的元数据和 pgvector 中的向量数据。修改嵌入模型、向量维度或重新向量化流程时，要同时检查关系型记录和向量库侧的约束是否一致。

## 测试现实情况

- `ruoyi-ai/src/test/java/com/ruoyi/ai/test/AiTest.java` 更接近一个集成测试/调试样例，不是完全隔离的单元测试。它依赖真实模型接口和本地资源。
- 修改后端逻辑时，优先先做模块构建验证；只有在 AI 相关基础设施具备时，再运行定向测试。

## 仓库中可见的环境假设

- 根目录 `pom.xml` 当前声明的 Java 版本是 21。
- 前端开发环境通过 `/dev-api` 代理后端接口，目标地址由 `ruoyi-ui/vue.config.js` 中的配置决定。
- AI 流式聊天基于 WebFlux 的 `Flux<String>` 和 `text/event-stream`。如果不是明确要重构协议，修改聊天响应时应保持流式语义不变。
