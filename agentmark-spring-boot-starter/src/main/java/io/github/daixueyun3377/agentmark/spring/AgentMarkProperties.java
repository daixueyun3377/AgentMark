package io.github.daixueyun3377.agentmark.spring;

import io.github.daixueyun3377.agentmark.core.agent.AgentMarkSessionManager;
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

    /**
     * 默认 Agent 的 system prompt 文件路径（classpath 相对路径）。
     * 应用工程在 {@code src/main/resources/agentmark/system-prompt.md} 放置内容。
     */
    private String systemPromptPath = SystemPromptLoader.DEFAULT_SYSTEM_PROMPT_PATH;

    /** 单次对话最大工具调用轮数（默认 10） */
    private int maxToolRounds = 10;

    /** 调用追踪配置 */
    private Trace trace = new Trace();

    /** 会话管理配置 */
    private Session session = new Session();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getSystemPromptPath() { return systemPromptPath; }
    public void setSystemPromptPath(String systemPromptPath) { this.systemPromptPath = systemPromptPath; }

    public int getMaxToolRounds() { return maxToolRounds; }
    public void setMaxToolRounds(int maxToolRounds) { this.maxToolRounds = maxToolRounds; }

    public Trace getTrace() { return trace; }
    public void setTrace(Trace trace) { this.trace = trace; }

    public Session getSession() { return session; }
    public void setSession(Session session) { this.session = session; }

    /**
     * 调用追踪配置。
     */
    public static class Trace {

        /** 是否开启调用追踪（默认关闭） */
        private boolean enabled = false;

        /** 追踪文件存储路径 */
        private String path;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }

    /**
     * 会话管理配置。
     */
    public static class Session {

        /** 是否自动创建 AgentMarkSessionManager Bean（默认开启） */
        private boolean enabled = true;

        /** 会话空闲过期时间，单位毫秒；小于等于 0 表示不过期 */
        private long ttlMillis = AgentMarkSessionManager.DEFAULT_TTL_MILLIS;

        /** 最大内存会话数 */
        private int maxSessions = AgentMarkSessionManager.DEFAULT_MAX_SESSIONS;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public long getTtlMillis() { return ttlMillis; }
        public void setTtlMillis(long ttlMillis) { this.ttlMillis = ttlMillis; }

        public int getMaxSessions() { return maxSessions; }
        public void setMaxSessions(int maxSessions) { this.maxSessions = maxSessions; }
    }
}
