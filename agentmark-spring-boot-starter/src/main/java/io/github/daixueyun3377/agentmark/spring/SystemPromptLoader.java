package io.github.daixueyun3377.agentmark.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 从应用工程 classpath 加载 system prompt Markdown 文件。
 *
 * <p>默认路径为 {@value #DEFAULT_SYSTEM_PROMPT_PATH}，多 Agent 场景可使用
 * {@code agentmark/xxx-prompt.md} 等自定义路径。</p>
 */
public class SystemPromptLoader {

    public static final String DEFAULT_SYSTEM_PROMPT_PATH = "agentmark/system-prompt.md";

    private static final Logger log = LoggerFactory.getLogger(SystemPromptLoader.class);

    private final ResourceLoader resourceLoader;

    public SystemPromptLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 加载默认 system prompt 文件。
     */
    public String loadDefault() {
        return load(DEFAULT_SYSTEM_PROMPT_PATH);
    }

    /**
     * 从 classpath 加载指定路径的 prompt 文件。
     *
     * @param path classpath 相对路径，如 {@code agentmark/recruit-prompt.md}
     * @return 文件内容；文件不存在或为空时返回 {@code null}
     */
    public String load(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        String location = path.startsWith("classpath:") ? path : "classpath:" + path;
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.debug("System prompt file not found: {}", path);
            return null;
        }
        try {
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                log.warn("System prompt file is empty: {}", path);
                return null;
            }
            log.info("Loaded system prompt from: {}", path);
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load system prompt from: " + path, e);
        }
    }
}
