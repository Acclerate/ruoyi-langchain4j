# Changelog

## v1.2.0 (2026-05-27)

### 新增功能

#### SiliconFlow（硅基流动）集成

- **新增 SiliconFlow 模型提供商支持**：可使用 Qwen/Qwen3-Embedding-8B 等高质量第三方 Embedding 模型
- **扩展模型提供商架构**：支持 OpenAI 兼容格式的第三方模型服务
- **新增 SiliconFlow 集成指南**：详见 [doc/siliconflow_integration.md](./doc/siliconflow_integration.md)
- **新增配置示例**：详见 [doc/siliconflow_config_example.yml](./doc/siliconflow_config_example.yml)

### 修改文件

#### 后端修改

1. **`ruoyi-ai/src/main/java/com/ruoyi/ai/enums/ModelProvider.java`**
   - 新增 `SILICONFLOW("SiliconFlow")` 枚举值

2. **`ruoyi-ai/src/main/java/com/ruoyi/ai/service/impl/ModelBuilder.java`**
   - 修改 `buildEmbeddingModel()` 方法：添加对 SiliconFlow 的支持
   - 修改 `buildStreamingLLM()` 方法：添加对 SiliconFlow 的支持
   - 修改 `buildBlockingLLM()` 方法：添加对 SiliconFlow 的支持
   - 修改 `getParameters()` 方法：添加对 SiliconFlow 的支持

3. **`ruoyi-ai/src/main/java/com/ruoyi/ai/service/impl/LangChain4jServiceImpl.java`**
   - 修改 `checkModelConfig()` 方法：添加对 SiliconFlow 的支持

#### 数据库修改

4. **`sql/add_siliconflow_provider.sql`**
   - 新增 SQL 脚本：添加 SiliconFlow 提供商到字典数据

#### 文档修改

5. **`doc/siliconflow_integration.md`**
   - 新增 SiliconFlow 集成指南

6. **`doc/siliconflow_config_example.yml`**
   - 新增 SiliconFlow 配置示例

7. **`README.md`**
   - 更新最新更新部分：添加 v1.2 版本说明
   - 更新技术栈说明：添加 SiliconFlow 支持

8. **`CLAUDE.md`**
   - 更新模型体系说明：添加 SiliconFlow 提供商
   - 更新数据库表说明：添加 SiliconFlow 提供商

#### 测试文件

9. **`ruoyi-ai/src/test/java/com/ruoyi/ai/SiliconFlowIntegrationTest.java`**
   - 新增 SiliconFlow 集成测试类

### 技术实现细节

#### SiliconFlow API 兼容性

SiliconFlow 的 API 完全兼容 OpenAI 格式，因此：

- **Base URL**：`https://api.siliconflow.cn/v1`
- **认证方式**：Bearer Token（与 OpenAI 相同）
- **请求格式**：与 OpenAI API 完全兼容

#### 代码实现

在 `ModelBuilder` 中，SiliconFlow 使用与 OpenAI 相同的类进行处理：

```java
// Embedding 模型
if (provider == ModelProvider.OPEN_AI || provider == ModelProvider.SILICONFLOW) {
    return OpenAiEmbeddingModel.builder()
            .baseUrl(model.getBaseUrl())
            .modelName(model.getName())
            .apiKey(model.getApiKey())
            .build();
}

// Streaming LLM
if (provider == ModelProvider.OPEN_AI || provider == ModelProvider.SILICONFLOW) {
    return OpenAiStreamingChatModel.builder()
            .baseUrl(model.getBaseUrl())
            .modelName(model.getName())
            .returnThinking(true)
            .apiKey(model.getApiKey())
            .build();
}
```

### 使用说明

#### 1. 添加 SiliconFlow 提供商到数据库

执行以下 SQL 脚本：

```sql
-- 添加 SiliconFlow 提供商到字典数据
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default,
                           status, create_by, create_time, update_by, update_time, remark)
VALUES (0, 'SiliconFlow', 'SiliconFlow', 'ai_model_provider', NULL, 'warning', 'N', '0', 'admin', NOW(), '', NULL, NULL);
```

#### 2. 在系统中配置 SiliconFlow 模型

1. 登录系统后台
2. 进入 **AI管理** → **模型管理**
3. 点击 **新增** 按钮
4. 填写模型信息：
   - **类型**：EMBEDDING 或 LLM
   - **提供商**：SiliconFlow
   - **模型名称**：Qwen/Qwen3-Embedding-8B（或其他模型）
   - **Base URL**：`https://api.siliconflow.cn/v1`
   - **API Key**：您的 SiliconFlow API 密钥
   - **Temperature**：0.7（默认值）
   - **Token最大生成数**：2048（默认值）
5. 点击 **确定** 保存

#### 3. 设置默认 Embedding 模型

1. 在模型管理页面，点击 **向量模型设置** 按钮
2. 在 **默认Embedding模型** 下拉框中选择刚添加的 SiliconFlow Embedding 模型
3. 点击 **确定** 保存设置

### 支持的 SiliconFlow 模型

#### Embedding 模型

- `Qwen/Qwen3-Embedding-8B` - 高质量中文 Embedding 模型（推荐）
- `BAAI/bge-large-zh-v1.5` - 高质量中文 Embedding 模型
- `text2vec-base-chinese` - 基础中文 Embedding 模型

#### LLM 模型

- `Qwen/Qwen3-8B` - 通义千问 8B 模型
- `Qwen/Qwen3-14B` - 通义千问 14B 模型
- `Qwen/Qwen3-32B` - 通义千问 32B 模型
- `deepseek-ai/DeepSeek-V3` - DeepSeek V3 模型
- `deepseek-ai/DeepSeek-R1` - DeepSeek R1 模型

### 注意事项

1. **API 密钥安全**：请妥善保管您的 API 密钥，不要泄露给他人
2. **调用限制**：SiliconFlow 可能有调用频率限制，请根据实际需求选择合适的套餐
3. **模型选择**：根据实际需求选择合适的模型，不同模型的性能和价格不同
4. **维度匹配**：确保 Embedding 模型的维度与 PgVector 数据库配置的维度一致（默认 768 维）

### 测试验证

运行集成测试验证 SiliconFlow 集成：

```bash
cd ruoyi-ai
mvn test -Dtest=SiliconFlowIntegrationTest
```

---

## v1.1.0

### 升级

1. 升级 LangChain4j 至 1.13.0（通过 BOM 统一管理依赖版本，不再维护双版本策略）
2. 升级 Lombok 至 1.18.36，修复 JDK 21 兼容性问题

---

## v1.0.0

### 新增功能

1. 新增 AI 模型管理（包含大语言模型 `LLM` 和文档向量模型 `Embedding`）
   - 支持 `ollama` 和兼容 `OpenAI` 格式的模型提供商
   - 支持本地运行向量模型（`shibing624_text2vec-base-chinese`）
   - 支持在线下载本地向量模型功能

2. 新增本地知识库功能
   - 支持文档分段和手动调整分段内容功能

3. 新增智能体管理
   - 智能体支持多知识库
   - 系统提示词
   - 用户提示词模板功能
   - 记忆功能
   - 客户端限流功能

4. 新增 AI 聊天功能
   - 支持深度思考 UI 渲染
   - 支持删除聊天历史功能

5. 新增推送服务功能
   - 支持异步下载本地向量模型结果推送
   - 支持异步文档分段向量化处理结果推送

6. 新增向量模型维度检测功能

7. 新增知识库重向量功能
