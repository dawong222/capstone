package com.capstone.capstone.service;

import com.capstone.capstone.dto.AiRequestDto;
import com.capstone.capstone.dto.AiResponseDto;
import com.capstone.capstone.dto.ai.ScheduleForecastRequestDto;
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

    public AiResponseDto requestScheduleForecast(ScheduleForecastRequestDto requestDto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ScheduleForecastRequestDto> entity = new HttpEntity<>(requestDto, headers);

        ResponseEntity<AiResponseDto> response = restTemplate.exchange(
                AI_URL,
                HttpMethod.POST,
                entity,
                AiResponseDto.class
        );

        return response.getBody();
    }
}