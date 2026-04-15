# AI 模型支持架构说明

## AI 模型体系总览

```
┌─────────────────────────────────────────────────┐
│                  AiAgent (智能体)                  │
│  ┌──────────┐  ┌──────────┐  ┌───────────────┐  │
│  │ 系统提示词 │  │ 提示词模板 │  │ 记忆轮次/限流  │  │
│  └──────────┘  └──────────┘  └───────────────┘  │
│         │             │                          │
│         ▼             ▼                          │
│  ┌──────────┐  ┌──────────────┐                 │
│  │  LLM模型  │  │ Embedding模型 │  ← 知识库RAG   │
│  └──────────┘  └──────────────┘                 │
└─────────────────────────────────────────────────┘
```

## 1. 模型类型：LLM vs Embedding

项目通过 `ModelType` 枚举区分两种模型：

| 类型 | 枚举值 | 数据库type字段 | 用途 | 输出 |
|------|--------|---------------|------|------|
| **LLM** | `LLM(0)` | 0 | 对话生成，流式输出回答 | 文本（含思考链） |
| **Embedding** | `EMBEDDING(1)` | 1 | 文本向量化，知识库检索 | 向量（768维） |

**LLM** 用于智能体对话——用户提问后，LLM生成回答并流式返回。

**Embedding** 用于知识库RAG——文档分段后转成向量存入PgVector，用户提问时也转成向量做相似度搜索，匹配结果注入提示词模板的 `{data}` 位置。

核心代码在 `ModelBuilder.java`：

```java
// LLM — 流式对话
public StreamingChatModel getStreamingLLM(Model model) { ... }

// Embedding — 文本向量化
public EmbeddingModel getEmbeddingModel(Model model) { ... }
```

## 2. 模型提供商：Ollama / OpenAI / Local

`ModelProvider` 枚举定义了三种提供商，`ModelBuilder` 根据provider字段创建不同实现：

### 2.1 Ollama 提供商

```java
// LLM — 流式对话（支持深度思考）
OllamaStreamingChatModel.builder()
    .baseUrl(model.getBaseUrl())     // 如 http://localhost:11434
    .modelName(model.getName())      // 如 qwen2.5:7b
    .think(true)                     // 开启思考链
    .returnThinking(true)
    .build();

// Embedding — 文本向量化
OllamaEmbeddingModel.builder()
    .baseUrl(model.getBaseUrl())
    .modelName(model.getName())      // 如 nomic-embed-text
    .build();
```

- 需要本地或远程运行 Ollama 服务
- 无需 API Key
- 支持所有 Ollama 模型（Qwen、Llama、Mistral 等）

### 2.2 OpenAI 兼容提供商

```java
// LLM
OpenAiStreamingChatModel.builder()
    .baseUrl(model.getBaseUrl())     // 如 https://api.openai.com/v1 或兼容端点
    .modelName(model.getName())      // 如 gpt-4o
    .apiKey(model.getApiKey())       // 需要 API Key
    .returnThinking(true)
    .build();

// Embedding
OpenAiEmbeddingModel.builder()
    .baseUrl(model.getBaseUrl())
    .apiKey(model.getApiKey())
    .modelName(model.getName())      // 如 text-embedding-3-small
    .build();
```

- 支持所有 OpenAI 兼容 API（OpenAI、DeepSeek、通义千问、Moonshot 等）
- 需要配置 `baseUrl`、`apiKey`、`modelName`
- 新增/修改模型时 `ModelServiceImpl` 会自动调用 `checkModelConfig()` 发送测试请求验证连通性

### 2.3 Local 本地提供商（ONNX）

```java
// 仅支持 Embedding，不支持 LLM
new OnnxEmbeddingModel(
    saveDir + "/onnx/model.onnx",       // ONNX模型文件
    saveDir + "/onnx/tokenizer.json",   // 分词器文件
    PoolingMode.MEAN                     // 池化模式
);
```

- 完全离线运行，无需网络和GPU
- 使用 `shibing624/text2vec-base-chinese` 中文向量模型
- 通过 ONNX Runtime 推理，Java进程内直接运行

## 3. 本地向量模型在线下载功能

下载流程涉及 `ModelController`、`ModelScopeUtil`、`WebSocket` 三个组件协作：

```
前端点击"下载默认Embedding模型"
         │
         ▼
GET /ai/model/download-default-embedding
         │
         ▼ ModelController
检查是否已下载过LOCAL类型模型（防止重复）
         │
         ▼
ModelScopeUtil.downloadMultiThread()
    ├── 1. 调用ModelScope API列出所有文件
    │      GET https://modelscope.cn/api/v1/models/{repoId}/repo/files
    │
    ├── 2. 过滤文件（支持正则匹配）
    │      默认下载全部文件 [\s\S]*
    │
    ├── 3. 按文件大小排序（小文件先下）
    │
    ├── 4. 多线程并发下载（CountDownLatch等待全部完成）
    │      - 使用线程池（core=CPU核数, max=10）
    │      - 支持断点续传（Range请求头）
    │      - 自动创建目录结构
    │
    └── 5. 下载完成回调
           ├── 创建Model记录（name=repoId, type=1, provider="local"）
           ├── 写入sys_config: ai.model.embedding = 模型ID
           └── WebSocket推送通知："embedding模型已下载,保存位置：xxx"
```

