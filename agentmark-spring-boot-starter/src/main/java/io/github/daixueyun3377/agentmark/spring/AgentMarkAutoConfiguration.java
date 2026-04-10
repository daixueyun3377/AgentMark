package io.github.daixueyun3377.agentmark.spring;

import io.github.daixueyun3377.agentmark.core.agent.AgentMarkAgent;
import io.github.daixueyun3377.agentmark.core.annotation.AgentMark;
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider;
import io.github.daixueyun3377.agentmark.core.provider.claude.ClaudeProvider;
import io.github.daixueyun3377.agentmark.core.provider.openai.OpenAiProvider;
import io.github.daixueyun3377.agentmark.core.registry.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean = context.getBean(beanName);
            boolean hasTool = false;
            for (Method method : bean.getClass().getMethods()) {
                if (method.isAnnotationPresent(AgentMark.class)) {
                    hasTool = true;
                    break;
                }
            }
            if (hasTool) {
                registry.register(bean);
            }
        }

        log.info("AgentMark: registered {} tools", registry.getAllTools().size());
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelProvider modelProvider(AgentMarkProperties props) {
        String p = props.getProvider();
        if ("claude".equals(p)) {
            return new ClaudeProvider(props.getApiKey(), props.getModel(), props.getBaseUrl());
        } else {
            return new OpenAiProvider(props.getApiKey(), props.getModel(), props.getBaseUrl());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentMarkAgent agentMarkAgent(ToolRegistry registry, ModelProvider provider) {
        return new AgentMarkAgent(registry, provider);
    }
}
