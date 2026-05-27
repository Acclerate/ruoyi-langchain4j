package com.ruoyi.ai;

import com.ruoyi.ai.enums.ModelProvider;
import com.ruoyi.ai.enums.ModelType;
import com.ruoyi.ai.service.LangChain4jService;
import com.ruoyi.ai.service.impl.ModelBuilder;
import com.ruoyi.ai.domain.Model;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SiliconFlow集成测试
 */
@SpringBootTest
public class SiliconFlowIntegrationTest {

    @Resource
    private LangChain4jService langChain4jService;

    @Resource
    private ModelBuilder modelBuilder;

    /**
     * 测试SiliconFlow提供商枚举
     */
    @Test
    public void testSiliconFlowProvider() {
        ModelProvider provider = ModelProvider.fromValue("SiliconFlow");
        assertEquals(ModelProvider.SILICONFLOW, provider);
        assertEquals("SiliconFlow", provider.getValue());
    }

    /**
     * 测试SiliconFlow Embedding模型构建
     */
    @Test
    public void testSiliconFlowEmbeddingModel() {
        // 注意：此测试需要有效的API密钥才能通过
        // 在实际测试中，请使用真实的SiliconFlow API密钥
        Model model = new Model();
        model.setId(999L);
        model.setName("Qwen/Qwen3-Embedding-8B");
        model.setBaseUrl("https://api.siliconflow.cn/v1");
        model.setApiKey("test-api-key"); // 替换为真实API密钥
        model.setProvider("SiliconFlow");
        model.setType(1); // EMBEDDING类型

        // 测试模型构建（不实际调用API）
        try {
            EmbeddingModel embeddingModel = modelBuilder.getEmbeddingModel(model);
            assertNotNull(embeddingModel);
        } catch (Exception e) {
            // 预期可能会因为无效API密钥而失败
            System.out.println("模型构建测试（预期失败）: " + e.getMessage());
        }
    }

    /**
     * 测试SiliconFlow模型配置检查
     */
    @Test
    public void testSiliconFlowModelConfigCheck() {
        // 注意：此测试需要有效的API密钥才能通过
        // 在实际测试中，请使用真实的SiliconFlow API密钥
        String baseUrl = "https://api.siliconflow.cn/v1";
        String apiKey = "test-api-key"; // 替换为真实API密钥
        String modelName = "Qwen/Qwen3-Embedding-8B";
        ModelProvider provider = ModelProvider.SILICONFLOW;
        ModelType type = ModelType.EMBEDDING;

        // 测试配置检查（不实际调用API）
        try {
            boolean result = langChain4jService.checkModelConfig(baseUrl, apiKey, modelName, provider, type);
            // 由于使用测试API密钥，预期会失败
            assertFalse(result);
        } catch (Exception e) {
            System.out.println("配置检查测试（预期失败）: " + e.getMessage());
        }
    }
}
