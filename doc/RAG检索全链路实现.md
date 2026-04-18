# RAG 检索全链路实现

> 涵盖文档写入（向量化入库）和查询（对话时检索）两个阶段的完整实现流程。

---

## 一、基础设施层（连接与存储）

### 配置链路

```
application.yml (ai.pgVector.*)
        ↓
   AiConfig.PgVector ← @ConfigurationProperties(prefix="ai")
        ↓
   Langchain4jConfig.embeddingStore() ← @Bean，构建 PgVectorEmbeddingStore
        ↓
   PgVectorEmbeddingStore（LangChain4j 提供的向量存储实现）
        ↓
   PostgreSQL embedding 表: embedding_id(UUID), embedding(vector 768d), text, metadata(JSONB)
```

### PgVectorEmbeddingStore Bean 配置

文件：`com.ruoyi.ai.config.Langchain4jConfig`

```java
@Configuration
public class Langchain4jConfig {

    @Resource
    private AiConfig aiConfig;

    @Bean
    public PgVectorEmbeddingStore embeddingStore() {
        AiConfig.PgVector pgVector = aiConfig.getPgVector();
        return PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getPort())
                .database(pgVector.getDatabase())
                .user(pgVector.getUser())
                .password(pgVector.getPassword())
                .table(pgVector.getTable())
                .dimension(pgVector.getDimension())    // 默认 768
                .build();
    }
}
```

### PgVector 表结构

```sql
-- PostgreSQL embedding 库中的 embedding 表
CREATE TABLE embedding (
    embedding_id UUID PRIMARY KEY,
    embedding   vector(768),      -- pgvector 向量类型
    text        TEXT,              -- 原始文本
    metadata    JSONB              -- 元数据（含 kb_id）
);
```

### AiConfig 配置类

文件：`com.ruoyi.ai.config.AiConfig`

关键配置项：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `ai.pgVector.host` | PgVector 主机 | — |
| `ai.pgVector.port` | PgVector 端口 | 5432 |
| `ai.pgVector.database` | 数据库名 | embedding |
| `ai.pgVector.table` | 表名 | embedding |
| `ai.pgVector.dimension` | 向量维度 | 768 |

---

## 二、写入阶段：文档向量化入库

### 入口：KnowledgeBaseController.embedding()

```
前端上传文档 → POST /ai/knowledgeBase/embedding
```

请求参数 `EmbeddingReq`：
- `embeddingModelId`: Embedding 模型 ID
- `kbId`: 知识库 ID
- `fileList`: 上传文件信息
- `maxSegmentSize`: 最大分段大小
- `maxOverlapSize`: 段间重叠大小

### 完整调用链

```
KnowledgeBaseController.embedding()
  │
  ├─ 1. docSplitSegment(req)
  │     ├─ FileSystemDocumentLoader.loadDocument(filePath)
  │     │     // 从磁盘加载文档（支持 PDF/DOCX/TXT 等）
  │     └─ DocumentSplitters.recursive(maxSegmentSize, maxOverlapSize).split(document)
  │           // 递归分段，段之间有 overlap 保留上下文
  │
  ├─ 2. 给每个 TextSegment 添加 metadata: kb_id = kbId
  │     segments.forEach(seg -> seg.metadata().put("kb_id", kbId));
  │
  └─ 3. AsyncManager 异步执行向量化（不阻塞 HTTP 响应）
        │
        └─ LangChain4jServiceImpl.embedTextSegments(embeddingModel, segments, callback)
              │
              ├─ 按 batchSize 分批（从 sys_config "ai.embedding.batchSize" 读取）
              │
              └─ 每批调用 doEmbedding():
                    │
                    ├─ embeddingModel.embedAll(textSegments)
                    │     // 文本 → 768维向量（详见"文本向量化实现"章节）
                    │
                    └─ embeddingStore.addAll(embeddings, textSegments)
                          // INSERT INTO embedding (embedding_id, embedding, text, metadata)
                          // PgVectorStore 自动生成 UUID、序列化向量
```

### 分段逻辑

