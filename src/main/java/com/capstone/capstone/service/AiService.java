package com.capstone.capstone.service;

import com.capstone.capstone.dto.AiRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

        ResponseEntity<String> response = restTemplate.exchange(
                AI_URL,
                HttpMethod.POST,
                entity,
                String.class
        );

        System.out.println("AI 응답: " + response.getBody());
    }
}