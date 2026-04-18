# AiChatServiceImpl 核心架构解析

> 基于源码深度分析，涵盖对话编排、流式响应、思考链、限流会话等核心技术要点。

---

## 一、整体定位

`AiChatServiceImpl` 是 AI 对话的**核心编排器**，位于 `com.ruoyi.ai.service.impl` 包，负责将 LLM 调用、RAG 检索、对话记忆、思考链处理、消息持久化、限流会话等多个子系统串联成完整的对话流程。

```
Controller (限流前置检查)
    └─→ AiChatServiceImpl.chat()
            ├─ 1. 加载模型 → ModelBuilder
            ├─ 2. RAG检索  → LangChain4jService + PgVector
            ├─ 3. 对话记忆 → TimedCache + ChatMemory
            ├─ 4. 构建请求 → ChatRequest
            ├─ 5. 流式响应 → Flux<String> + StreamingChatResponseHandler
            └─ 6. 异步持久化 → AsyncManager
```

### 接口方法总览

| 方法 | 作用 | 要点 |
|------|------|------|
| `chat()` | 核心对话方法 | 流式响应，返回 `Flux<String>` |
| `createSession()` | 创建会话 | UUID 生成 sessionId，存 Redis |
| `checkClientSession()` | 校验 clientId 与 sessionId 匹配 | Redis 读取比对 |
| `checkIfOverLmtRequest()` | 滑动窗口限流 | Redis Lua 脚本，窗口=1天 |
| `listClientSession()` | 查历史会话列表 | 标题截断到5字 + "..." |
| `deleteSession()` | 删除会话 | 同时清理 TimedCache 中的记忆 |
| `listAgentChatMessageBySessionId()` | 查会话聊天记录 | 按 sessionId + agentId 查询 |

---

## 二、chat() 方法逐段解析（第 84-238 行，核心方法）

### Phase 1：模型加载（85-87 行）

```java
Model model = modelService.selectModelById(aiAgent.getModelId());
StreamingChatModel llm = modelBuilder.getStreamingLLM(model);
```

- 从数据库 `model` 表查出智能体关联的 LLM 配置
- `ModelBuilder.getStreamingLLM()` 根据 `provider` 分发创建：
  - **Ollama**: `OllamaStreamingChatModel` + `think(true)` + `returnThinking(true)` — 启用思考链
  - **OpenAI**: `OpenAiStreamingChatModel` + `returnThinking(true)` + `apiKey`

**关键设计**：原始代码每次对话都重新创建模型实例（已优化为缓存复用，见"模型实例缓存方案"章节）。

### Phase 2：提示词模板处理（88-130 行）

提示词占位符：
- `{question}` — 用户输入占位符（必须）
- `{data}` — RAG 检索内容占位符（可选，有知识库时替换）

三个分支逻辑：

| 条件 | 行为 |
|------|------|
| 模板含 `{data}` **且** `kbIds` 非空 | 触发 RAG 检索，将检索结果拼入模板 |
| 模板含 `{data}` **但** `kbIds` 为空 | 清空 `{data}` 占位符（无知识库可查） |
| 模板不含 `{data}` | 跳过，纯 LLM 对话 |

```java
// 替换用户问题
promptTemplate = promptTemplate.replace(Constants.USER_MSG_TEMPLATE, prompt);

// RAG 检索条件判断
if (promptTemplate.contains(Constants.KNOWLEDGE_BASE_TEMPLATE) && StringUtils.isNotBlank(aiAgent.getKbIds())) {
    // 触发 RAG 检索...
}
```

### Phase 3：对话记忆（139-149 行）

```java
private static final TimedCache<String, ChatMemory> chatMemories = new TimedCache<>(
        TimeUnit.DAYS.toMillis(1));
```

- `MessageWindowChatMemory` — 基于**滑动窗口**的记忆，保留最近 N 条消息（`ai_agent.memory_count`）
- 底层存储：`InMemoryChatMemoryStore` — 纯内存，进程重启丢失
- 缓存 key = `"ai:agent:memory:" + sessionId`
- `memoryCount <= 0` 时不启用记忆

**局限**：
1. 不可持久化，重启丢失
2. `TimedCache` 是 Hutool 的本地缓存，不支持分布式
3. 系统 `systemMessage` 每次都 add 进 memory，可能导致窗口中 system 消息重复

### Phase 4：构建 ChatRequest（152-175 行）

```java
ChatRequest.builder()
    .parameters(parameters)     // temperature, maxOutputTokens, modelName
    .messages(...)              // 三种情况：
    //   有 memory → chatMemory.messages()（包含 system + history + current）
    //   无 memory 有 systemMsg → [systemMsg, userMsg]
    //   都没有 → [userMsg]
    .build();
```

### Phase 5：流式响应 + 思考链处理（177-237 行）

这是最复杂的部分，用 `Flux.create` + `ThreadUtil.execute` 实现了**响应式流式输出**：

