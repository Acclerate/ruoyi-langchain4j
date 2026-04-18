# AI 模型实例缓存方案

> 解决 AiChatServiceImpl 每次对话都重建模型实例导致的 HttpClient 连接池浪费问题。

---

## 一、问题分析

### 原始代码问题

```java
// ModelBuilder.java — 原始实现（每次都 rebuild）
public StreamingChatModel getStreamingLLM(Model model) {
    ModelProvider provider = ModelProvider.fromValue(model.getProvider());
    StreamingChatModel llm = null;
    if (provider == ModelProvider.OLLAMA) {
        llm = OllamaStreamingChatModel.builder()
                .baseUrl(model.getBaseUrl())
                .modelName(model.getName())
                .think(true).returnThinking(true)
                .logRequests(true).logResponses(true)
                .build();   // 每次都 build 新实例
    } else {
        llm = OpenAiStreamingChatModel.builder()
                // ...
                .build();   // 每次都 build 新实例
    }
    return llm;
}
```

### 问题影响

| 影响项 | 说明 |
|--------|------|
| OpenAI HttpClient | 每次 build 都新建 OkHttp/HttpClient 连接池，无法复用 TCP 连接 |
| Ollama HttpClient | 虽然是本地调用，但仍有连接建立/销毁开销 |
| ONNX Runtime | Local Embedding 每次重新加载模型文件（~400MB），严重浪费 |
| GC 压力 | 频繁创建/销毁大对象，增加 GC 负担 |

---

## 二、解决方案：ConcurrentHashMap + 配置指纹 + 变更驱逐

### 缓存 key 设计（配置指纹）

```java
private String configKey(Long modelId, Model model) {
    return modelId + "#" + model.getProvider() + "#" + model.getBaseUrl()
            + "#" + model.getName() + "#" + model.getApiKey();
}
```

示例：数据库中 id=3 的 OpenAI 模型：

```
id=3, provider=Open AI, baseUrl=http://api.openai.com, name=gpt-4o, apiKey=sk-xxx
→ key: "3#Open AI#http://api.openai.com#gpt-4o#sk-xxx"
```

**设计原则**：
- 包含**影响模型实例创建**的字段（baseUrl、name、apiKey）
- 不包含**每次请求级别**的参数（temperature、maxOutputToken），这些通过 `getParameters(AiAgent)` 动态构建

### 缓存驱逐（evict）

```java
public void evict(Long modelId) {
    streamingChatCache.keySet().removeIf(k -> k.startsWith(modelId + "#"));
    blockingChatCache.keySet().removeIf(k -> k.startsWith(modelId + "#"));
    embeddingCache.keySet().removeIf(k -> k.startsWith(modelId + "#"));
}
```

调用 `evict(3)` 会删除所有以 `"3#"` 开头的 key，清除该模型的所有旧缓存实例。

---

## 三、改动的两个文件

### 文件 1：ModelBuilder.java

新增三个 `ConcurrentHashMap` 缓存，改造所有 build 方法：

```java
@Service
public class ModelBuilder {

    private final ConcurrentHashMap<String, StreamingChatModel> streamingChatCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ChatModel> blockingChatCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EmbeddingModel> embeddingCache = new ConcurrentHashMap<>();

    private String configKey(Long modelId, Model model) { /* 配置指纹 */ }

    public void evict(Long modelId) { /* 驱逐缓存 */ }

    // StreamingChatModel — 缓存 + 延迟构建
    public StreamingChatModel getStreamingLLM(Model model) {
        String key = configKey(model.getId(), model);
        return streamingChatCache.computeIfAbsent(key, k -> buildStreamingLLM(model));
    }

    private StreamingChatModel buildStreamingLLM(Model model) {
        // 原来的 build 逻辑
    }

    // ChatModel — 缓存 + 延迟构建
    public ChatModel getBlockingLLM(Model model) {
        String key = configKey(model.getId(), model);
        return blockingChatCache.computeIfAbsent(key, k -> buildBlockingLLM(model));
    }

    private ChatModel buildBlockingLLM(Model model) { /* ... */ }

    // EmbeddingModel — 缓存 + 延迟构建
    public EmbeddingModel getEmbeddingModel(Model model) {
        String key = configKey(model.getId(), model);
        return embeddingCache.computeIfAbsent(key, k -> buildEmbeddingModel(model));
    }

    private EmbeddingModel buildEmbeddingModel(Model model) { /* ... */ }

    // getParameters() 不变 — 这些是每次请求级别的参数，不需要缓存
}
```

### 文件 2：ModelServiceImpl.java

在模型增删改时触发缓存驱逐：

```java
@Service
public class ModelServiceImpl implements IModelService {

    @Resource
    private ModelBuilder modelBuilder;

    @Override
    public int updateModel(Model model) {
        checkModelConfig(model);
        model.setUpdateTime(DateUtils.getNowDate());
        int rows = modelMapper.updateModel(model);
        modelBuilder.evict(model.getId());    // 更新后驱逐
        return rows;
    }

    @Override
    public int deleteModelByIds(Long[] ids) {
        for (Long id : ids) {
            modelBuilder.evict(id);            // 删除前驱逐
        }
        return modelMapper.deleteModelByIds(ids);
    }

    @Override
    public int deleteModelById(Long id) {
        modelBuilder.evict(id);                // 删除前驱逐
        return modelMapper.deleteModelById(id);
    }
}
```

---

## 四、缓存行为场景表

| 场景 | 发生什么 |
|------|---------|
| 首次对话 | `configKey()` 生成新 key → cache miss → build 实例 → 存入缓存 |
| 后续对话（配置未变） | `configKey()` 生成相同 key → cache hit → 直接返回，不重建 |
| 管理员改了 apiKey | `evict(modelId)` 先清旧缓存 → 下次对话时 configKey 生成新 key → cache miss → 用新配置 rebuild |
| 管理员删除模型 | `evict(modelId)` 清除所有该模型的缓存，防止持有已删除模型的实例 |
| 旧 key 积累 | 旧 key 不再被查询自然淘汰，不会造成内存泄漏（模型数量极少，通常个位数） |

---

## 五、线程安全保证

- **`ConcurrentHashMap.computeIfAbsent`**：保证同一个 key 只 build 一次，并发请求不会重复创建
- **`ConcurrentHashMap.keySet().removeIf`**：线程安全的批量删除操作
- **模型实例本身线程安全**：LangChain4j 的 StreamingChatModel / EmbeddingModel 内部使用 OkHttp 连接池，天然支持并发调用

---

## 六、为什么不使用 Caffeine / Guava Cache

1. 模型实例数量极少（通常 3-10 个），`ConcurrentHashMap` 完全够用
2. 不需要 TTL 过期（模型实例没有时间维度的失效需求）
3. 不需要 LRU 淘汰（内存占用可控）
4. 不引入额外依赖

如果将来需要限制缓存大小或加 TTL，可以替换为 Caffeine：

```java
// 未来扩展方向（当前不需要）
private final Cache<String, StreamingChatModel> streamingChatCache = Caffeine.newBuilder()
        .maximumSize(50)
        .expireAfterAccess(Duration.ofHours(1))
        .build();
```
