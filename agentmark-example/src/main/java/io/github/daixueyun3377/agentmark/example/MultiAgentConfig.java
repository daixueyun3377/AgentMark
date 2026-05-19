package io.github.daixueyun3377.agentmark.example;

import io.github.daixueyun3377.agentmark.core.agent.AgentMarkAgent;
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider;
import io.github.daixueyun3377.agentmark.core.registry.ToolRegistry;
import io.github.daixueyun3377.agentmark.spring.AgentMarkProperties;
import io.github.daixueyun3377.agentmark.spring.SystemPromptLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多 Agent 示例：在 {@code agentmark/} 下为每个 Agent 配置独立的 {@code xxx-prompt.md}。
 *
 * <p>启用方式：{@code agentmark.multi-agent.enabled=true}</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "agentmark.multi-agent", name = "enabled", havingValue = "true")
public class MultiAgentConfig {

    @Bean("recruitAgent")
    public AgentMarkAgent recruitAgent(ToolRegistry registry, ModelProvider provider,
                                       SystemPromptLoader promptLoader, AgentMarkProperties props) {
        String prompt = promptLoader.load("agentmark/recruit-prompt.md");
        return new AgentMarkAgent(registry, provider, null, prompt, props.getMaxToolRounds());
    }

    @Bean("customerAgent")
    public AgentMarkAgent customerAgent(ToolRegistry registry, ModelProvider provider,
                                        SystemPromptLoader promptLoader, AgentMarkProperties props) {
        String prompt = promptLoader.load("agentmark/customer-prompt.md");
        return new AgentMarkAgent(registry, provider, null, prompt, props.getMaxToolRounds());
    }
}