```java
// LangChain4jServiceImpl.java
@Override
public List<TextSegment> splitDocument(String docFile, int maxSegmentSize, int maxOverlapSize) {
    Document document = FileSystemDocumentLoader.loadDocument(docFile);
    DocumentSplitter splitter = DocumentSplitters.recursive(maxSegmentSize, maxOverlapSize);
    return splitter.split(document);
}
```

`DocumentSplitters.recursive()` 的分段策略：
- 优先按段落/句子边界分割
- 单段超过 `maxSegmentSize` 时继续递归分割
- 相邻段之间保留 `maxOverlapSize` 字符重叠，避免语义断裂

### 批量向量化

```java
// LangChain4jServiceImpl.java
@Override
public List<String> embedTextSegments(EmbeddingModel embeddingModel,
                                       List<TextSegment> textSegments,
                                       Consumer<List<TextSegment>> consumer) {
    String value = sysConfigService.selectConfigByKey("ai.embedding.batchSize");
    int batchSize = Integer.parseInt(value);
    List<String> ids = new ArrayList<>();

    if (textSegments.size() < batchSize) {
        ids = doEmbedding(embeddingModel, textSegments);
    } else {
        // 分批处理，避免单次向量化请求过大
        int loop = (textSegments.size() + batchSize - 1) / batchSize;
        for (int i = 0; i < loop; i++) {
            int from = i * batchSize;
            int end = Math.min(from + batchSize, textSegments.size());
            ids.addAll(doEmbedding(embeddingModel, textSegments.subList(from, end)));
        }
    }
    // 完成后 WebSocket 通知前端
    if (consumer != null) {
        consumer.accept(textSegments);
    }
    return ids;
}

private List<String> doEmbedding(EmbeddingModel embeddingModel, List<TextSegment> textSegments) {
    Response<List<Embedding>> response = embeddingModel.embedAll(textSegments);
    List<Embedding> embeddings = response.content();
    return embeddingStore.addAll(embeddings, textSegments);
}
```

---

## 三、查询阶段：对话时 RAG 检索

### 入口：AiChatServiceImpl.chat() 第 100-130 行

```java
if (promptTemplate.contains(Constants.KNOWLEDGE_BASE_TEMPLATE)
        && StringUtils.isNotBlank(aiAgent.getKbIds())) {

    List<Long> ids = Arrays.stream(aiAgent.getKbIds().split(","))
            .map(Long::valueOf).collect(Collectors.toList());

    EmbeddingModel embeddingModel = getEmbeddingModel();
    retrievalMaxResult = aiAgent.getMaxResult() == null ? 3 : aiAgent.getMaxResult();
    retrievalMinScore = aiAgent.getMinScore() == null ? 0.5 : aiAgent.getMinScore();

    List<EmbeddingMatch<TextSegment>> searchRes = langChain4jService.search(
            embeddingModel, prompt,
            retrievalMaxResult,
            retrievalMinScore,
            new IsIn("kb_id", ids));    // Filter: 只搜指定知识库
    // ...
}
```

### 完整检索流程

```
用户发送: "如何配置Redis连接？"
        ↓
Step 1: 确定 Embedding 模型
───────────────────────────────────────
getEmbeddingModel():
  优先读 sys_config "ai.model.embedding" 指定的模型 ID
  如果没配 → 取数据库中第一个 type=EMBEDDING 的模型
  → modelBuilder.getEmbeddingModel(model)  （有缓存复用）

        ↓
Step 2: 解析 Agent 检索参数
───────────────────────────────────────
kbIds = "1,3"        → 知识库 ID 列表
maxResult = 3 (默认)  → 最多返回 3 条匹配
minScore = 0.5 (默认) → 最低相似度阈值

        ↓
Step 3: 调用 LangChain4jServiceImpl.search()
───────────────────────────────────────
① embeddingModel.embed(query)
   "如何配置Redis连接？" → [0.012, -0.034, 0.056, ...]  (768维)

② 构建 EmbeddingSearchRequest:
   - queryEmbedding = 用户问题向量
   - maxResults = 3
   - minScore = 0.5
   - filter = IsIn("kb_id", [1, 3])

③ embeddingStore.search(request)
   PgVectorEmbeddingStore 生成 SQL（见下一章）

④ 返回 List<EmbeddingMatch<TextSegment>>

        ↓
Step 4: 拼接检索结果到提示词
───────────────────────────────────────
StringBuilder embBuilder = new StringBuilder();
searchRes.stream().map(EmbeddingMatch::embedded).forEach(embedded -> {
    embBuilder.append(embedded.text());    // 直接 append，无分隔符
});
promptTemplate = promptTemplate.replace("{data}", embBuilder.toString());

最终 promptTemplate:
  "你是一个AI助手。参考资料：Redis连接需要配置host和port...
   用户问题：如何配置Redis连接？"
```

