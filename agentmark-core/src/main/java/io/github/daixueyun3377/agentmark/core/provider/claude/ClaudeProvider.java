package io.github.daixueyun3377.agentmark.core.provider.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * Claude (Anthropic) 模型提供者，对接 Anthropic Messages API。
 */
public class ClaudeProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeProvider.class);
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/";
    private static final String API_VERSION = "2023-06-01";

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

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
        List<ChatMessage> messages = new ArrayList<ChatMessage>(history);
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

            ArrayNode msgArray = body.putArray("messages");
            for (ChatMessage msg : messages) {
                ObjectNode msgNode = msgArray.addObject();
                msgNode.put("role", "tool".equals(msg.getRole()) ? "user" : msg.getRole());

                if ("tool".equals(msg.getRole())) {
                    ArrayNode contentArr = msgNode.putArray("content");
                    ObjectNode block = contentArr.addObject();
                    block.put("type", "tool_result");
                    block.put("tool_use_id", msg.getToolCallId());
                    block.put("content", msg.getContent() != null ? msg.getContent() : "");
                } else if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    ArrayNode contentArr = msgNode.putArray("content");
                    if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                        ObjectNode textBlock = contentArr.addObject();
                        textBlock.put("type", "text");
                        textBlock.put("text", msg.getContent());
                    }
                    for (ToolCall tc : msg.getToolCalls()) {
                        ObjectNode toolUse = contentArr.addObject();
                        toolUse.put("type", "tool_use");
                        toolUse.put("id", tc.getId());
                        toolUse.put("name", tc.getName());
                        toolUse.set("input", mapper.valueToTree(tc.getArguments()));
                    }
                } else {
                    msgNode.put("content", msg.getContent() != null ? msg.getContent() : "");
                }
            }

            if (tools != null && !tools.isEmpty()) {
                ArrayNode toolsArray = body.putArray("tools");
                for (ToolDefinition tool : tools) {
                    ObjectNode toolNode = toolsArray.addObject();
                    toolNode.put("name", tool.getName());
                    toolNode.put("description", tool.getDescription());
                    ObjectNode inputSchema = toolNode.putObject("input_schema");
                    buildSchemaNode(inputSchema, tool.getParameters());
                }
            }

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
                return parseResponse(response.body().string());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to call Claude API", e);
        }
    }

    private void buildSchemaNode(ObjectNode node, List<ToolParameter> params) {
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

    private ChatResponse parseResponse(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode contentArray = root.get("content");

            String text = null;
            List<ToolCall> toolCalls = new ArrayList<ToolCall>();

            for (JsonNode block : contentArray) {
                String type = block.get("type").asText();
                if ("text".equals(type)) {
                    text = block.get("text").asText();
                } else if ("tool_use".equals(type)) {
                    String id = block.get("id").asText();
                    String name = block.get("name").asText();
                    Map<String, Object> args = mapper.convertValue(
                            block.get("input"),
                            mapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
                    toolCalls.add(new ToolCall(id, name, args));
                }
            }

            return new ChatResponse(text, toolCalls.isEmpty() ? null : toolCalls);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Claude response: " + responseBody, e);
        }
    }
}
