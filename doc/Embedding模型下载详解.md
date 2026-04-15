# Embedding 模型下载详解

## 一、系统提示说明

当系统检测到尚未配置任何 Embedding 模型时，前端会弹出提示：

> **"系统检测到您还没有配置任何embedding模型，是否使用系统默认embedding模型？此操作会从modelscope上下载模型"**

点击确认后，系统会从阿里云 ModelScope（魔搭社区）自动下载 `shibing624/text2vec-base-chinese` 中文文本向量模型到本地，无需手动操作。

---

## 二、什么是 Embedding 模型？

### 2.1 一句话解释

**Embedding 模型就是把文本变成数字向量的工具。** 它能把一句话、一段文字转换成一个固定长度的数字数组（向量），使得语义相近的文本对应的数字向量在数学上也接近。

### 2.2 直观类比

想象一个巨大的"语义地图"：

```
                        动物
                       /    \
                    猫科      犬科
                   /    \    /    \
                 老虎   猫  狼    狗
                 
"猫"  → [0.12, 0.85, 0.33, ...]  ← 768个数字
"狗"  → [0.15, 0.78, 0.41, ...]  ← 和"猫"比较接近
"汽车" → [0.91, 0.05, 0.67, ...]  ← 和"猫""狗"距离很远
```

在这个地图上：
- **"猫"和"狗"** 的向量距离很近（都是宠物）
- **"猫"和"汽车"** 的向量距离很远（语义无关）
- **每段文本都映射为 768 个浮点数**（即 768 维向量）

### 2.3 技术原理

本项目使用的 `text2vec-base-chinese` 模型基于 **BERT（双向Transformer编码器）** 架构：

```
输入文本: "什么是机器学习？"
        │
        ▼
┌─────────────────────────┐
│  1. 分词 (Tokenizer)      │  → ["什么", "是", "机器", "学习", "？"]
│  2. 转为Token ID          │  → [101, 788, 3221, 6945, 739, 1408, 102]
│  3. Transformer编码       │  → 12层自注意力计算
│  4. 池化 (Mean Pooling)   │  → 取所有Token的平均值
│  5. 输出向量              │  → [0.023, -0.156, 0.782, ...]  (768维)
└─────────────────────────┘
```

关键参数：
- **模型层数：** 12层 Transformer
- **隐藏维度：** 768（这就是为什么配置中 `dimension: 768`）
- **词汇表大小：** 约 21,128 个中文词/字
- **模型参数量：** 约 1.02 亿（102M）
- **最大输入长度：** 512 个 Token

---

## 三、为什么需要 Embedding 模型？

### 3.1 核心原因：让计算机"理解"语义

传统搜索是**关键词匹配**，而 Embedding 实现的是**语义匹配**：

| 用户提问 | 关键词匹配 | 语义匹配（Embedding） |
|----------|-----------|---------------------|
| "如何提高销售额？" | 必须包含"提高"+"销售额" | 能匹配到"增加业绩的方法"、"提升营收策略" |
| "员工离职怎么办？" | 必须包含"离职" | 能匹配到"人员流失处理"、"员工辞退流程" |
| "系统登录不了" | 必须包含"登录" | 能匹配到"无法进入系统"、"密码错误怎么办" |

### 3.2 在本系统中的作用——RAG（检索增强生成）

Embedding 模型是整个知识库系统的**核心引擎**，支撑 RAG（Retrieval-Augmented Generation）流程：

