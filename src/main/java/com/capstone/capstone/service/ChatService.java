package com.capstone.capstone.service;

import com.capstone.capstone.dto.ChatResponseDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.llm.chat-url}")
    private String llmChatUrl;

    public ChatResponseDto chat(String sessionId, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", sessionId);
        body.put("question", message);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        log.info("[LLM 챗봇 요청] url={}, message={}", llmChatUrl, message);
        String raw = restTemplate.postForObject(llmChatUrl, request, String.class);
        log.info("[LLM 챗봇 응답] {}", raw);

        if (raw == null) {
            return ChatResponseDto.builder().answer("응답을 받지 못했습니다.").build();
        }

        JsonNode llmResponse;
        try {
            llmResponse = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new RuntimeException("LLM 응답 파싱 실패: " + e.getMessage(), e);
        }

        Map<String, Object> context = null;
        JsonNode contextNode = llmResponse.path("context");
        if (!contextNode.isMissingNode() && !contextNode.isNull()) {
            context = objectMapper.convertValue(contextNode, new TypeReference<>() {});
        }

        return ChatResponseDto.builder()
                .answer(llmResponse.path("answer").asText(""))
                .intent(llmResponse.path("intent").asText(""))
                .context(context)
                .build();
    }
}
