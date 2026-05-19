package io.github.daixueyun3377.agentmark.example;

import io.github.daixueyun3377.agentmark.core.agent.AgentMarkAgent;
import io.github.daixueyun3377.agentmark.core.model.ChatResult;
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

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reply", result.getText());
        response.put("traceId", result.getTraceId());
        return response;
    }
}
