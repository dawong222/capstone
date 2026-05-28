package com.capstone.capstone.service;

import com.capstone.capstone.dto.ChatRequestDto;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.llm.chat-url}")
    private String llmChatUrl;

    public ChatResponseDto chat(ChatRequestDto request) {
        String message = request.getMessage();

        if (message == null || message.isBlank()) {
            return ChatResponseDto.builder()
                    .answer("질문을 입력해 주세요.")
                    .intent("EMPTY")
                    .context(Map.of("status", "no_data", "message", "질문이 비어 있습니다."))
                    .build();
        }

        Map<String, Object> llmRequest = new LinkedHashMap<>();
        llmRequest.put("sessionId", request.getSessionId());
        llmRequest.put("question", message);
        llmRequest.put("intent", "");
        llmRequest.put("context", Map.of());
        llmRequest.put("history", request.getHistory() == null ? List.of() : request.getHistory());
        llmRequest.put("max_new_tokens", 420);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(llmRequest, headers);

        log.info("[LLM 챗봇 요청] url={}, message={}", llmChatUrl, message);

        JsonNode llmResponse;
        try {
            llmResponse = restTemplate.postForObject(llmChatUrl, httpRequest, JsonNode.class);
        } catch (Exception e) {
            log.error("[LLM 챗봇 요청 실패] {}", e.getMessage());
            return ChatResponseDto.builder()
                    .answer("LLM 서버에 연결할 수 없습니다.")
                    .intent("ERROR")
                    .context(noData("LLM 서버 연결 실패: " + e.getMessage()))
                    .build();
        }

        log.info("[LLM 챗봇 응답] {}", llmResponse);

        if (llmResponse == null) {
            return ChatResponseDto.builder()
                    .answer("응답을 받지 못했습니다.")
                    .intent("NO_RESPONSE")
                    .context(noData("LLM 응답이 없습니다."))
                    .build();
        }

        String intent = llmResponse.path("intent").asText("").trim();
        if (intent.isBlank()) {
            intent = "UNKNOWN";
        }

        Map<String, Object> context;
        JsonNode contextNode = llmResponse.path("context");
        if (!contextNode.isMissingNode() && !contextNode.isNull()) {
            context = objectMapper.convertValue(contextNode, new TypeReference<>() {});
        } else {
            context = noData("DB 조회 결과가 없습니다.");
        }

        if (llmResponse.hasNonNull("answer")) {
            return ChatResponseDto.builder()
                    .answer(llmResponse.get("answer").asText())
                    .intent(intent)
                    .context(context)
                    .build();
        }

        return ChatResponseDto.builder()
                .answer("답변을 생성하지 못했습니다.")
                .intent(intent)
                .context(context)
                .build();
    }

    private Map<String, Object> noData(String message) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("status", "no_data");
        context.put("message", message);
        return context;
    }
}
