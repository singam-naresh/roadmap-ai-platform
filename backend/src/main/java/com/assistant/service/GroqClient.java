package com.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * HTTP client for the Groq Chat Completions API (OpenAI-compatible).
 */
@Component
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Sends a list of messages to Groq and returns the assistant reply text.
     *
     * @param messages list of ChatMessage records
     * @return assistant message content string
     * @throws RuntimeException with a readable message on any API failure
     */
    public String chat(List<ChatMessage> messages) {
        log.info("[groq] Sending request — model: {}, messages: {}", model, messages.size());

        try {
            String requestBody = buildRequestBody(messages, false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, request, String.class);

            log.info("[groq] Response status: {}", response.getStatusCode());

            return extractContent(response.getBody());

        } catch (HttpClientErrorException e) {
            // 4xx — Groq returned a structured error
            String groqError = extractGroqError(e.getResponseBodyAsString());
            log.error("[groq] Client error {}: {}", e.getStatusCode(), groqError);
            // Context length exceeded — give a clear message
            if (groqError.contains("context") || groqError.contains("token") || groqError.contains("length")) {
                throw new RuntimeException("Prompt too long. Please shorten your request.");
            }
            if (groqError.contains("api_key") || groqError.contains("invalid_api_key")) {
                throw new RuntimeException("AI service configuration error. Please contact support.");
            }
            throw new RuntimeException("AI service error: " + groqError);

        } catch (HttpServerErrorException e) {
            // 5xx — Groq server-side failure
            String groqError = extractGroqError(e.getResponseBodyAsString());
            log.error("[groq] Server error {}: {}", e.getStatusCode(), groqError);
            throw new RuntimeException("AI service temporarily unavailable. Please try again in a moment.");

        } catch (Exception e) {
            log.error("[groq] Unexpected failure: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to reach AI service. Check your connection and try again.");
        }
    }

    /**
     * Streams a response from Groq using Server-Sent Events.
     * Calls the consumer for each text chunk as it arrives.
     */
    public void chatStream(List<ChatMessage> messages, java.util.function.Consumer<String> chunkConsumer) {
        log.info("[groq] Streaming request — model: {}, messages: {}", model, messages.size());

        try {
            String requestBody = buildRequestBody(messages, true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));

            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

            // Use streaming RestTemplate call
            restTemplate.execute(apiUrl, HttpMethod.POST,
                    req -> {
                        req.getHeaders().addAll(headers);
                        req.getBody().write(requestBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    },
                    response -> {
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(response.getBody()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6).trim();
                                    if ("[DONE]".equals(data)) break;
                                    try {
                                        JsonNode chunk = objectMapper.readTree(data);
                                        JsonNode delta = chunk.path("choices").path(0).path("delta").path("content");
                                        if (!delta.isMissingNode() && !delta.isNull()) {
                                            chunkConsumer.accept(delta.asText());
                                        }
                                    } catch (Exception ignored) {
                                        // Skip malformed chunks
                                    }
                                }
                            }
                        }
                        return null;
                    });

        } catch (Exception e) {
            log.error("[groq] Stream error: {}", e.getMessage());
            throw new RuntimeException("Groq streaming failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String buildRequestBody(List<ChatMessage> messages, boolean stream) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.4);
        body.put("max_tokens", 4096);
        if (stream) body.put("stream", true);

        ArrayNode messagesNode = body.putArray("messages");
        for (ChatMessage msg : messages) {
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", msg.role());
            // Truncate individual messages to avoid context limit — 6000 chars max per message
            String content = msg.content();
            if (content != null && content.length() > 6000) {
                content = content.substring(0, 6000) + "\n[truncated]";
                log.warn("[groq] Message truncated to 6000 chars (role={})", msg.role());
            }
            msgNode.put("content", content);
            messagesNode.add(msgNode);
        }

        return objectMapper.writeValueAsString(body);
    }

    /**
     * Extracts the assistant message content from a successful Groq response body.
     */
    private String extractContent(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("choices").get(0).path("message").path("content");
        if (content == null || content.isMissingNode()) {
            log.error("[groq] Unexpected response shape: {}", responseBody);
            throw new RuntimeException("Groq response missing expected 'choices[0].message.content' field");
        }
        return content.asText();
    }

    /**
     * Attempts to extract a human-readable error message from a Groq error response body.
     * Falls back to the raw body if parsing fails.
     *
     * Groq error shape: { "error": { "message": "...", "type": "...", "code": "..." } }
     */
    private String extractGroqError(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("error").path("message");
            if (!message.isMissingNode()) {
                return message.asText();
            }
        } catch (Exception ignored) {
            // fall through to raw body
        }
        return responseBody;
    }

    // -------------------------------------------------------------------------

    public record ChatMessage(String role, String content) {}
}
