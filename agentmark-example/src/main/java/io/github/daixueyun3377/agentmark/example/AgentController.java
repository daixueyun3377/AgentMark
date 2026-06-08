package io.github.daixueyun3377.agentmark.example;

import io.github.daixueyun3377.agentmark.core.agent.AgentMarkSessionManager;
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

    private final AgentMarkSessionManager sessionManager;

    public AgentController(AgentMarkSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 多轮对话
     * POST /api/agent/chat
     * {"sessionId": "可选", "message": "北京今天天气怎么样？"}
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = sessionManager.createSession();
        }

        ChatResult result = sessionManager.chat(sessionId, request.getMessage());
        return new ChatResponse(sessionId, result.getText(), result.getTraceId());
    }

    /**
     * 清除指定会话
     * DELETE /api/agent/sessions/{sessionId}
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, Object> clearSession(@PathVariable String sessionId) {
        boolean removed = sessionManager.clear(sessionId);

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("sessionId", sessionId);
        response.put("removed", removed);
        return response;
    }

    public static class ChatRequest {
        private String sessionId;
        private String message;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class ChatResponse {
        private final String sessionId;
        private final String reply;
        private final String traceId;

        public ChatResponse(String sessionId, String reply, String traceId) {
            this.sessionId = sessionId;
            this.reply = reply;
            this.traceId = traceId;
        }

        public String getSessionId() { return sessionId; }
        public String getReply() { return reply; }
        public String getTraceId() { return traceId; }
    }
}