---

## 四、检索结果注入提示词

```java
// AiChatServiceImpl.java 第 120-125 行
StringBuilder embBuilder = new StringBuilder();
searchRes.stream().map(EmbeddingMatch::embedded).forEach(embedded -> {
    String text = embedded.text();
    embBuilder.append(text);
});
promptTemplate = promptTemplate.replace(Constants.KNOWLEDGEBASE_TEMPLATE, embBuilder.toString());
```

### 拼接示例

假设检索到两条结果：

```
Row1: score=0.92  text="Redis连接需要配置host和port"
Row2: score=0.85  text="Spring Boot整合Redis的配置方法"
```

原始 promptTemplate：
```
"你是一个AI助手。以下是可以参考的资料：{data}。用户问题：{question}"
```

替换后：
```
"你是一个AI助手。以下是可以参考的资料：
 Redis连接需要配置host和portSpring Boot整合Redis的配置方法。
 用户问题：如何配置Redis连接？"
```

**注意**：两段文本直接拼接，中间没有换行或分隔符。

---

## 五、写入与查询数据流转图

```
                        写入阶段                          查询阶段
                    ───────────────                  ───────────────

  文档.pdf           TextSegment[]                   用户问题 "Redis?"
     │                    │                              │
     ▼                    ▼                              ▼
 FileSystemLoader    递归分段器                    embed("Redis?")
     │              + kb_id metadata              → [0.01, -0.03, ...]
     │                    │                              │
     │                    ▼                              ▼
     │            embedAll(segments)          EmbeddingSearchRequest
     │           → [[0.02,...],               + IsIn("kb_id",[1,3])
     │              [0.05,...],                        │
     │              ...]                               ▼
     │                    │                     PgVector SQL 搜索
     │                    ▼                     (余弦相似度 >= 0.5)
     │           PgVector INSERT                  │
     │           ┌──────────────┐                 ▼
     │           │ embedding_id │           3条匹配结果
     │           │ embedding    │           → 拼入 {data}
     │           │ text         │           → LLM 生成回答
     │           │ metadata:    │
     │           │   {"kb_id":1}│
     │           └──────────────┘
     │               PostgreSQL
     └───────────────────┘
```

**核心要点**：写入和查询使用**同一个 Embedding 模型**做向量化，这是语义搜索能工作的前提 — 文档段落和用户问题被映射到同一个向量空间，距离近的向量意味着语义相似。`kb_id` metadata 贯穿写入和查询两端，实现了知识库级别的数据隔离。

---

## 六、RAG 检索调试日志

代码中在第 132-135 行记录了 RAG 检索的调试信息：

```java
log.info(
    "RAG_DEBUG triggered={}, kbIds={}, hitCount={}, minScore={}, maxResult={}, queryPreview={}, top1Score={}",
    retrievalTriggered, aiAgent.getKbIds(), retrievalHitCount, retrievalMinScore,
    retrievalMaxResult, retrievalQueryPreview, retrievalTop1Score);
```

可用于排查：
- `triggered=false` → 模板不含 `{data}` 或 kbIds 为空
- `hitCount=0` → 无匹配结果，可能 minScore 过高或知识库无数据
- `top1Score` 很低 → 检索质量差，考虑调整 minScore 或更换 Embedding 模型
