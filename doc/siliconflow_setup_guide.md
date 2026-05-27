# SiliconFlow 模型配置指南

## 数据库脚本已执行 ✅

SiliconFlow 提供商已成功添加到 `sys_dict_data` 表。

## 配置 SiliconFlow 模型

### 方式一：通过系统界面配置（推荐）

1. **启动系统后端服务**
   ```bash
   cd ruoyi-admin
   java -jar target/ruoyi-admin.jar
   ```

2. **启动前端服务**
   ```bash
   cd ruoyi-ui
   npm install
   npm run dev
   ```

3. **登录系统**
   - 访问 http://localhost:8282
   - 使用管理员账号：admin / admin123

4. **添加 SiliconFlow Embedding 模型**
   - 进入 **AI管理** → **模型管理**
   - 点击 **新增** 按钮
   - 填写以下信息：

   | 配置项 | 值 |
   |--------|-----|
   | **类型** | EMBEDDING |
   | **提供商** | SiliconFlow |
   | **模型名称** | Qwen/Qwen3-Embedding-8B |
   | **Base URL** | https://api.siliconflow.cn/v1 |
   | **API Key** | 您的 SiliconFlow API 密钥 |
   | **Temperature** | 0.7 |
   | **Token最大生成数** | 2048 |

5. **设置为默认 Embedding 模型**
   - 在模型管理页面，点击 **向量模型设置** 按钮
   - 在 **默认Embedding模型** 下拉框中选择刚添加的 SiliconFlow 模型
   - 点击 **确定** 保存

### 方式二：通过 SQL 直接配置

如果您有真实的 SiliconFlow API 密钥，可以直接执行 SQL：

```sql
-- 插入 SiliconFlow Embedding 模型
INSERT INTO `ry-vue`.model (name, base_url, api_key, temperature, max_output_token, type, provider, create_by, create_time)
VALUES (
    'Qwen/Qwen3-Embedding-8B',           -- 模型名称
    'https://api.siliconflow.cn/v1',      -- Base URL
    'your-siliconflow-api-key',           -- API Key (替换为您的真实API密钥)
    0.7,                                  -- temperature
    2048,                                 -- max_output_token
    1,                                    -- type: 1=EMBEDDING
    'SiliconFlow',                        -- provider
    'admin',                              -- create_by
    NOW()                                 -- create_time
);

-- 获取刚插入的模型ID
SET @model_id = LAST_INSERT_ID();

-- 设置为默认 Embedding 模型
UPDATE `ry-vue`.sys_config SET config_value = @model_id WHERE config_key = 'ai.model.embedding';
```

## 获取 SiliconFlow API 密钥

1. 访问 [SiliconFlow 官网](https://siliconflow.cn/)
2. 注册账号并登录
3. 在控制台获取 API 密钥
4. 复制 API 密钥用于配置

## 支持的模型列表

### Embedding 模型（推荐用于知识库）

| 模型名称 | 说明 | 维度 |
|----------|------|------|
| `Qwen/Qwen3-Embedding-8B` | 高质量中文 Embedding（推荐） | 4096 |
| `BAAI/bge-large-zh-v1.5` | 高质量中文 Embedding | 1024 |
| `text2vec-base-chinese` | 基础中文 Embedding | 768 |

### LLM 模型（推荐用于对话）

| 模型名称 | 说明 |
|----------|------|
| `Qwen/Qwen3-8B` | 通义千问 8B |
| `Qwen/Qwen3-14B` | 通义千问 14B |
| `Qwen/Qwen3-32B` | 通义千问 32B |
| `deepseek-ai/DeepSeek-V3` | DeepSeek V3 |
| `deepseek-ai/DeepSeek-R1` | DeepSeek R1 |

## 注意事项

1. **维度匹配**：如果使用 `Qwen/Qwen3-Embedding-8B`（4096维），需要修改 `application.yml` 中的向量维度配置：
   ```yaml
   ai:
     pgVector:
       dimension: 4096  # 修改为 4096
   ```
   并重建向量表。

2. **API 密钥安全**：请妥善保管您的 API 密钥，不要泄露给他人。

3. **调用限制**：SiliconFlow 可能有调用频率限制，请根据实际需求选择合适的套餐。

## 验证配置

配置完成后，可以通过以下方式验证：

1. **检查模型配置**：在模型管理页面点击 **维度检查** 按钮
2. **测试知识库**：上传文档并测试向量化功能
3. **测试对话**：创建智能体并测试对话功能

## 故障排查

### 模型配置检查失败

1. 检查 API 密钥是否正确
2. 检查 Base URL 是否正确（`https://api.siliconflow.cn/v1`）
3. 检查模型名称是否正确
4. 检查网络连接是否正常

### 向量化失败

1. 检查 Embedding 模型是否已正确配置
2. 检查文档格式是否支持
3. 检查分段参数是否合理

### 维度不匹配

如果遇到维度不匹配错误，需要：
1. 修改 `application.yml` 中的 `ai.pgVector.dimension` 配置
2. 重建 PgVector 向量表
3. 重新向量化所有知识库数据
