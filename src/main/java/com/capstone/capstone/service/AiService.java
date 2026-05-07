package com.capstone.capstone.service;

import com.capstone.capstone.dto.AiRequestDto;
import com.capstone.capstone.dto.AiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final RestTemplate restTemplate;

    @Value("${ai.server.url}")
    private String AI_URL;

    public AiResponseDto requestSchedule(AiRequestDto requestDto) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AiRequestDto> entity = new HttpEntity<>(requestDto, headers);

        ResponseEntity<AiResponseDto> response = restTemplate.exchange(
                AI_URL,
                HttpMethod.POST,
                entity,
                AiResponseDto.class
        );

        return response.getBody();
    }

    /** Raw Map 그대로 AI 서버에 POST, 응답을 AiResponseDto로 파싱해 반환 */
    public AiResponseDto sendRaw(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        log.info("[AI 서버 전송] url={}", AI_URL);
        ResponseEntity<AiResponseDto> response = restTemplate.exchange(
                AI_URL,
                HttpMethod.POST,
                entity,
                AiResponseDto.class
        );
        log.info("[AI 서버 응답] status={}", response.getStatusCode());
        return response.getBody();
    }
}