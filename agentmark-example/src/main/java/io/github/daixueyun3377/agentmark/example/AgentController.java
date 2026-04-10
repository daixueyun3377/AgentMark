package io.github.daixueyun3377.agentmark.example;

import io.github.daixueyun3377.agentmark.core.agent.AgentMarkAgent;
import io.github.daixueyun3377.agentmark.core.agent.AgentMarkSession;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
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
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String reply = agent.chat(message);
        return Collections.singletonMap("reply", reply);
    }
}
