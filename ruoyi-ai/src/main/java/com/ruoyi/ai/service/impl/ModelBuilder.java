package com.ruoyi.ai.service.impl;

import com.ruoyi.ai.domain.AiAgent;
import com.ruoyi.ai.domain.Model;
import com.ruoyi.ai.enums.ModelProvider;
import com.ruoyi.ai.service.IModelService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.spring.SpringUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModelBuilder {

    /** 缓存 key = modelId + "#" + configHash（配置不变则复用实例） */
    private final ConcurrentHashMap<String, StreamingChatModel> streamingChatCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ChatModel> blockingChatCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EmbeddingModel> embeddingCache = new ConcurrentHashMap<>();

    /**
     * 根据模型核心配置生成指纹，配置不变时可命中缓存
     */
    private String configKey(Long modelId, Model model) {
        return modelId + "#" + model.getProvider() + "#" + model.getBaseUrl()
                + "#" + model.getName() + "#" + model.getApiKey();
    }

    /**
     * 清除指定模型ID的所有缓存实例（配置变更/删除时调用）
     */
    public void evict(Long modelId) {
        streamingChatCache.keySet().removeIf(k -> k.startsWith(modelId + "#"));
        blockingChatCache.keySet().removeIf(k -> k.startsWith(modelId + "#"));
        embeddingCache.keySet().removeIf(k -> k.startsWith(modelId + "#"));
    }

    public EmbeddingModel getEmbeddingModel(Model model) {
        String key = configKey(model.getId(), model);
        return embeddingCache.computeIfAbsent(key, k -> buildEmbeddingModel(model));
    }

    private EmbeddingModel buildEmbeddingModel(Model model) {
        ModelProvider provider = ModelProvider.fromValue(model.getProvider());
        if (provider == ModelProvider.OLLAMA) {
            return OllamaEmbeddingModel.builder()
                    .baseUrl(model.getBaseUrl())
                    .modelName(model.getName())
                    .build();
        } else if (provider == ModelProvider.OPEN_AI) {
            return OpenAiEmbeddingModel.builder()
                    .baseUrl(model.getBaseUrl())
                    .modelName(model.getName())
                    .build();
        } else if (provider == ModelProvider.LOCAL) {
            String saveDir = model.getSaveDir();
            return new OnnxEmbeddingModel(saveDir + "/onnx/model.onnx", saveDir + "/onnx/tokenizer.json", PoolingMode.MEAN);
        } else {
            throw new ServiceException("不支持的模型提供商");
        }
    }

    public StreamingChatModel getStreamingLLM(Model model) {
        String key = configKey(model.getId(), model);
        return streamingChatCache.computeIfAbsent(key, k -> buildStreamingLLM(model));
    }

    private StreamingChatModel buildStreamingLLM(Model model) {
        ModelProvider provider = ModelProvider.fromValue(model.getProvider());
        if (provider == ModelProvider.OLLAMA) {
            return OllamaStreamingChatModel.builder()
                    .baseUrl(model.getBaseUrl())
                    .modelName(model.getName())
                    .think(true)
                    .returnThinking(true)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        } else {
            return OpenAiStreamingChatModel.builder()
                    .baseUrl(model.getBaseUrl())
                    .modelName(model.getName())
                    .returnThinking(true)
                    .apiKey(model.getApiKey())
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        }
    }

    public ChatModel getBlockingLLM(Model model) {
        String key = configKey(model.getId(), model);
        return blockingChatCache.computeIfAbsent(key, k -> buildBlockingLLM(model));
    }

    private ChatModel buildBlockingLLM(Model model) {
        ModelProvider provider = ModelProvider.fromValue(model.getProvider());
        if (provider == ModelProvider.OLLAMA) {
            return OllamaChatModel.builder()
                    .baseUrl(model.getBaseUrl())
                    .modelName(model.getName())
                    .build();
        } else {
            return OpenAiChatModel.builder()
                    .baseUrl(model.getBaseUrl())
                    .modelName(model.getName())
                    .apiKey(model.getApiKey())
                    .build();
        }
    }

    public ChatRequestParameters getParameters(Model model) {
        ModelProvider provider = ModelProvider.fromValue(model.getProvider());
        ChatRequestParameters parameters = null;
        if (provider == ModelProvider.OLLAMA) {
            parameters = OllamaChatRequestParameters.builder()
                    .modelName(model.getName())
                    .temperature(model.getTemperature())
                    .maxOutputTokens(model.getMaxOutputToken())
                    .build();
        } else {
            parameters = OpenAiChatRequestParameters.builder()
                    .modelName(model.getName())
                    .temperature(model.getTemperature())
                    .maxOutputTokens(model.getMaxOutputToken())
                    .build();
        }

        return parameters;
    }

    public ChatRequestParameters getParameters(AiAgent aiAgent) {
        Model model = SpringUtils.getBean(IModelService.class).selectModelById(aiAgent.getModelId());
        ModelProvider provider = ModelProvider.fromValue(model.getProvider());
        ChatRequestParameters parameters = null;
        if (provider == ModelProvider.OLLAMA) {
            parameters = OllamaChatRequestParameters.builder()
                    .modelName(model.getName())
                    .temperature(aiAgent.getTemperature())
                    .maxOutputTokens(aiAgent.getMaxOutputToken())
                    .build();
        } else {
            parameters = OpenAiChatRequestParameters.builder()
                    .modelName(model.getName())
                    .temperature(aiAgent.getTemperature())
                    .maxOutputTokens(aiAgent.getMaxOutputToken())
                    .build();
        }

        return parameters;
    }
}
