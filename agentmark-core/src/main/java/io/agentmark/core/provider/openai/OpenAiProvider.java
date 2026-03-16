package io.agentmark.core.provider.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
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
 * OpenAI 兼容的模型提供者（支持 OpenAI、通义千问等兼容接口）。
 */
public class OpenAiProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiProvider(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    public OpenAiProvider(String apiKey, String model) {
        this(apiKey, model, "https://api.openai.com/v1/");
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

            // messages
            ArrayNode msgArray = body.putArray("messages");
            for (ChatMessage msg : messages) {
                ObjectNode msgNode = msgArray.addObject();
                msgNode.put("role", msg.role());
                if (msg.content() != null) {
                    msgNode.put("content", msg.content());
                }
                if (msg.toolCallId() != null) {
                    msgNode.put("tool_call_id", msg.toolCallId());
                }
                if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                    ArrayNode tcArray = msgNode.putArray("tool_calls");
                    for (ToolCall tc : msg.toolCalls()) {
                        ObjectNode tcNode = tcArray.addObject();
                        tcNode.put("id", tc.id());
                        tcNode.put("type", "function");
                        ObjectNode fn = tcNode.putObject("function");
                        fn.put("name", tc.name());
                        fn.put("arguments", mapper.writeValueAsString(tc.arguments()));
                    }
                }
            }

            // tools
            if (tools != null && !tools.isEmpty()) {
                ArrayNode toolsArray = body.putArray("tools");
                for (ToolDefinition tool : tools) {
                    ObjectNode toolNode = toolsArray.addObject();
                    toolNode.put("type", "function");
                    ObjectNode fn = toolNode.putObject("function");
                    fn.put("name", tool.getName());
                    fn.put("description", tool.getDescription());

                    ObjectNode params = fn.putObject("parameters");
                    params.put("type", "object");
                    ObjectNode props = params.putObject("properties");
                    ArrayNode required = params.putArray("required");

                    for (ToolParameter p : tool.getParameters()) {
                        ObjectNode prop = props.putObject(p.getName());
                        prop.put("type", p.getType());
                        prop.put("description", p.getDescription());
                        if (p.isRequired()) {
                            required.add(p.getName());
                        }
                    }
                }
            }

            Request request = new Request.Builder()
                    .url(baseUrl + "chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "unknown";
                    throw new RuntimeException("OpenAI API error: " + response.code() + " " + errBody);
                }

                JsonNode root = mapper.readTree(response.body().string());
                JsonNode choice = root.get("choices").get(0).get("message");

                // check tool calls
                if (choice.has("tool_calls") && !choice.get("tool_calls").isEmpty()) {
                    List<ToolCall> toolCalls = new ArrayList<>();
                    for (JsonNode tc : choice.get("tool_calls")) {
                        String id = tc.get("id").asText();
                        String name = tc.get("function").get("name").asText();
                        Map<String, Object> args = mapper.readValue(
                                tc.get("function").get("arguments").asText(),
                                mapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
                        toolCalls.add(new ToolCall(id, name, args));
                    }
                    String text = choice.has("content") && !choice.get("content").isNull()
                            ? choice.get("content").asText() : null;
                    return new ChatResponse(text, toolCalls);
                }

                return new ChatResponse(choice.get("content").asText(), null);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to call OpenAI API", e);
        }
    }
}