```
┌─────────────── 知识库建设阶段（离线） ───────────────┐
│                                                      │
│  上传文档                                             │
│    │                                                 │
│    ▼                                                 │
│  文档分段（按段落/句子切分）                             │
│    │                                                 │
│    ▼                                                 │
│  Embedding模型 → 把每段文字变成768维向量               │
│    │                                                 │
│    ▼                                                 │
│  存入PgVector向量数据库                                │
│  ┌──────────────────────────────────┐                │
│  │ 段1: "公司年假为15天..."  → [0.23, 0.85, ...] │    │
│  │ 段2: "试用期工资打八折..." → [0.12, 0.67, ...] │    │
│  │ 段3: "报销流程如下..."    → [0.91, 0.33, ...] │    │
│  │ ...                                           │    │
│  └──────────────────────────────────┘                │
└──────────────────────────────────────────────────────┘

┌─────────────── 用户提问阶段（在线） ─────────────────┐
│                                                      │
│  用户提问: "年假有几天？"                               │
│    │                                                 │
│    ▼                                                 │
│  Embedding模型 → 把问题也变成768维向量                 │
│    "年假有几天？" → [0.21, 0.83, ...]                 │
│    │                                                 │
│    ▼                                                 │
│  在PgVector中做向量相似度搜索                           │
│  找到最相似的段: "公司年假为15天..."  (相似度: 0.92)    │
│    │                                                 │
│    ▼                                                 │
│  把检索结果注入LLM提示词:                               │
│  ┌──────────────────────────────────────┐            │
│  │ 系统提示: 你是公司助手...               │            │
│  │ 知识库内容: {data} ← 这里填入检索结果   │            │
│  │ 用户问题: 年假有几天？                  │            │
│  └──────────────────────────────────────┘            │
│    │                                                 │
│    ▼                                                 │
│  LLM生成回答: "根据公司规定，年假为15天。"              │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### 3.3 没有Embedding模型会怎样？

- **知识库功能完全不可用** — 无法对文档进行向量化，无法进行语义检索
- **智能体的RAG功能失效** — agent配置的知识库无法在对话中被引用
- **只能进行纯LLM对话** — 没有知识库支撑，LLM只能基于自身训练数据回答

---

## 四、会下载哪些文件？

### 4.1 完整文件清单

系统调用 ModelScope API 递归列出仓库中所有文件，然后**全部下载**（过滤条件 `[\s\S]*` 匹配所有文件）：

#### 根目录文件（10个）

| 文件名 | 大小 | 说明 |
|--------|------|------|
| `.gitattributes` | 1.2 KB | Git属性配置 |
| `config.json` | 856 B | 模型结构配置（层数、维度等） |
| `configuration.json` | 51 B | ModelScope框架配置 |
| `logs.txt` | 546 B | 训练日志 |
| `model.safetensors` | **~390 MB** | SafeTensors格式模型权重 |
| `modules.json` | 230 B | 模块定义 |
| `pytorch_model.bin` | **~390 MB** | PyTorch格式模型权重 |
| `README.md` | 13.7 KB | 模型说明文档 |
| `sentence_bert_config.json` | 54 B | Sentence-BERT配置 |
| `special_tokens_map.json` | 112 B | 特殊Token映射 |
| `tokenizer_config.json` | 319 B | 分词器配置 |
| `vocab.txt` | 107 KB | 词汇表（21,128个中文词） |

#### onnx/ 子目录（8个）—— **这是Java项目实际使用的文件**

| 文件名 | 大小 | 说明 |
|--------|------|------|
| `onnx/config.json` | 836 B | ONNX模型配置 |
| `onnx/model.onnx` | **~388 MB** | **ONNX标准格式模型（本项目使用）** |
| `onnx/model_O4.onnx` | **~194 MB** | ONNX O4优化格式（量化版） |
| `onnx/model_qint8_avx512_vnni.onnx` | **~98 MB** | INT8量化版（AVX512指令集优化） |
| `onnx/special_tokens_map.json` | 125 B | 特殊Token映射 |
| `onnx/tokenizer.json` | 429 KB | **分词器（本项目使用）** |
| `onnx/tokenizer_config.json` | 394 B | 分词器配置 |
| `onnx/vocab.txt` | 107 KB | 词汇表 |

#### 1_Pooling/ 子目录（1个）

| 文件名 | 大小 | 说明 |
|--------|------|------|
| `1_Pooling/config.json` | 74 B | 池化层配置（定义Mean Pooling策略） |

#### openvino/ 子目录（未详细列出）
OpenVINO格式模型文件，供Intel硬件加速使用。

### 4.2 下载总量

约 **1.3 GB**（包含多种格式的模型权重文件）。按文件大小排序从小到大下载。

### 4.3 Java项目实际只用到其中2个文件

```java
// ModelBuilder.java 第42-43行
new OnnxEmbeddingModel(
    saveDir + "/onnx/model.onnx",       // ONNX推理模型 (~388MB)
    saveDir + "/onnx/tokenizer.json",   // 分词器 (~429KB)
    PoolingMode.MEAN                     // 均值池化
);
```

其他文件（pytorch_model.bin、model.safetensors、model_O4.onnx 等）虽然下载了，但Java运行时**不会使用**。保留它们是因为：
- `pytorch_model.bin` / `model.safetensors` — Python环境使用
- `model_O4.onnx` / `model_qint8_avx512_vnni.onnx` — 优化版ONNX，可替换使用
- `config.json`、`vocab.txt` 等 — 模型元数据，调试时有用

### 4.4 下载后本地目录结构

```
{saveDir}/                                          ← sys_config: ai.model.saveDir
├── .gitattributes
├── config.json
├── configuration.json
├── logs.txt
├── model.safetensors                               ← ~390MB (Python用)
├── modules.json
├── pytorch_model.bin                               ← ~390MB (Python用)
├── README.md
├── sentence_bert_config.json
├── special_tokens_map.json
├── tokenizer_config.json
├── vocab.txt
├── 1_Pooling/
│   └── config.json
├── onnx/
│   ├── config.json
│   ├── model.onnx                                  ← ★ Java实际使用 (~388MB)
│   ├── model_O4.onnx                               ← ~194MB (优化版)
│   ├── model_qint8_avx512_vnni.onnx                ← ~98MB (INT8量化版)
│   ├── special_tokens_map.json
│   ├── tokenizer.json                              ← ★ Java实际使用 (~429KB)
│   ├── tokenizer_config.json
│   └── vocab.txt
└── openvino/
    └── ...
```

---

## 五、下载机制详解

### 5.1 下载流程

```
前端: 点击"下载默认Embedding模型"
  │
  ▼
