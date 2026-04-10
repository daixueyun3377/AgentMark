package io.github.daixueyun3377.agentmark.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AgentMark 配置属性。
 */
@ConfigurationProperties(prefix = "agentmark")
public class AgentMarkProperties {

    /** 模型提供者：claude / openai（兼容通义千问、DeepSeek 等） */
    private String provider = "claude";

    /** API Key */
    private String apiKey;

    /** 模型名称 */
    private String model = "claude-sonnet-4-20250514";

    /** API 基础地址（可选，用于自定义端点） */
    private String baseUrl = "https://api.anthropic.com/";

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
