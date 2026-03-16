package io.agentmark.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AgentMark 配置属性。
 */
@ConfigurationProperties(prefix = "agentmark")
public class AgentMarkProperties {

    /** 模型提供者：openai / claude / dashscope */
    private String provider = "openai";

    /** API Key */
    private String apiKey;

    /** 模型名称 */
    private String model = "gpt-4o";

    /** API 基础地址（可选，用于自定义端点） */
    private String baseUrl = "https://api.openai.com/v1/";

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