关键代码 `ModelScopeUtil.java:164`：

```java
public String downloadMultiThread(String repoId, String saveDir,
                                   String allowFilePattern,
                                   Consumer<String> consumer) {
    // 列出所有文件
    List<ModelScopeFile> list = listModelFilesList(repoId, ...);
    // 过滤并排序
    list = list.stream().filter(...).sorted(...).collect(...);
    // 多线程下载
    CountDownLatch latch = new CountDownLatch(list.size());
    for (ModelScopeFile file : list) {
        threadPool.execute(() -> {
            downloadFile(file.getDownloadUri(), saveDir + "/" + file.getPath());
            latch.countDown();
        });
    }
    // 完成回调
    new Thread(() -> {
        latch.await();
        consumer.accept(root.getAbsolutePath());  // → 保存到数据库 + WebSocket通知
    }).start();
}
```

下载完成后保存的目录结构：

```
{saveDir}/
├── config.json
├── tokenizer.json
├── vocab.txt
├── onnx/
│   ├── model.onnx          ← ONNX推理模型
│   └── tokenizer.json      ← 分词器
└── ...其他模型文件
```

## 4. 默认 Embedding 模型切换与维度校验

系统通过 `sys_config` 表的 `ai.model.embedding` 配置项指定默认Embedding模型ID。切换默认模型时：

```
POST /ai/model/set-default-embedding/{id}
  │
  ├── 更新 sys_config: ai.model.embedding = 新ID
  │
  └── 如果与旧值不同 → WebSocket推送警告：
      "系统检测到默认embedding模型已改变，向量内容需要重新向量化，
       否则会影响查询结果"
```

维度校验 `GET /ai/knowledgeBase/check-embedding-dimension`：

```java
// 加载Embedding模型
EmbeddingModel embeddingModel = modelBuilder.getEmbeddingModel(model);
// 对"test"文本做向量化
int dimension = embeddingModel.embed("test").content().dimension();
// 与PgVector配置的维度对比
if (dimension != aiConfig.getPgVector().getDimension()) {
    throw new ServiceException("当前向量模型维度为" + dimension
        + "和向量数据库维度" + aiConfig.getPgVector().getDimension() + "不匹配");
}
```

默认配置维度为 **768**（`text2vec-base-chinese` 的输出维度），如果使用其他Embedding模型（如OpenAI的1536维），需要同步修改 `application.yml` 中的 `ai.pgVector.dimension` 并重建向量表。

## 5. 模型验证机制

新增或修改模型时，`ModelServiceImpl.checkModelConfig()` 会自动验证：

```java
private void checkModelConfig(Model model) {
    ModelProvider provider = ModelProvider.fromValue(model.getProvider());

    if (provider == ModelProvider.LOCAL) {
        // 本地模型：加载ONNX文件，对"test"文本做向量化验证
        langChain4jService.checkLocalEmbeddingModel(model.getSaveDir());
    } else {
        // 远程模型：发送测试请求（LLM发"hello"，Embedding发"test"）
        langChain4jService.checkModelConfig(baseUrl, apiKey, name, provider, type);
    }
    // 验证失败抛出 ServiceException("模型验证失败")
}
```

- **OLLAMA/OpenAI LLM** → 发送 `"hello"` 聊天请求
- **OLLAMA/OpenAI Embedding** → 发送 `"test"` 向量化请求
- **LOCAL Embedding** → 加载ONNX模型文件并嵌入 `"test"`

## 6. 相关源码文件索引

| 文件 | 路径 | 说明 |
|------|------|------|
| ModelBuilder | `ruoyi-ai/src/main/java/com/ruoyi/ai/service/impl/ModelBuilder.java` | 模型实例工厂 |
| ModelServiceImpl | `ruoyi-ai/src/main/java/com/ruoyi/ai/service/impl/ModelServiceImpl.java` | 模型管理服务（含验证） |
| LangChain4jServiceImpl | `ruoyi-ai/src/main/java/com/ruoyi/ai/service/impl/LangChain4jServiceImpl.java` | LangChain4j操作封装 |
| AiChatServiceImpl | `ruoyi-ai/src/main/java/com/ruoyi/ai/service/impl/AiChatServiceImpl.java` | 对话编排（LLM调用+RAG） |
| ModelController | `ruoyi-ai/src/main/java/com/ruoyi/ai/controller/ModelController.java` | 模型管理API |
| ModelScopeUtil | `ruoyi-ai/src/main/java/com/ruoyi/ai/util/ModelScopeUtil.java` | ModelScope多线程下载器 |
| AiConfig | `ruoyi-ai/src/main/java/com/ruoyi/ai/config/AiConfig.java` | AI配置（PgVector/ModelScope） |
| ModelType | `ruoyi-ai/src/main/java/com/ruoyi/ai/enums/ModelType.java` | 模型类型枚举 |
| ModelProvider | `ruoyi-ai/src/main/java/com/ruoyi/ai/enums/ModelProvider.java` | 模型提供商枚举 |
| Constants | `ruoyi-ai/src/main/java/com/ruoyi/ai/util/Constants.java` | 常量定义 |