```
StreamingChatModel.chat(request, handler)
  ├─ onPartialThinking()   → 收到思考片段
  ├─ onPartialResponse()   → 收到正文片段
  ├─ onCompleteResponse()  → 全部完成
  └─ onError()             → 出错
```

**思考链处理逻辑**：

```
onPartialThinking:
  第一个思考片段 → 加 ``` 前缀
  后续片段      → 直接发送
  计数器 thinkCount++

onPartialResponse:
  如果 thinkCount > 0 且 thinkFinish == false
    → 说明刚从思考切换到正文，加 ``` 后缀闭合思考块
    → thinkFinish = true
  后续正文片段 → 直接发送

onCompleteResponse:
  取完整 thinking + text → 异步保存到 chat_message 表
```

**流输出格式**：每个 chunk 都是 JSON `{"msg":"..."}`

**为什么用 `ThreadUtil.execute`？** 因为 LangChain4j 的 `StreamingChatResponseHandler` 是回调模式（非 Reactive），而返回值需要是 `Flux<String>`。通过在新线程中发起 LLM 调用，用 `fluxSink.next()` 桥接回调到响应式流。

```java
return Flux.create(fluxSink -> {
    ThreadUtil.execute(() -> {
        llm.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialThinking(PartialThinking partialThinking) { /* ... */ }

            @Override
            public void onPartialResponse(String partialResponse) { /* ... */ }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) { /* ... */ }

            @Override
            public void onError(Throwable error) {
                fluxSink.error(error);
            }
        });
    });
});
```

### Phase 6：异步持久化（264-285 行）

```java
AsyncManager.me().execute(new TimerTask() { ... });
```

使用若依框架自带的异步管理器（非 Spring `@Async`），用 `TimerTask` 包装：
- 用户消息：在 RAG 之后、LLM 调用之前保存（第137行）
- AI 回复：在 `onCompleteResponse` 中保存，包含完整 thinking + 正文

---

## 三、限流机制

### Redis Lua 滑动窗口限流

```java
public boolean checkIfOverLmtRequest(Long agentId, Integer dayLmtPerClient, String clientId) {
    Long number = redisTemplate.execute(limitScript,
            Collections.singletonList(Constants.AI_AGENT_CHAT_LMT + agentId + ":" + clientId),
            dayLmtPerClient,
            (int) TimeUnit.DAYS.toSeconds(1));
    // ...
}
```

- Redis key：`ai:agent:limit:{agentId}:{clientId}`
- 窗口：1天（86400秒）
- 每个 clientId 对每个 agent 独立计数
- 超出 `dayLmtPerClient` 后拒绝请求

### 会话管理

```java
// 创建会话
public String createSession(String clientId) {
    String sessionId = IdUtils.fastSimpleUUID();
    redisTemplate.opsForValue().set(Constants.AI_CHAT_CLIENT_SESSION + clientId, sessionId);
    return sessionId;
}
```

- clientId ↔ sessionId 映射存 Redis（`ai:chat:client:{clientId}`）
- 注意：Controller 中 `checkClientSession` 校验被注释掉了（第51-53行）

---

## 四、Controller 层调用流程

```java
// AiChatController.java
@PostMapping(produces = {MediaType.TEXT_EVENT_STREAM_VALUE})
public Flux<String> aiChat(@RequestBody @Valid ChatReq chatReq) {
    AiAgent aiAgent = aiAgentService.selectAiAgentById(chatReq.getAgentId());
    preCheck(aiAgent, chatReq);  // 限流检查
    return aiChatService.chat(aiAgent, chatReq.getPrompt(), chatReq.getClientId(),
            chatReq.getSessionId());
}
```

`preCheck()` 方法：
1. 检查每客户端每日请求限额是否超限
2. ~~检查 clientId 和 sessionId 是否匹配~~（已注释）

---

## 五、架构优缺点总结

**优点**：
1. **完整的端到端流程**：从模型加载 → RAG → 记忆 → 流式输出 → 持久化，一站式完成
2. **思考链支持**：通过 `PartialThinking` 回调实现思考过程与正文分离，前端可分别渲染
3. **限流设计**：Redis Lua 滑动窗口，精确控制每客户端每日调用上限
4. **可配置的 RAG**：minScore/maxResult/kbIds 都由 Agent 配置驱动

**值得关注的点**：
1. ~~**模型实例无缓存**~~：已通过 ModelBuilder 缓存方案解决
2. **RAG 拼接无分隔**：多个检索结果直接 `append`，可能导致上下文边界模糊
3. **systemMessage 可能重复进入 memory 窗口**（第161-163行每次都 add）
4. **限流校验被注释掉了**：Controller 第51-53行的 `checkClientSession` 被注释
5. **TimedCache 线程安全**：Hutool 的 `TimedCache` 默认非线程安全，高并发下可能需要开启同步
