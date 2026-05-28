package com.capstone.capstone.service;

import com.capstone.capstone.dto.ChatResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
        Map<String, Object> llmRequest = Map.of("sessionId", sessionId, "message", message);

        log.info("[LLM 챗봇 요청] url={}, message={}", llmChatUrl, message);
        JsonNode llmResponse = restTemplate.postForObject(
                llmChatUrl,
                llmRequest,
                JsonNode.class
        );
        log.info("[LLM 챗봇 응답] {}", llmResponse);

        if (llmResponse == null) {
            return ChatResponseDto.builder().answer("응답을 받지 못했습니다.").build();
        }
        return objectMapper.convertValue(llmResponse, ChatResponseDto.class);
    }
}
