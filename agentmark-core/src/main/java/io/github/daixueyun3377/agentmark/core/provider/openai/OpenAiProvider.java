package io.github.daixueyun3377.agentmark.core.provider.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.daixueyun3377.agentmark.core.model.ToolDefinition;
import io.github.daixueyun3377.agentmark.core.model.ToolParameter;
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider;
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
                msgNode.put("role", msg.getRole());
                if (msg.getContent() != null) {
                    msgNode.put("content", msg.getContent());
                }
                if (msg.getToolCallId() != null) {
                    msgNode.put("tool_call_id", msg.getToolCallId());
                }
                if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    ArrayNode tcArray = msgNode.putArray("tool_calls");
                    for (ToolCall tc : msg.getToolCalls()) {
                        ObjectNode tcNode = tcArray.addObject();
                        tcNode.put("id", tc.getId());
                        tcNode.put("type", "function");
                        ObjectNode fn = tcNode.putObject("function");
                        fn.put("name", tc.getName());
                        fn.put("arguments", mapper.writeValueAsString(tc.getArguments()));
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
                    buildSchemaNode(params, tool.getParameters());
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

    /**
     * 递归构建 JSON Schema 节点，支持嵌套对象、数组、枚举。
     */
    private void buildSchemaNode(ObjectNode node, java.util.List<ToolParameter> params) {
        node.put("type", "object");
        ObjectNode props = node.putObject("properties");
        ArrayNode required = node.putArray("required");

        for (ToolParameter p : params) {
            ObjectNode prop = props.putObject(p.getName());
            writeParameterSchema(prop, p);
            if (p.isRequired()) {
                required.add(p.getName());
            }
        }
    }

    private void writeParameterSchema(ObjectNode prop, ToolParameter p) {
        prop.put("type", p.getType());
        if (p.getDescription() != null && !p.getDescription().isEmpty()) {
            prop.put("description", p.getDescription());
        }
        if (p.getEnumValues() != null && !p.getEnumValues().isEmpty()) {
            ArrayNode enumArr = prop.putArray("enum");
            for (String v : p.getEnumValues()) {
                enumArr.add(v);
            }
        }
        if ("object".equals(p.getType()) && p.getProperties() != null && !p.getProperties().isEmpty()) {
            buildSchemaNode(prop, p.getProperties());
        }
        if ("array".equals(p.getType()) && p.getItems() != null) {
            ObjectNode itemsNode = prop.putObject("items");
            writeParameterSchema(itemsNode, p.getItems());
        }
    }
}
