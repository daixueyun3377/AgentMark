package io.agentmark.core.provider.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentmark.core.model.ToolDefinition;
import io.agentmark.core.model.ToolParameter;
import io.agentmark.core.provider.ModelProvider;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Claude API 模型提供者，支持 Claude tool use 协议。
 * P0 优先对接。
 */
public class ClaudeProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeProvider.class);
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/";
    private static final String API_VERSION = "2023-06-01";

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public ClaudeProvider(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = (baseUrl != null && !baseUrl.isEmpty())
                ? (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/")
                : DEFAULT_BASE_URL;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    public ClaudeProvider(String apiKey, String model) {
        this(apiKey, model, DEFAULT_BASE_URL);
    }

    @Override
    public ChatResponse chat(String userMessage, Collection<ToolDefinition> tools, List<ChatMessage> history) {
        List<ChatMessage> messages = new ArrayList<>(history);
        messages.add(ChatMessage.user(userMessage));
        return doRequest(messages, tools);
    }

    @Override
    public ChatResponse submitToolResults(List<ChatMessage> history, Collection<ToolDefinition> tools) {
        return doRequest(history, tools);
    }

    private ChatResponse doRequest(List<ChatMessage> messages, Collection<ToolDefinition> tools) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", 4096);

            // messages — Claude 格式
            ArrayNode msgArray = body.putArray("messages");
            for (ChatMessage msg : messages) {
                if ("user".equals(msg.role())) {
                    ObjectNode msgNode = msgArray.addObject();
                    msgNode.put("role", "user");
                    // 普通用户消息
                    if (msg.content() != null) {
                        msgNode.put("content", msg.content());
                    }
                } else if ("assistant".equals(msg.role())) {
                    ObjectNode msgNode = msgArray.addObject();
                    msgNode.put("role", "assistant");
                    if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                        // assistant 消息带 tool_use content blocks
                        ArrayNode contentArr = msgNode.putArray("content");
                        if (msg.content() != null && !msg.content().isEmpty()) {
                            ObjectNode textBlock = contentArr.addObject();
                            textBlock.put("type", "text");
                            textBlock.put("text", msg.content());
                        }
                        for (ToolCall tc : msg.toolCalls()) {
                            ObjectNode toolUseBlock = contentArr.addObject();
                            toolUseBlock.put("type", "tool_use");
                            toolUseBlock.put("id", tc.id());
                            toolUseBlock.put("name", tc.name());
                            toolUseBlock.set("input", mapper.valueToTree(tc.arguments()));
                        }
                    } else if (msg.content() != null) {
                        msgNode.put("content", msg.content());
                    }
                } else if ("tool".equals(msg.role())) {
                    // Claude: tool results 作为 user 消息中的 tool_result content block
                    // 需要合并连续的 tool results 到同一个 user 消息
                    ObjectNode lastMsg = getOrCreateToolResultMessage(msgArray);
                    ArrayNode contentArr = (ArrayNode) lastMsg.get("content");
                    ObjectNode resultBlock = contentArr.addObject();
                    resultBlock.put("type", "tool_result");
                    resultBlock.put("tool_use_id", msg.toolCallId());
                    resultBlock.put("content", msg.content());
                }
            }

            // tools — Claude tool 定义格式
            if (tools != null && !tools.isEmpty()) {
                ArrayNode toolsArray = body.putArray("tools");
                for (ToolDefinition tool : tools) {
                    ObjectNode toolNode = toolsArray.addObject();
                    toolNode.put("name", tool.getName());
                    toolNode.put("description", tool.getDescription());

                    ObjectNode inputSchema = toolNode.putObject("input_schema");
                    inputSchema.put("type", "object");
                    ObjectNode props = inputSchema.putObject("properties");
                    ArrayNode required = inputSchema.putArray("required");

                    for (ToolParameter p : tool.getParameters()) {
                        if (p.isComplex() && p.getSchema() != null) {
                            // 复杂类型：直接使用预生成的 schema
                            props.set(p.getName(), p.getSchema().deepCopy());
                        } else {
                            ObjectNode prop = props.putObject(p.getName());
                            prop.put("type", p.getType());
                            prop.put("description", p.getDescription());
                        }
                        if (p.isRequired()) {
                            required.add(p.getName());
                        }
                    }
                }
            }

            log.debug("Claude request: {}", body.toString());

            Request request = new Request.Builder()
                    .url(baseUrl + "v1/messages")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", API_VERSION)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "unknown";
                    throw new RuntimeException("Claude API error: " + response.code() + " " + errBody);
                }

                JsonNode root = mapper.readTree(response.body().string());
                return parseClaudeResponse(root);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to call Claude API", e);
        }
    }

    /**
     * 解析 Claude 响应，提取文本和 tool_use 调用。
     */
    private ChatResponse parseClaudeResponse(JsonNode root) {
        JsonNode contentArray = root.get("content");
        if (contentArray == null || !contentArray.isArray()) {
            return new ChatResponse("", null);
        }

        StringBuilder textBuilder = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();

        for (JsonNode block : contentArray) {
            String type = block.get("type").asText();
            if ("thinking".equals(type)) {
                // 跳过 extended thinking 块
                continue;
            } else if ("text".equals(type)) {
                textBuilder.append(block.get("text").asText());
            } else if ("tool_use".equals(type)) {
                String id = block.get("id").asText();
                String name = block.get("name").asText();
                Map<String, Object> args = mapper.convertValue(
                        block.get("input"),
                        mapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
                toolCalls.add(new ToolCall(id, name, args));
            }
        }

        String text = textBuilder.length() > 0 ? textBuilder.toString() : null;
        return new ChatResponse(text, toolCalls.isEmpty() ? null : toolCalls);
    }

    /**
     * 获取或创建用于存放 tool_result 的 user 消息。
     * Claude 要求 tool_result 放在 user 角色的消息中。
     */
    private ObjectNode getOrCreateToolResultMessage(ArrayNode msgArray) {
        if (msgArray.size() > 0) {
            JsonNode last = msgArray.get(msgArray.size() - 1);
            if (last.isObject() && "user".equals(last.get("role").asText())) {
                JsonNode content = last.get("content");
                if (content != null && content.isArray() && content.size() > 0) {
                    JsonNode firstBlock = content.get(0);
                    if (firstBlock.has("type") && "tool_result".equals(firstBlock.get("type").asText())) {
                        return (ObjectNode) last;
                    }
                }
            }
        }
        ObjectNode userMsg = msgArray.addObject();
        userMsg.put("role", "user");
        userMsg.putArray("content");
        return userMsg;
    }
}