GET /ai/model/download-default-embedding
  │
  ├─ 1. 检查是否已有LOCAL类型模型（防止重复下载）
  │     Model req = new Model();
  │     req.setProvider(ModelProvider.LOCAL.getValue());
  │     if (!modelService.selectModelList(req).isEmpty())
  │         throw new ServiceException("已经下载过本地embedding模型");
  │
  ├─ 2. 从sys_config获取保存目录
  │     String saveDir = sysConfigService.selectConfigByKey("ai.model.saveDir");
  │
  ├─ 3. 获取当前用户WebSocket会话（用于异步通知）
  │     String token = tokenService.getToken(request);
  │
  ├─ 4. 调用ModelScopeUtil多线程下载
  │     modelScopeUtil.downloadMultiThread(repoId, saveDir, "[\\s\\S]*", callback);
  │     │
  │     │  4a. 调用ModelScope API递归列出所有文件
  │     │      GET https://modelscope.cn/api/v1/models/{repoId}/repo/files
  │     │
  │     │  4b. 过滤所有blob类型文件
  │     │
  │     │  4c. 按文件大小升序排列（小文件先下）
  │     │
  │     │  4d. 创建线程池（core=CPU核数, max=10）
  │     │
  │     │  4e. 并发下载每个文件
  │     │      - 支持断点续传（检查本地文件大小 + Range请求头）
  │     │      - 自动创建目录结构
  │     │      - CountDownLatch等待所有文件下载完成
  │     │
  │     └─ 5. 下载完成回调
  │            ├── 创建Model数据库记录
  │            │     name = "zjwan461/shibing624_text2vec-base-chinese"
  │            │     type = 1 (EMBEDDING)
  │            │     provider = "local"
  │            │     baseUrl = "#"
  │            │     saveDir = 实际保存路径
  │            │
  │            ├── 设置为默认Embedding模型
  │            │     sys_config: ai.model.embedding = 模型ID
  │            │
  │            └── WebSocket推送通知
  │                  "embedding模型已下载,保存位置：{path}"
  │
  └─ 立即返回（后台继续下载）
       return success(saveDir);
```

### 5.2 下载特性

| 特性 | 说明 |
|------|------|
| **多线程并发** | 线程池 core=CPU核数, max=10，所有文件同时下载 |
| **断点续传** | 检查本地文件大小，使用 HTTP Range 头续传 |
| **异步执行** | 下载在后台线程中执行，不阻塞API响应 |
| **WebSocket通知** | 下载完成后通过WebSocket推送结果到前端 |
| **防重复下载** | 启动前检查数据库是否已有 LOCAL 类型模型 |
| **自动创建目录** | 自动创建所需的子目录结构 |

---

## 六、关于 text2vec-base-chinese 模型

### 6.1 基本信息

| 项目 | 说明 |
|------|------|
| **模型名称** | shibing624/text2vec-base-chinese |
| **原始来源** | HuggingFace → 移植到 ModelScope |
| **作者** | shibing624 (许明) |
| **基础架构** | BERT-base-chinese (12层Transformer) |
| **训练语料** | 中文自然语言推理数据集（STS、NLI等） |
| **输出维度** | 768维向量 |
| **模型大小** | ~390MB (ONNX格式 ~388MB) |
| **适用场景** | 中文文本相似度计算、语义搜索、文本聚类 |
| **License** | Apache-2.0 |

### 6.2 为什么选择这个模型？

1. **中文专用** — 词汇表和训练数据都针对中文优化，比多语言模型效果好
2. **轻量级** — 只有102M参数，CPU即可运行，不需要GPU
3. **ONNX支持** — 提供 ONNX 格式导出，Java可通过 ONNX Runtime 直接加载
4. **维度适中** — 768维兼顾了精度和性能，PgVector索引效率较高
5. **开源免费** — Apache-2.0许可证，商用无忧

### 6.3 性能参考

在中文STS（语义文本相似度）基准测试上的表现：

| 模型 | 平均Spearman相关系数 |
|------|---------------------|
| text2vec-base-chinese | ~67% |
| text2vec-large-chinese | ~71% |
| multilingual-e5-base | ~65% |

在CPU上的推理速度：
- 单条文本向量化：约 20-50ms
- 批量（32条）向量化：约 200-500ms

---

## 七、也可以使用其他 Embedding 模型

本系统不限于本地ONNX模型，还支持：

### 7.1 Ollama Embedding

在模型管理页面添加：
- **提供商：** Ollama
- **类型：** Embedding
- **Base URL：** `http://localhost:11434`
- **模型名称：** `nomic-embed-text`、`mxbai-embed-large` 等

需要本地运行 Ollama 服务。

### 7.2 OpenAI 兼容 Embedding

在模型管理页面添加：
- **提供商：** Open AI
- **类型：** Embedding
- **Base URL：** `https://api.openai.com/v1` 或兼容端点
- **API Key：** 你的密钥
- **模型名称：** `text-embedding-3-small`、`text-embedding-3-large` 等

**注意：** 切换Embedding模型后，已有知识库需要重新向量化（`POST /ai/knowledgeBase/re-embedding/{kbId}`），因为不同模型的向量维度和语义空间不同，旧向量无法与新向量比较。
