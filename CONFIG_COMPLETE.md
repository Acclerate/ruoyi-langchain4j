# SiliconFlow 配置完成报告

## 配置时间
2026-05-27

## 一、数据库配置

### 1. SiliconFlow 提供商 ✅
已添加到 `sys_dict_data` 表：
- dict_label: SiliconFlow
- dict_value: SiliconFlow
- dict_type: ai_model_provider
- list_class: warning

### 2. SiliconFlow 模型 ✅

| ID | 模型名称 | 类型 | 提供商 | Base URL |
|----|----------|------|--------|----------|
| 3 | Qwen/Qwen3-Embedding-8B | EMBEDDING(1) | SiliconFlow | https://api.siliconflow.cn/v1 |
| 4 | deepseek-ai/DeepSeek-V4-Pro | LLM(0) | SiliconFlow | https://api.siliconflow.cn/v1 |
| 5 | deepseek-ai/DeepSeek-V4-Flash | LLM(0) | SiliconFlow | https://api.siliconflow.cn/v1 |

### 3. 默认 Embedding 模型 ✅
- config_key: ai.model.embedding
- config_value: 3 (Qwen/Qwen3-Embedding-8B)

## 二、向量数据库配置

### PgVector 向量表 ✅
- 数据库: embedding
- 表名: embedding
- 向量维度: 4096 (已从768修改)
- 已清空旧的768维数据

```
Table "public.embedding"
    Column    |     Type     
--------------+--------------
 embedding_id | uuid         
 embedding    | vector(4096) 
 text         | text         
 metadata     | json         
```

## 三、配置文件修改

### application.yml ✅
```yaml
ai:
  pgVector:
    host: 127.0.0.1
    port: 5432
    database: embedding
    user: root
    password: root
    table: embedding
    dimension: 4096  # 已从768修改为4096
```

## 四、使用说明

### 1. 重启后端服务
```bash
cd ruoyi-admin
java -jar target/ruoyi-admin.jar
```

### 2. 启动前端服务
```bash
cd ruoyi-ui
npm run dev
```

### 3. 登录系统
- 地址: http://localhost:8282
- 账号: admin / admin123

### 4. 验证配置
1. 进入 **AI管理** → **模型管理**
2. 确认三个 SiliconFlow 模型已显示
3. 点击 **向量模型设置**，确认默认 Embedding 模型为 Qwen/Qwen3-Embedding-8B

### 5. 创建知识库
1. 进入 **AI管理** → **知识库管理**
2. 创建新知识库
3. 上传文档，选择 SiliconFlow 向量模型
4. 配置分段参数：
   - 最大分段数: 500-800
   - 最大重叠数: 50-80

### 6. 创建智能体
1. 进入 **AI管理** → **智能体管理**
2. 创建新智能体
3. 选择 LLM 模型: deepseek-ai/DeepSeek-V4-Pro 或 deepseek-ai/DeepSeek-V4-Flash
4. 关联知识库
5. 配置提示词模板：
```
基于以下参考资料回答用户问题。如果参考资料中没有相关信息，请明确说明。

参考资料：
{data}

用户问题：{question}

请提供准确、详细的回答：
```

## 五、注意事项

### 1. 维度匹配
Qwen/Qwen3-Embedding-8B 输出维度为 4096，已同步修改：
- application.yml 中的 ai.pgVector.dimension: 4096
- PgVector 向量表的 embedding 列: vector(4096)

### 2. 旧数据已清空
由于维度变更，原有的 195 条 768 维向量数据已清空，需要重新向量化知识库。

### 3. API 密钥安全
请妥善保管 SiliconFlow API 密钥，不要泄露给他人。

### 4. API 调用限制
SiliconFlow 可能有调用频率限制，请根据实际需求选择合适的套餐。

## 六、支持的 SiliconFlow 模型

### Embedding 模型
| 模型名称 | 维度 | 说明 |
|----------|------|------|
| Qwen/Qwen3-Embedding-8B | 4096 | 高质量中文 Embedding（已配置） |
| BAAI/bge-large-zh-v1.5 | 1024 | 高质量中文 Embedding |

### LLM 模型
| 模型名称 | 说明 |
|----------|------|
| deepseek-ai/DeepSeek-V4-Pro | DeepSeek V4 Pro（已配置） |
| deepseek-ai/DeepSeek-V4-Flash | DeepSeek V4 Flash（已配置） |
| Qwen/Qwen3-8B | 通义千问 8B |
| Qwen/Qwen3-14B | 通义千问 14B |

## 七、故障排查

### 1. 模型配置检查失败
- 检查 API 密钥是否正确
- 检查 Base URL 是否为 https://api.siliconflow.cn/v1
- 检查网络连接是否正常

### 2. 向量化失败
- 检查 Embedding 模型是否已正确配置
- 检查文档格式是否支持
- 检查分段参数是否合理

### 3. 维度不匹配
- 确认 application.yml 中 ai.pgVector.dimension 为 4096
- 确认 PgVector 向量表 embedding 列为 vector(4096)
- 重启后端服务使配置生效

## 八、配置验证清单

- [x] SiliconFlow 提供商已添加到字典
- [x] Qwen/Qwen3-Embedding-8B 模型已添加
- [x] deepseek-ai/DeepSeek-V4-Pro 模型已添加
- [x] deepseek-ai/DeepSeek-V4-Flash 模型已添加
- [x] 默认 Embedding 模型已设置为 Qwen/Qwen3-Embedding-8B
- [x] application.yml 向量维度已修改为 4096
- [x] PgVector 向量表维度已修改为 4096
- [x] 旧的 768 维数据已清空

## 九、下一步操作

1. 重启后端服务
2. 登录系统验证配置
3. 创建知识库并上传文档
4. 测试向量化功能
5. 创建智能体并关联知识库
6. 测试对话功能

---

**配置状态**: ✅ 完成  
**配置时间**: 2026-05-27  
**配置人员**: AI Assistant
