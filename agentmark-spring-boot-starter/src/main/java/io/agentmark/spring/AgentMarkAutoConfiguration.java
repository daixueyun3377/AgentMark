package io.agentmark.spring;

import io.agentmark.core.agent.AgentMarkAgent;
import io.agentmark.core.annotation.AgentMark;
import io.agentmark.core.provider.ModelProvider;
import io.agentmark.core.provider.claude.ClaudeProvider;
import io.agentmark.core.provider.openai.OpenAiProvider;
import io.agentmark.core.registry.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;

/**
 * AgentMark Spring Boot 自动配置。
 */
@Configuration
@EnableConfigurationProperties(AgentMarkProperties.class)
public class AgentMarkAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentMarkAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(ApplicationContext context) {
        ToolRegistry registry = new ToolRegistry();

        // 自动扫描所有 Spring Bean 中的 @AgentMark 方法
        // 先用 getType() 检查类定义，避免提前实例化不相关的 Bean 导致循环依赖
        for (String beanName : context.getBeanDefinitionNames()) {
            Class<?> beanType = context.getType(beanName);
            if (beanType == null) continue;

            boolean hasAgentMark = false;
            for (Method method : beanType.getMethods()) {
                if (method.isAnnotationPresent(AgentMark.class)) {
                    hasAgentMark = true;
                    break;
                }
            }
            if (hasAgentMark) {
                registry.register(context.getBean(beanName));
            }
        }

        log.info("AgentMark: registered {} tools", registry.getAllTools().size());
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelProvider modelProvider(AgentMarkProperties props) {
        String provider = props.getProvider();
        if ("claude".equals(provider)) {
            log.info("AgentMark: using Claude provider, model={}", props.getModel());
            return new ClaudeProvider(props.getApiKey(), props.getModel(), props.getBaseUrl());
        }
        if ("openai".equals(provider) || "dashscope".equals(provider)) {
            String baseUrl = (props.getBaseUrl() != null && !props.getBaseUrl().isEmpty())
                    ? props.getBaseUrl() : "https://api.openai.com/v1/";
            log.info("AgentMark: using OpenAI provider, model={}", props.getModel());
            return new OpenAiProvider(props.getApiKey(), props.getModel(), baseUrl);
        }
        throw new IllegalArgumentException("Unsupported provider: " + provider
                + ". Supported: claude, openai, dashscope");
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentMarkAgent agentMarkAgent(ToolRegistry registry, ModelProvider provider) {
        return new AgentMarkAgent(registry, provider);
    }
}
