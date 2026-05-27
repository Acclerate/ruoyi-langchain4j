# SiliconFlow 集成指南

## 概述

本项目已集成SiliconFlow（硅基流动）支持，可以使用SiliconFlow提供的各种AI模型，包括Qwen/Qwen3-Embedding-8B等高质量Embedding模型。

## 配置步骤

### 1. 获取SiliconFlow API密钥

1. 访问 [SiliconFlow官网](https://siliconflow.cn/)
2. 注册账号并登录
3. 在控制台获取API密钥

### 2. 添加SiliconFlow提供商到数据库

执行以下SQL脚本：

```sql
-- 添加SiliconFlow提供商到字典数据
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default,
                           status, create_by, create_time, update_by, update_time, remark)
VALUES (0, 'SiliconFlow', 'SiliconFlow', 'ai_model_provider', NULL, 'warning', 'N', '0', 'admin', NOW(), '', NULL, NULL);
```

### 3. 在系统中配置SiliconFlow模型

1. 登录系统后台
2. 进入 **AI管理** → **模型管理**
3. 点击 **新增** 按钮
4. 填写模型信息：

#### Qwen/Qwen3-Embedding-8B 配置示例

| 配置项 | 值 |
|--------|-----|
| **类型** | EMBEDDING |
| **提供商** | SiliconFlow |
| **模型名称** | Qwen/Qwen3-Embedding-8B |
| **Base URL** | https://api.siliconflow.cn/v1 |
| **API Key** | 您的SiliconFlow API密钥 |
| **Temperature** | 0.7（默认值） |
| **Token最大生成数** | 2048（默认值） |

#### 其他可用模型

SiliconFlow提供多种模型，包括：

**Embedding模型：**
- `Qwen/Qwen3-Embedding-8B` - 高质量中文Embedding模型
- `BAAI/bge-large-zh-v1.5` - 高质量中文Embedding模型
- `text2vec-base-chinese` - 基础中文Embedding模型

**LLM模型：**
- `Qwen/Qwen3-8B` - 通义千问8B模型
- `Qwen/Qwen3-14B` - 通义千问14B模型
- `Qwen/Qwen3-32B` - 通义千问32B模型
- `deepseek-ai/DeepSeek-V3` - DeepSeek V3模型

### 4. 设置默认Embedding模型

1. 在模型管理页面，点击 **向量模型设置** 按钮
2. 在 **默认Embedding模型** 下拉框中选择刚添加的SiliconFlow Embedding模型
3. 点击 **确定** 保存设置

## 使用示例

### 知识库配置

1. 进入 **AI管理** → **知识库管理**
2. 创建新知识库或选择现有知识库
3. 上传文档并配置分段参数：
   - **最大分段数**：建议500-800字符
   - **最大重叠数**：建议50-80字符
4. 选择 **向量模型** 为SiliconFlow的Embedding模型
5. 点击 **提交** 开始向量化

### 智能体配置

1. 进入 **AI管理** → **智能体管理**
2. 创建新智能体或编辑现有智能体
3. 配置智能体参数：
   - **模型ID**：选择支持的LLM模型
   - **知识库ID**：关联已向量化的知识库
   - **提示词模板**：包含 `{data}` 和 `{question}` 占位符
   - **最小相似度得分**：建议0.3-0.7
   - **最大返回条数**：建议3-10条

### 示例提示词模板

```
基于以下参考资料回答用户问题。如果参考资料中没有相关信息，请明确说明。

参考资料：
{data}

用户问题：{question}

请提供准确、详细的回答：
```

## API格式说明

SiliconFlow的API兼容OpenAI格式，因此：

- **Base URL**：`https://api.siliconflow.cn/v1`
- **认证方式**：Bearer Token（与OpenAI相同）
- **请求格式**：与OpenAI API完全兼容

## 注意事项

1. **API密钥安全**：请妥善保管您的API密钥，不要泄露给他人
2. **调用限制**：SiliconFlow可能有调用频率限制，请根据实际需求选择合适的套餐
3. **模型选择**：根据实际需求选择合适的模型，不同模型的性能和价格不同
4. **维度匹配**：确保Embedding模型的维度与PgVector数据库配置的维度一致（默认768维）

## 故障排查

### 模型配置检查失败

1. 检查API密钥是否正确
2. 检查Base URL是否正确
3. 检查模型名称是否正确
4. 检查网络连接是否正常

### 向量化失败

1. 检查Embedding模型是否已正确配置
2. 检查文档格式是否支持
3. 检查分段参数是否合理

### 检索命中率低

1. 调整 **最小相似度得分**（降低值会增加召回率）
2. 调整 **最大返回条数**（增加值会提供更多上下文）
3. 优化文档分段策略（调整分段大小和重叠）
4. 检查Embedding模型质量

## 性能优化建议

1. **分段策略优化**：
   - 短文档（<1000字符）：`maxSegmentSize=300, maxOverlapSize=30`
   - 中等文档（1000-5000字符）：`maxSegmentSize=500, maxOverlapSize=50`
   - 长文档（>5000字符）：`maxSegmentSize=800, maxOverlapSize=80`

2. **检索参数优化**：
   - 精确问答：`minScore=0.7, maxResult=3`
   - 一般问答：`minScore=0.5, maxResult=5`
   - 探索性问答：`minScore=0.3, maxResult=10`

3. **批量处理**：
   - 调整 `ai.embedding.batchSize` 配置（默认100）
   - 根据服务器性能和网络情况调整批次大小

## 技术支持

如有问题，请参考：
- [SiliconFlow官方文档](https://docs.siliconflow.cn/)
- [LangChain4j官方文档](https://docs.langchain4j.dev/)
- 项目GitHub Issues
