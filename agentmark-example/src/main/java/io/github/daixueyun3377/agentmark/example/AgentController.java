package io.github.daixueyun3377.agentmark.example;

import io.github.daixueyun3377.agentmark.core.agent.AgentMarkAgent;
import io.github.daixueyun3377.agentmark.core.model.ChatResult;
import io.github.daixueyun3377.agentmark.core.model.ChatStatistics;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 示例 REST 接口 —— 通过 HTTP 与 Agent 对话。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentMarkAgent agent;

    public AgentController(AgentMarkAgent agent) {
        this.agent = agent;
    }

    /**
     * 单轮对话
     * POST /api/agent/chat
     * {"message": "北京今天天气怎么样？"}
     */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        ChatResult result = agent.chat(message);
        ChatStatistics stats = result.getStatistics();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reply", result.getText());

        Map<String, Object> statistics = new LinkedHashMap<>();
        statistics.put("llmCallCount", stats.getLlmCallCount());
        statistics.put("toolCallCount", stats.getToolCallCount());
        statistics.put("totalDurationMs", stats.getTotalDurationMs());
        statistics.put("llmDurationMs", stats.getLlmDurationMs());
        statistics.put("toolDurationMs", stats.getToolDurationMs());
        statistics.put("callChain", stats.getCallChain());
        response.put("statistics", statistics);

        return response;
    }
}
