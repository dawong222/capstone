package com.capstone.capstone.service;

import com.capstone.capstone.dto.AiRequestDto;
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

    public void requestSchedule(AiRequestDto requestDto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AiRequestDto> entity = new HttpEntity<>(requestDto, headers);

        log.info("[AI 서버 전송] url={}", AI_URL);
        ResponseEntity<Void> response = restTemplate.exchange(
                AI_URL,
                HttpMethod.POST,
                entity,
                Void.class
        );
        log.info("[AI 서버 전송 완료] status={}", response.getStatusCode());
    }

    /** Raw Map 그대로 AI 서버에 POST, AI 서버가 처리 완료 후 콜백으로 결과 전송 */
    public void sendRaw(Map<String, Object> payload) {
        String url = AI_URL + "/ai/control";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        log.info("[AI 서버 전송] url={}", url);
        ResponseEntity<Void> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Void.class
        );
        log.info("[AI 서버 전송 완료] status={}", response.getStatusCode());
    }

}